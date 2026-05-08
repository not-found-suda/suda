import gc
import os
import re
import tempfile
import time
from concurrent.futures import ProcessPoolExecutor, as_completed

import boto3
import cv2
import mediapipe as mp
import numpy as np


AWS_ACCESS_KEY = os.environ.get("AWS_ACCESS_KEY")
AWS_SECRET_KEY = os.environ.get("AWS_SECRET_KEY")
BUCKET_NAME = os.environ.get("S3_BUCKET_NAME")

S3_SOURCE_PREFIX = "raw_videos/"
S3_TARGET_PREFIX = "processed_npy_v5/"
DEFAULT_MAX_WORKERS = 2
DEFAULT_BATCH_SIZE = 8

# A source folder is included only when it is exactly the word or word + digits.
# Example: "엄마", "엄마1", "엄마2" are included for "엄마"; "작은엄마" is not.
TARGET_WORDS = [
    "기차",
    "장난감",
    "놀다",
    "가다",
    "오다",
    "자다",
    "일어나다",
    "아프다",
    "병원",
    "조심",
]

# Feature layout:
# left hand 63 + right hand 63 + expanded face 135 + pose 21 + hand-face distances 50 = 332
EXPECTED_INPUT_DIM = 332

BASE_FACE_IDXS = [
    70, 63, 105, 66,
    336, 296, 334, 293,
    33, 160, 158, 133, 153, 144,
    362, 385, 387, 263, 373, 380,
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
]
FACEPLUS_IDXS = [
    1, 4,          # nose
    152,           # chin
    234, 454,      # cheeks / face sides
    172, 397,      # jaw line
    148, 377,      # lower jaw line
    13, 14,        # upper/lower lip center
    78, 308,       # mouth corners
    82, 312,       # upper lip detail
]
FACE_IDXS = BASE_FACE_IDXS + FACEPLUS_IDXS
POSE_IDXS = [0, 7, 8, 11, 12, 13, 14]
HAND_TIP_IDXS = [4, 8, 12, 16, 20]
FACE_ANCHOR_IDXS = [1, 13, 152, 234, 454]  # nose, mouth, chin, left cheek, right cheek


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


def _flatten_landmarks(landmarks, idxs):
    return np.array([[landmarks[idx].x, landmarks[idx].y, landmarks[idx].z] for idx in idxs], dtype=np.float32).flatten()


def _hand_array(hand_landmarks):
    if not hand_landmarks:
        return np.zeros((21, 3), dtype=np.float32), False

    points = np.array([[lm.x, lm.y, lm.z] for lm in hand_landmarks.landmark], dtype=np.float32)
    return points, True


def _hand_face_distances(left_hand, left_present, right_hand, right_present, face_landmarks):
    if not face_landmarks:
        return np.zeros(len(HAND_TIP_IDXS) * 2 * len(FACE_ANCHOR_IDXS), dtype=np.float32)

    anchors = np.array(
        [
            [
                face_landmarks.landmark[idx].x,
                face_landmarks.landmark[idx].y,
                face_landmarks.landmark[idx].z,
            ]
            for idx in FACE_ANCHOR_IDXS
        ],
        dtype=np.float32,
    )

    features = []
    for hand_points, is_present in ((left_hand, left_present), (right_hand, right_present)):
        if not is_present:
            features.extend([0.0] * (len(HAND_TIP_IDXS) * len(FACE_ANCHOR_IDXS)))
            continue

        for tip_idx in HAND_TIP_IDXS:
            tip = hand_points[tip_idx]
            features.extend(np.linalg.norm(anchors - tip, axis=1).tolist())

    return np.array(features, dtype=np.float32)


