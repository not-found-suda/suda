import argparse
import gc
import json
import multiprocessing as mp_context
import os
import shutil
import subprocess
import tempfile
import time
import zipfile
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np


DEFAULT_MAPPING = "AI_Hub_Video_Mapping_v7.json"
DEFAULT_OUTPUT_DIR = "processed_npy_v7"
DEFAULT_MAX_WORKERS = 2
DEFAULT_BATCH_SIZE = 4
DEFAULT_TASKS_PER_CHILD = 1

EXPECTED_INPUT_DIM = 332

BASE_FACE_IDXS = [
    70, 63, 105, 66,
    336, 296, 334, 293,
    33, 160, 158, 133, 153, 144,
    362, 385, 387, 263, 373, 380,
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
]
FACEPLUS_IDXS = [
    1, 4,
    152,
    234, 454,
    172, 397,
    148, 377,
    13, 14,
    78, 308,
    82, 312,
]
FACE_IDXS = BASE_FACE_IDXS + FACEPLUS_IDXS
POSE_IDXS = [0, 7, 8, 11, 12, 13, 14]
HAND_TIP_IDXS = [4, 8, 12, 16, 20]
FACE_ANCHOR_IDXS = [1, 13, 152, 234, 454]


def parse_filekeys(filekeys, filekey_range):
    parsed = []
    if filekeys:
        for item in filekeys.split(","):
            item = item.strip()
            if item:
                parsed.append(int(item))

    if filekey_range:
        start_raw, end_raw = filekey_range.split(":", maxsplit=1)
        parsed.extend(range(int(start_raw), int(end_raw) + 1))

    return sorted(set(parsed))


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


def load_mapping(mapping_path):
    with Path(mapping_path).open("r", encoding="utf-8") as f:
        mapping_data = json.load(f)

    filename_to_word = {}
    for word, videos in mapping_data.items():
        for video in videos:
            filename_to_word[video["filename"]] = word
    return filename_to_word


def find_zip_files(root):
    return sorted(root.rglob("*.zip"), key=lambda path: path.stat().st_mtime)


def run_aihub_download(aihub_shell, dataset_key, file_key, api_key):
    command = [
        str(aihub_shell),
        "-mode",
        "d",
        "-datasetkey",
        str(dataset_key),
        "-filekey",
        str(file_key),
    ]
    if api_key:
        command.extend(["-aihubapikey", api_key])

    print("RUN", " ".join(command))
    subprocess.run(command, check=True)


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


def process_local_video(job):
    video_path, output_path = job
    cap = None

    try:
        video_data = []
        last_pose = None
        last_face = None
        cap = cv2.VideoCapture(str(video_path))

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
                return f"FAIL shape mismatch {npy_data.shape}: {video_path.name}"

            output_path.parent.mkdir(parents=True, exist_ok=True)
            np.save(output_path, npy_data)
            return f"DONE {output_path}"

        return f"FAIL no usable pose data: {video_path.name}"

    except Exception as e:
        return f"ERROR {video_path.name}: {e}"

    finally:
        if cap is not None:
            cap.release()
        if video_path.exists():
            video_path.unlink()
        gc.collect()


def chunked(items, chunk_size):
    for start in range(0, len(items), chunk_size):
        yield items[start:start + chunk_size]


def process_jobs_in_batches(jobs, max_workers, batch_size, tasks_per_child):
    completed = 0
    total = len(jobs)

    for batch_idx, batch in enumerate(chunked(jobs, batch_size), start=1):
        print(
            f"\nBatch {batch_idx}: {len(batch)} jobs "
            f"(progress {completed}/{total}, workers={max_workers}, "
            f"tasks_per_child={tasks_per_child})"
        )
        with mp_context.Pool(
            processes=max_workers,
            maxtasksperchild=tasks_per_child,
        ) as pool:
            for result in pool.imap_unordered(process_local_video, batch):
                completed += 1
                print(f"[{completed}/{total}] {result}")
                time.sleep(0.05)


