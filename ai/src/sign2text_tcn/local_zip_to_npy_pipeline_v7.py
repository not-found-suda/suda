"""
local_zip_to_npy_pipeline_v7.py
────────────────────────────────────────────────────────────────────
기존 local_zip_to_npy_pipeline.py 와 완전히 동일한 로직.
변경점:
  - DEFAULT_MAPPING  → "AI_Hub_Video_Mapping_v7.json"
  - DEFAULT_OUTPUT_DIR → "processed_npy_v7"
  - TARGET_WORDS     → target_words_v7.TARGET_WORDS (99단어 + none)

사용법 (v100 서버):
  python local_zip_to_npy_pipeline_v7.py \\
      --zip-root /path/to/aihub_zips \\
      --mapping  AI_Hub_Video_Mapping_v7.json \\
      --output-dir processed_npy_v7
────────────────────────────────────────────────────────────────────
"""

import argparse
import json
import os
import shutil
import tempfile
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

from aihub_to_local_npy_pipeline import (
    DEFAULT_BATCH_SIZE,
    DEFAULT_MAX_WORKERS,
    DEFAULT_TASKS_PER_CHILD,
    get_int_env,
    process_jobs_in_batches,
)
from target_words_v7 import TARGET_WORDS


DEFAULT_ZIP_PATTERN = "*real_word_video.zip"
DEFAULT_MAPPING = "AI_Hub_Video_Mapping_v7.json"
DEFAULT_OUTPUT_DIR = "processed_npy_v7"


def load_filename_to_label(mapping_path):
    with Path(mapping_path).open("r", encoding="utf-8") as f:
        mapping = json.load(f)

    filename_to_label = {}
    label_counts = {}
    for label in TARGET_WORDS:
        if label == "none":
            continue
        videos = mapping.get(label, [])
        label_counts[label] = len(videos)
        for video in videos:
            filename_to_label[video["filename"]] = label

    return filename_to_label, label_counts


def find_zip_files(zip_roots, zip_pattern):
    zip_files = []
    zip_counts = {}

    for zip_root in zip_roots:
        matches = sorted(zip_root.rglob(zip_pattern))
        zip_counts[str(zip_root)] = len(matches)
        zip_files.extend(matches)

    return sorted(dict.fromkeys(zip_files)), zip_counts


def scan_zip_members(zip_files):
    filename_files = defaultdict(list)
    zip_member_count = Counter()

    for zip_path in zip_files:
        with zipfile.ZipFile(zip_path, "r") as archive:
            for member in archive.namelist():
                filename = os.path.basename(member)
                if not filename.lower().endswith(".mp4"):
                    continue

                filename_files[filename].append((zip_path, member, filename))
                zip_member_count[zip_path.name] += 1

    return filename_files, zip_member_count


def output_path_for(output_dir, base_label, filename):
    return output_dir / base_label / (Path(filename).stem + ".npy")


def extract_members_to_temp(filename_to_label, filename_files, output_dir, temp_dir, overwrite):
    jobs = []
    counters = Counter()

    for filename, base_label in filename_to_label.items():
        matches = filename_files.get(filename, [])
        if not matches:
            counters[f"missing_in_zip/{base_label}"] += 1
            continue

        for zip_path, member, matched_filename in matches[:1]:
            output_path = output_path_for(output_dir, base_label, matched_filename)
            if output_path.exists() and not overwrite:
                counters[f"skipped_existing/{base_label}"] += 1
                continue

            temp_label_dir = temp_dir / base_label
            temp_label_dir.mkdir(parents=True, exist_ok=True)
            temp_video_path = temp_label_dir / matched_filename

            with zipfile.ZipFile(zip_path, "r") as archive:
                with archive.open(member) as source, temp_video_path.open("wb") as target:
                    shutil.copyfileobj(source, target)

            jobs.append((temp_video_path, output_path))
            counters[f"pending/{base_label}"] += 1

    return jobs, counters


def parse_args():
    parser = argparse.ArgumentParser(
        description="Create local npy files from pre-downloaded AIHub word ZIP files (v7: 100 classes)."
    )
    parser.add_argument(
        "--zip-root",
        action="append",
        required=True,
        help=(
            "Root directory containing *_real_word_video.zip files. "
            "Pass this option multiple times to scan split AIHub folders."
        ),
    )
    parser.add_argument(
        "--mapping",
        default=DEFAULT_MAPPING,
        help=f"Mapping JSON path. Default: {DEFAULT_MAPPING}",
    )
    parser.add_argument("--zip-pattern", default=DEFAULT_ZIP_PATTERN)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--max-workers", type=int, default=get_int_env("ONDEVICE_MAX_WORKERS", DEFAULT_MAX_WORKERS))
    parser.add_argument("--batch-size", type=int, default=get_int_env("ONDEVICE_BATCH_SIZE", DEFAULT_BATCH_SIZE))
    parser.add_argument(
        "--tasks-per-child",
        type=int,
        default=get_int_env("ONDEVICE_TASKS_PER_CHILD", DEFAULT_TASKS_PER_CHILD),
    )
    return parser.parse_args()


def main():
    args = parse_args()
    zip_roots = [Path(root).expanduser().resolve() for root in args.zip_root]
    mapping_path = Path(args.mapping).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    missing_roots = [zip_root for zip_root in zip_roots if not zip_root.exists()]
    if missing_roots:
        missing = "\n".join(str(zip_root) for zip_root in missing_roots)
        raise FileNotFoundError(f"zip root not found:\n{missing}")
    if not mapping_path.exists():
        raise FileNotFoundError(f"mapping not found: {mapping_path}")

    zip_files, zip_counts = find_zip_files(zip_roots, args.zip_pattern)
    if not zip_files:
        roots = "\n".join(str(zip_root) for zip_root in zip_roots)
        raise FileNotFoundError(f"No ZIP files found: roots=\n{roots}\npattern={args.zip_pattern}")

    print("ZIP roots:")
    for zip_root in zip_roots:
        print(f"- {zip_root}: {zip_counts.get(str(zip_root), 0)}")
    print(f"ZIP files: {len(zip_files)}")
    print(f"Mapping: {mapping_path}")
    print(f"Output dir: {output_dir}")
    print(f"Target words: {len([w for w in TARGET_WORDS if w != 'none'])} (+ none)")

    filename_to_label, label_counts = load_filename_to_label(mapping_path)
    filename_files, _ = scan_zip_members(zip_files)

    print("Mapping target counts:")
    for label in TARGET_WORDS:
        if label == "none":
            continue
        print(f"- {label}: {label_counts.get(label, 0)}")

    with tempfile.TemporaryDirectory(prefix="local_zip_mp4_v7_") as temp_root:
        jobs, counters = extract_members_to_temp(
            filename_to_label=filename_to_label,
            filename_files=filename_files,
            output_dir=output_dir,
            temp_dir=Path(temp_root),
            overwrite=args.overwrite,
        )

        print(f"Pending local ZIP mp4 files: {len(jobs)}")
        print("Counters:")
        for key, value in sorted(counters.items()):
            print(f"- {key}: {value}")

        if jobs:
            process_jobs_in_batches(
                jobs,
                max_workers=max(args.max_workers, 1),
                batch_size=max(args.batch_size, args.max_workers, 1),
                tasks_per_child=max(args.tasks_per_child, 1),
            )

    print("Local ZIP to npy generation complete (v7).")


if __name__ == "__main__":
    main()