def extract_keypoints(results, last_pose, last_face):
    left_hand, left_present = _hand_array(results.left_hand_landmarks)
    right_hand, right_present = _hand_array(results.right_hand_landmarks)
    lh = left_hand.flatten()
    rh = right_hand.flatten()

    if results.face_landmarks:
        face = _flatten_landmarks(results.face_landmarks.landmark, FACE_IDXS)
        last_face = face
    else:
        face = last_face if last_face is not None else np.zeros(len(FACE_IDXS) * 3, dtype=np.float32)

    if results.pose_landmarks:
        pose = _flatten_landmarks(results.pose_landmarks.landmark, POSE_IDXS)
        last_pose = pose
    else:
        pose = last_pose if last_pose is not None else np.zeros(len(POSE_IDXS) * 3, dtype=np.float32)

    distances = _hand_face_distances(
        left_hand,
        left_present,
        right_hand,
        right_present,
        results.face_landmarks,
    )

    feature = np.concatenate([lh, rh, face, pose, distances]).astype(np.float32)
    if feature.shape[0] != EXPECTED_INPUT_DIM:
        raise ValueError(f"Unexpected feature dim: {feature.shape[0]} != {EXPECTED_INPUT_DIM}")

    return feature, last_pose, last_face


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


def get_int_env(name, default, minimum=1):
    raw_value = os.environ.get(name)
    if raw_value is None:
        return default

    try:
        value = int(raw_value)
    except ValueError:
        print(f"Invalid {name}={raw_value!r}; use default={default}")
        return default

    return max(value, minimum)


def list_source_folders(s3_client):
    paginator = s3_client.get_paginator("list_objects_v2")
    folders = []

    for page in paginator.paginate(Bucket=BUCKET_NAME, Prefix=S3_SOURCE_PREFIX, Delimiter="/"):
        for prefix_info in page.get("CommonPrefixes", []):
            prefix = prefix_info["Prefix"]
            folder_name = remove_prefix(prefix, S3_SOURCE_PREFIX).strip("/")
            if folder_name:
                folders.append(folder_name)

    return sorted(set(folders))


def match_target_word(folder_name):
    for word in TARGET_WORDS:
        if re.fullmatch(rf"{re.escape(word)}\d*", folder_name):
            return word
    return None


def generate_video_jobs(s3_client):
    paginator = s3_client.get_paginator("list_objects_v2")
    jobs = []
    matched_folders = []

    for folder_name in list_source_folders(s3_client):
        target_word = match_target_word(folder_name)
        if target_word is None:
            continue

        matched_folders.append((folder_name, target_word))
        folder_prefix = f"{S3_SOURCE_PREFIX}{folder_name}/"
        for page in paginator.paginate(Bucket=BUCKET_NAME, Prefix=folder_prefix):
            for obj in page.get("Contents", []):
                key = obj["Key"]
                if key.lower().endswith(".mp4"):
                    jobs.append((key, target_word, folder_name))

    return jobs, matched_folders


def build_target_key(s3_key, target_word, source_folder):
    source_prefix = f"{S3_SOURCE_PREFIX}{source_folder}/"
    relative_path = remove_prefix(s3_key, source_prefix)
    stem_path = os.path.splitext(relative_path)[0]
    target_key = os.path.join(S3_TARGET_PREFIX, target_word, source_folder, stem_path + ".npy")
    return target_key.replace("\\", "/")


def s3_object_exists(s3_client, key):
    try:
        s3_client.head_object(Bucket=BUCKET_NAME, Key=key)
        return True
    except Exception:
        return False


def filter_pending_jobs(s3_client, video_jobs):
    pending_jobs = []
    skipped_count = 0

    for job in video_jobs:
        s3_key, target_word, source_folder = job
        target_key = build_target_key(s3_key, target_word, source_folder)
        if s3_object_exists(s3_client, target_key):
            skipped_count += 1
            continue
        pending_jobs.append(job)

    return pending_jobs, skipped_count


def chunked(items, chunk_size):
    for start in range(0, len(items), chunk_size):
        yield items[start:start + chunk_size]