def extract_zip_targets_to_temp(zip_path, filename_to_word, output_dir, temp_dir, overwrite):
    jobs = []
    matched = 0
    skipped = 0

    with zipfile.ZipFile(zip_path, "r") as archive:
        for file_info in archive.infolist():
            filename = os.path.basename(file_info.filename)
            word = filename_to_word.get(filename)
            if word is None:
                continue

            matched += 1
            output_path = output_dir / word / (Path(filename).stem + ".npy")
            if output_path.exists() and not overwrite:
                skipped += 1
                continue

            temp_word_dir = temp_dir / word
            temp_word_dir.mkdir(parents=True, exist_ok=True)
            temp_video_path = temp_word_dir / filename

            with archive.open(file_info) as source, temp_video_path.open("wb") as target:
                shutil.copyfileobj(source, target)
            jobs.append((temp_video_path, output_path))

    print(f"ZIP scan: {zip_path.name} matched={matched}, pending={len(jobs)}, skipped={skipped}")
    return jobs


def parse_args():
    parser = argparse.ArgumentParser(
        description="Download AI Hub ZIP files and convert selected mp4 files directly to local npy files."
    )
    parser.add_argument("--dataset-key", required=True, help="AI Hub dataset key.")
    parser.add_argument("--filekeys", help="Comma-separated AI Hub file keys, e.g. 39547,39548.")
    parser.add_argument("--filekey-range", help="Inclusive range, e.g. 39547:39577.")
    parser.add_argument("--aihubshell", default="./aihubshell", help="Path to aihubshell.")
    parser.add_argument("--aihub-api-key", default=os.environ.get("AIHUB_API_KEY"))
    parser.add_argument("--download-dir", default="./aihub_downloads_v7")
    parser.add_argument("--mapping", default=DEFAULT_MAPPING)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--delete-zip", action="store_true")
    parser.add_argument("--keep-temp-mp4", action="store_true")
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
    filekeys = parse_filekeys(args.filekeys, args.filekey_range)
    if not filekeys:
        raise ValueError("Provide --filekeys or --filekey-range.")

    download_dir = Path(args.download_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    aihub_shell = Path(args.aihubshell).resolve()
    mapping_path = Path(args.mapping).resolve()

    download_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)

    if not aihub_shell.exists():
        raise FileNotFoundError(f"aihubshell not found: {aihub_shell}")
    if not mapping_path.exists():
        raise FileNotFoundError(f"mapping not found: {mapping_path}")

    filename_to_word = load_mapping(mapping_path)
    processed_zips = set()

    os.chdir(download_dir)
    for file_key in filekeys:
        before = set(find_zip_files(download_dir))
        run_aihub_download(aihub_shell, args.dataset_key, file_key, args.aihub_api_key)
        after = set(find_zip_files(download_dir))
        new_zips = sorted(after - before, key=lambda path: path.stat().st_mtime)

        if not new_zips:
            print(f"No new ZIP found for filekey={file_key}. Check aihubshell output.")
            continue

        for zip_path in new_zips:
            if zip_path in processed_zips:
                continue

            with tempfile.TemporaryDirectory(prefix="aihub_mp4_") as temp_root:
                temp_dir = Path(temp_root)
                jobs = extract_zip_targets_to_temp(
                    zip_path=zip_path,
                    filename_to_word=filename_to_word,
                    output_dir=output_dir,
                    temp_dir=temp_dir,
                    overwrite=args.overwrite,
                )
                if jobs:
                    process_jobs_in_batches(
                        jobs,
                        max_workers=max(args.max_workers, 1),
                        batch_size=max(args.batch_size, args.max_workers, 1),
                        tasks_per_child=max(args.tasks_per_child, 1),
                    )
                if args.keep_temp_mp4:
                    keep_dir = download_dir / f"{zip_path.stem}_matched_mp4"
                    if keep_dir.exists():
                        shutil.rmtree(keep_dir)
                    shutil.copytree(temp_dir, keep_dir)
                    print(f"Kept temp mp4 files: {keep_dir}")

            processed_zips.add(zip_path)
            if args.delete_zip:
                zip_path.unlink()
                print(f"Deleted ZIP: {zip_path}")


if __name__ == "__main__":
    main()
