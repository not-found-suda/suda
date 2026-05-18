import os
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed

import boto3
import numpy as np


AWS_ACCESS_KEY = os.environ.get("AWS_ACCESS_KEY")
AWS_SECRET_KEY = os.environ.get("AWS_SECRET_KEY")
BUCKET_NAME = os.environ.get("S3_BUCKET_NAME")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
S3_SOURCE_PREFIX = "processed_npy_v5/"
LOCAL_DOWNLOAD_DIR = os.path.join(SCRIPT_DIR, "processed_npy_v5")
EXPECTED_INPUT_DIM = 332
MAX_WORKERS = int(os.environ.get("V5_DOWNLOAD_WORKERS", "8"))


def validate_env():
    missing = [
        name
        for name, value in {
            "AWS_ACCESS_KEY": AWS_ACCESS_KEY,
            "AWS_SECRET_KEY": AWS_SECRET_KEY,
            "S3_BUCKET_NAME": BUCKET_NAME,
        }.items()
        if not value
    ]
    if missing:
        raise RuntimeError(f"Missing required environment variables: {', '.join(missing)}")


def make_s3_client():
    return boto3.client(
        "s3",
        aws_access_key_id=AWS_ACCESS_KEY,
        aws_secret_access_key=AWS_SECRET_KEY,
    )


def remove_prefix(value, prefix):
    if value.startswith(prefix):
        return value[len(prefix):]
    return value


def download_single_file(s3_client, s3_key, local_path):
    try:
        if os.path.exists(local_path):
            return f"SKIP already exists: {local_path}"

        os.makedirs(os.path.dirname(local_path), exist_ok=True)
        s3_client.download_file(BUCKET_NAME, s3_key, local_path)
        return f"DONE downloaded: {local_path}"
    except Exception as e:
        return f"FAIL download {s3_key}: {e}"


def generate_s3_keys(s3_client, prefix):
    paginator = s3_client.get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=BUCKET_NAME, Prefix=prefix):
        for obj in page.get("Contents", []):
            if obj["Key"].endswith(".npy"):
                yield obj["Key"]


def summarize_local_files():
    class_counts = Counter()
    variant_counts = Counter()
    shape_samples = defaultdict(list)
    bad_shapes = []

    for root, _, files in os.walk(LOCAL_DOWNLOAD_DIR):
        for filename in files:
            if not filename.endswith(".npy"):
                continue

            path = os.path.join(root, filename)
            rel_path = os.path.relpath(path, LOCAL_DOWNLOAD_DIR)
            path_parts = rel_path.split(os.sep)
            class_name = path_parts[0]
            variant_name = path_parts[1] if len(path_parts) > 1 else class_name

            class_counts[class_name] += 1
            variant_counts[f"{class_name}/{variant_name}"] += 1

            if len(shape_samples[class_name]) >= 3:
                continue

            try:
                data = np.load(path, mmap_mode="r")
                shape_samples[class_name].append((rel_path, tuple(data.shape)))
                if data.ndim != 2 or data.shape[1] != EXPECTED_INPUT_DIM:
                    bad_shapes.append((rel_path, tuple(data.shape)))
            except Exception as e:
                bad_shapes.append((rel_path, f"load error: {e}"))

    print("\nClass counts:")
    for class_name, count in sorted(class_counts.items()):
        print(f"- {class_name}: {count}")

    print("\nVariant counts:")
    for variant_name, count in sorted(variant_counts.items()):
        print(f"- {variant_name}: {count}")

    print("\nShape samples:")
    for class_name, samples in sorted(shape_samples.items()):
        for rel_path, shape in samples:
            print(f"- {class_name}: {rel_path} shape={shape}")

    if bad_shapes:
        print("\nBad shapes:")
        for rel_path, shape in bad_shapes[:20]:
            print(f"- {rel_path}: {shape}")
    else:
        print(f"\nAll sampled files match input_dim={EXPECTED_INPUT_DIM}.")


def main():
    validate_env()
    s3_client = make_s3_client()

    print(f"List V5 npy files from S3: {S3_SOURCE_PREFIX}")
    s3_keys = list(generate_s3_keys(s3_client, S3_SOURCE_PREFIX))

    if not s3_keys:
        print("No .npy files found to download.")
        return

    print(f"Start download: {len(s3_keys)} files -> {LOCAL_DOWNLOAD_DIR}")

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        future_to_key = {}
        for key in s3_keys:
            local_path = os.path.join(LOCAL_DOWNLOAD_DIR, remove_prefix(key, S3_SOURCE_PREFIX)).replace("\\", "/")
            future = executor.submit(download_single_file, s3_client, key, local_path)
            future_to_key[future] = key

        for future in as_completed(future_to_key):
            print(future.result())

    summarize_local_files()


if __name__ == "__main__":
    main()