def process_single_video(job):
    s3_key, target_word, source_folder = job
    tmp_path = None
    tmp_npy_path = None
    cap = None

    try:
        s3_client = make_s3_client()
        target_key = build_target_key(s3_key, target_word, source_folder)

        try:
            s3_client.head_object(Bucket=BUCKET_NAME, Key=target_key)
            return f"SKIP already exists: {target_key}"
        except Exception:
            pass

        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp_file:
            tmp_path = tmp_file.name
        s3_client.download_file(Bucket=BUCKET_NAME, Key=s3_key, Filename=tmp_path)

        video_data = []
        last_pose = None
        last_face = None
        cap = cv2.VideoCapture(tmp_path)

        mp_holistic = mp.solutions.holistic
        with mp_holistic.Holistic(static_image_mode=False, min_detection_confidence=0.5) as holistic:
            while cap.isOpened():
                success, frame = cap.read()
                if not success:
                    break

                image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                results = holistic.process(image_rgb)
                keypoints, last_pose, last_face = extract_keypoints(results, last_pose, last_face)
                video_data.append(keypoints)

        if len(video_data) > 0 and last_pose is not None:
            npy_data = np.array(video_data, dtype=np.float32)
            if npy_data.ndim != 2 or npy_data.shape[1] != EXPECTED_INPUT_DIM:
                return f"FAIL shape mismatch {npy_data.shape}: {s3_key}"

            with tempfile.NamedTemporaryFile(suffix=".npy", delete=False) as tmp_npy:
                tmp_npy_path = tmp_npy.name
                np.save(tmp_npy_path, npy_data)
            s3_client.upload_file(tmp_npy_path, BUCKET_NAME, target_key)
            return f"DONE input_dim={EXPECTED_INPUT_DIM}: {target_key}"

        return f"FAIL no usable pose data: {s3_key}"

    except Exception as e:
        return f"ERROR {s3_key}: {e}"

    finally:
        if cap is not None:
            cap.release()
        for path in (tmp_path, tmp_npy_path):
            if path and os.path.exists(path):
                os.remove(path)
        gc.collect()


def process_jobs_in_batches(video_jobs, max_workers, batch_size):
    completed = 0
    total = len(video_jobs)

    for batch_idx, batch in enumerate(chunked(video_jobs, batch_size), start=1):
        print(
            f"\nBatch {batch_idx}: {len(batch)} jobs "
            f"(progress {completed}/{total}, workers={max_workers})"
        )

        # Recreate the pool per batch so MediaPipe/OpenCV memory is returned to the OS regularly.
        with ProcessPoolExecutor(max_workers=max_workers) as executor:
            future_to_video = {executor.submit(process_single_video, job): job[0] for job in batch}

            for future in as_completed(future_to_video):
                completed += 1
                print(f"[{completed}/{total}] {future.result()}")
                time.sleep(0.05)


def main():
    validate_env()
    s3_client = make_s3_client()
    video_jobs, matched_folders = generate_video_jobs(s3_client)
    max_workers = get_int_env("V5_MAX_WORKERS", DEFAULT_MAX_WORKERS)
    batch_size = get_int_env("V5_BATCH_SIZE", DEFAULT_BATCH_SIZE)
    batch_size = max(batch_size, max_workers)

    print(f"Start V5 extraction: {len(video_jobs)} videos, input_dim={EXPECTED_INPUT_DIM}")
    print(f"S3 source prefix: {S3_SOURCE_PREFIX}")
    print(f"S3 target prefix: {S3_TARGET_PREFIX}")
    print(f"Parallel config: workers={max_workers}, batch_size={batch_size}")
    print("Matched folders:")
    for folder_name, target_word in matched_folders:
        print(f"- {folder_name} -> {target_word}")

    if not video_jobs:
        print("No matching .mp4 files found.")
        return

    pending_jobs, skipped_count = filter_pending_jobs(s3_client, video_jobs)
    print(f"Resume check: skip existing={skipped_count}, pending={len(pending_jobs)}")

    if not pending_jobs:
        print("All matching videos are already processed.")
        return

    process_jobs_in_batches(pending_jobs, max_workers=max_workers, batch_size=batch_size)


if __name__ == "__main__":
    main()
