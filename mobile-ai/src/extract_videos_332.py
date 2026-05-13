from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from sign_features import INPUT_DIM, ContinuousFeatureExtractor


SCHEMA = "isolated_332_v1"
VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".mkv"}
DEFAULT_OUTPUT_ROOT = Path(__file__).resolve().parents[1] / "data" / "isolated_332"
DEFAULT_TASK_PATH = (
    Path(__file__).resolve().parents[2]
    / "mobile"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "models"
    / "holistic_landmarker.task"
)


def create_holistic_landmarker(task_path: Path):
    if not task_path.exists():
        raise FileNotFoundError(
            f"Holistic task file not found: {task_path}. "
            "Download it from mobile/README.md or pass --task-path."
        )

    from mediapipe.tasks import python
    from mediapipe.tasks.python import vision

    base_options = python.BaseOptions(model_asset_path=str(task_path))
    options = vision.HolisticLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        min_face_detection_confidence=0.5,
        min_face_landmarks_confidence=0.5,
        min_pose_detection_confidence=0.5,
        min_pose_landmarks_confidence=0.5,
        min_hand_landmarks_confidence=0.5,
    )
    return vision.HolisticLandmarker.create_from_options(options)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract Android-compatible 332-dim features from sign video files.",
    )
    parser.add_argument(
        "--source-root",
        type=Path,
        required=True,
        help="Root directory containing label folders with video files.",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=DEFAULT_OUTPUT_ROOT,
        help="Output directory for extracted npy/json files.",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "config" / "label_manifest_mvp.json",
        help="JSON manifest containing canonical label mapping.",
    )
    parser.add_argument(
        "--labels",
        default="",
        help="Optional comma-separated canonical labels to keep. Defaults to manifest target_labels + blank_sources.",
    )
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--hand-forward-fill", action="store_true")
    parser.add_argument("--max-files-per-label", type=int, default=0)
    parser.add_argument(
        "--task-path",
        type=Path,
        default=DEFAULT_TASK_PATH,
        help="Path to holistic_landmarker.task.",
    )
    parser.add_argument(
        "--backend",
        choices=("tasks", "solutions"),
        default="tasks",
        help="MediaPipe backend. 'solutions' can be useful on headless servers.",
    )
    return parser.parse_args()


def load_manifest(path: Path) -> dict:
    if not path.exists():
        return {
            "target_labels": [],
            "blank_sources": [],
            "canonical_labels": {},
        }
    return json.loads(path.read_text(encoding="utf-8"))


def requested_labels(args: argparse.Namespace, manifest: dict) -> set[str]:
    if args.labels.strip():
        return {label.strip() for label in args.labels.split(",") if label.strip()}

    labels = set(manifest.get("target_labels", []))
    canonical = manifest.get("canonical_labels", {})
    for source in manifest.get("blank_sources", []):
        labels.add(canonical.get(source, source))
    return labels


def canonical_label_for(path: Path, source_root: Path, manifest: dict) -> str:
    rel = path.relative_to(source_root)
    source_label = rel.parts[0]
    return manifest.get("canonical_labels", {}).get(source_label, source_label)


def iter_video_files(
    source_root: Path,
    manifest: dict,
    keep_labels: set[str],
    max_files_per_label: int,
) -> list[tuple[Path, str, str]]:
    counts: dict[str, int] = {}
    items: list[tuple[Path, str, str]] = []

    for path in sorted(source_root.rglob("*")):
        if path.suffix.lower() not in VIDEO_EXTENSIONS:
            continue

        source_label = path.relative_to(source_root).parts[0]
        canonical = manifest.get("canonical_labels", {}).get(source_label, source_label)
        if keep_labels and canonical not in keep_labels:
            continue

        current_count = counts.get(canonical, 0)
        if max_files_per_label > 0 and current_count >= max_files_per_label:
            continue

        counts[canonical] = current_count + 1
        items.append((path, source_label, canonical))

    return items


def extract_video(
    path: Path,
    hand_forward_fill: bool,
    task_path: Path,
    backend: str = "tasks",
) -> tuple[np.ndarray, dict]:
    if backend == "solutions":
        return extract_video_with_solutions(path, hand_forward_fill)
    return extract_video_with_tasks(path, hand_forward_fill, task_path)


def extract_video_with_tasks(path: Path, hand_forward_fill: bool, task_path: Path) -> tuple[np.ndarray, dict]:
    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video: {path}")

    fps = cap.get(cv2.CAP_PROP_FPS)
    if not fps or fps <= 1e-6:
        fps = 30.0

    extractor = ContinuousFeatureExtractor(hand_forward_fill=hand_forward_fill)
    frames: list[np.ndarray] = []
    has_hand_count = 0
    raw_frame_count = 0

    with create_holistic_landmarker(task_path) as landmarker:
        while True:
            success, frame = cap.read()
            if not success:
                break

            raw_frame_count += 1
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=image_rgb)
            timestamp_ms = int(round((raw_frame_count - 1) / fps * 1000.0))
            results = landmarker.detect_for_video(mp_image, timestamp_ms)
            values, has_hands = extractor.encode(results)
            frames.append(values)
            if has_hands:
                has_hand_count += 1

    cap.release()

    if not frames:
        raise ValueError(f"No frames extracted: {path}")

    sequence = np.asarray(frames, dtype=np.float32)
    if sequence.ndim != 2 or sequence.shape[1] != INPUT_DIM:
        raise ValueError(f"Unexpected feature shape {sequence.shape}: {path}")

    stats = {
        "fps": float(fps),
        "raw_frames": int(raw_frame_count),
        "frames": int(sequence.shape[0]),
        "has_hand_frames": int(has_hand_count),
        "backend": "tasks",
    }
    return sequence, stats


def extract_video_with_solutions(path: Path, hand_forward_fill: bool) -> tuple[np.ndarray, dict]:
    if not hasattr(mp, "solutions") or not hasattr(mp.solutions, "holistic"):
        raise RuntimeError(
            "mediapipe.solutions.holistic is not available. "
            "Try a legacy MediaPipe env, e.g. Python 3.10 + mediapipe==0.10.14."
        )

    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video: {path}")

    fps = cap.get(cv2.CAP_PROP_FPS)
    if not fps or fps <= 1e-6:
        fps = 30.0

    extractor = ContinuousFeatureExtractor(hand_forward_fill=hand_forward_fill)
    frames: list[np.ndarray] = []
    has_hand_count = 0
    raw_frame_count = 0

    with mp.solutions.holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        smooth_landmarks=True,
        enable_segmentation=False,
        refine_face_landmarks=True,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while True:
            success, frame = cap.read()
            if not success:
                break

            raw_frame_count += 1
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            image_rgb.flags.writeable = False
            results = holistic.process(image_rgb)
            values, has_hands = extractor.encode(results)
            frames.append(values)
            if has_hands:
                has_hand_count += 1

    cap.release()

    if not frames:
        raise ValueError(f"No frames extracted: {path}")

    sequence = np.asarray(frames, dtype=np.float32)
    if sequence.ndim != 2 or sequence.shape[1] != INPUT_DIM:
        raise ValueError(f"Unexpected feature shape {sequence.shape}: {path}")

    stats = {
        "fps": float(fps),
        "raw_frames": int(raw_frame_count),
        "frames": int(sequence.shape[0]),
        "has_hand_frames": int(has_hand_count),
        "backend": "solutions",
    }
    return sequence, stats


def output_paths(output_root: Path, canonical_label: str, source_path: Path) -> tuple[Path, Path]:
    label_dir = output_root / canonical_label
    stem = source_path.stem
    return label_dir / f"{stem}.npy", label_dir / f"{stem}.json"


def save_output(
    output_root: Path,
    source_path: Path,
    source_label: str,
    canonical_label: str,
    sequence: np.ndarray,
    stats: dict,
) -> tuple[Path, Path]:
    npy_path, json_path = output_paths(output_root, canonical_label, source_path)
    npy_path.parent.mkdir(parents=True, exist_ok=True)

    np.save(npy_path, sequence.astype(np.float32))
    metadata = {
        "schema": SCHEMA,
        "source_video": str(source_path),
        "source_label": source_label,
        "label": canonical_label,
        "feature_dim": INPUT_DIM,
        "created_at": datetime.now().isoformat(timespec="seconds"),
        **stats,
    }
    json_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return npy_path, json_path


def main() -> None:
    args = parse_args()
    source_root = args.source_root.resolve()
    output_root = args.output_root.resolve()
    manifest = load_manifest(args.manifest)
    keep_labels = requested_labels(args, manifest)
    video_files = iter_video_files(
        source_root=source_root,
        manifest=manifest,
        keep_labels=keep_labels,
        max_files_per_label=args.max_files_per_label,
    )

    print(f"source_root={source_root}")
    print(f"output_root={output_root}")
    print(f"labels={sorted(keep_labels)}")
    print(f"videos={len(video_files)}")

    for index, (video_path, source_label, canonical_label) in enumerate(video_files, start=1):
        npy_path, json_path = output_paths(output_root, canonical_label, video_path)
        if npy_path.exists() and json_path.exists() and not args.overwrite:
            print(f"[{index}/{len(video_files)}] skip existing: {npy_path}")
            continue

        try:
            sequence, stats = extract_video(
                video_path,
                hand_forward_fill=args.hand_forward_fill,
                task_path=args.task_path.resolve(),
                backend=args.backend,
            )
            save_output(
                output_root=output_root,
                source_path=video_path,
                source_label=source_label,
                canonical_label=canonical_label,
                sequence=sequence,
                stats=stats,
            )
            print(
                f"[{index}/{len(video_files)}] saved {canonical_label}: "
                f"shape={tuple(sequence.shape)}, hands={stats['has_hand_frames']}/{stats['frames']}"
            )
        except Exception as exc:
            print(f"[{index}/{len(video_files)}] failed {video_path}: {exc}")


if __name__ == "__main__":
    main()
