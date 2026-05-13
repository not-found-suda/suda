from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from sign_features import INPUT_DIM, ContinuousFeatureExtractor


SCHEMA = "continuous_v1"
DEFAULT_DATA_ROOT = Path(__file__).resolve().parents[1] / "data" / SCHEMA
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
WINDOW_NAME = "Continuous Sign Collector"


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
    parser = argparse.ArgumentParser(description="Collect continuous sign clips as npy/json pairs.")
    parser.add_argument(
        "--split",
        choices=("train", "holdout"),
        required=True,
        help="Dataset split to save into.",
    )
    parser.add_argument(
        "--labels",
        required=True,
        help='Space-separated gloss sequence, e.g. "장난감 조심".',
    )
    parser.add_argument("--data-root", type=Path, default=DEFAULT_DATA_ROOT)
    parser.add_argument("--camera-index", type=int, default=0)
    parser.add_argument("--min-frames", type=int, default=15)
    parser.add_argument("--prefix", default="sample")
    parser.add_argument("--hand-forward-fill", action="store_true")
    parser.add_argument(
        "--task-path",
        type=Path,
        default=DEFAULT_TASK_PATH,
        help="Path to holistic_landmarker.task.",
    )
    return parser.parse_args()


def next_sample_stem(split_dir: Path, prefix: str) -> str:
    existing = sorted(split_dir.glob(f"{prefix}_*.json"))
    if not existing:
        return f"{prefix}_0001"

    max_index = 0
    for path in existing:
        suffix = path.stem.replace(f"{prefix}_", "")
        if suffix.isdigit():
            max_index = max(max_index, int(suffix))
    return f"{prefix}_{max_index + 1:04d}"


def draw_status(frame, labels: list[str], recording: bool, frame_count: int, split: str) -> None:
    status = "REC" if recording else "IDLE"
    color = (0, 0, 255) if recording else (0, 200, 255)
    cv2.putText(frame, f"{status} | split={split}", (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
    cv2.putText(frame, f"labels={' '.join(labels)}", (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.75, (255, 255, 255), 2)
    cv2.putText(frame, f"frames={frame_count}", (20, 105), cv2.FONT_HERSHEY_SIMPLEX, 0.75, (255, 255, 255), 2)
    cv2.putText(frame, "s/space: start-stop | q/esc: quit", (20, 140), cv2.FONT_HERSHEY_SIMPLEX, 0.65, (0, 255, 0), 2)


def save_sample(
    split_dir: Path,
    prefix: str,
    labels: list[str],
    frames: list[np.ndarray],
    fps: float,
) -> tuple[Path, Path]:
    split_dir.mkdir(parents=True, exist_ok=True)
    stem = next_sample_stem(split_dir, prefix)
    npy_path = split_dir / f"{stem}.npy"
    json_path = split_dir / f"{stem}.json"

    sequence = np.asarray(frames, dtype=np.float32)
    if sequence.ndim != 2 or sequence.shape[1] != INPUT_DIM:
        raise ValueError(f"Unexpected sequence shape: {sequence.shape}")

    np.save(npy_path, sequence)
    metadata = {
        "schema": SCHEMA,
        "labels": labels,
        "fps": float(fps),
        "frames": int(sequence.shape[0]),
        "feature_dim": INPUT_DIM,
        "created_at": datetime.now().isoformat(timespec="seconds"),
    }
    json_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return npy_path, json_path


def main() -> None:
    args = parse_args()
    labels = [label.strip() for label in args.labels.split() if label.strip()]
    if not labels:
        raise ValueError("--labels must include at least one gloss.")

    split_dir = args.data_root / args.split
    cap = cv2.VideoCapture(args.camera_index)
    if not cap.isOpened():
        raise RuntimeError(f"Could not open camera index {args.camera_index}.")

    fps = cap.get(cv2.CAP_PROP_FPS)
    if not fps or fps <= 1e-6:
        fps = 30.0

    extractor = ContinuousFeatureExtractor(hand_forward_fill=args.hand_forward_fill)
    recording = False
    frames: list[np.ndarray] = []

    frame_index = 0
    with create_holistic_landmarker(args.task_path.resolve()) as landmarker:
        while True:
            success, frame = cap.read()
            if not success:
                break

            frame_index += 1
            frame = cv2.flip(frame, 1)
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            timestamp_ms = int(round((frame_index - 1) / fps * 1000.0))
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=image_rgb)
            results = landmarker.detect_for_video(mp_image, timestamp_ms)

            if recording:
                features, _ = extractor.encode(results)
                frames.append(features)

            draw_status(frame, labels, recording, len(frames), args.split)
            cv2.imshow(WINDOW_NAME, frame)

            key = cv2.waitKey(1) & 0xFF
            if key in (ord("q"), 27) and not recording:
                break

            if key in (ord("s"), 32):
                if recording:
                    recording = False
                    if len(frames) < args.min_frames:
                        print(f"Discarded: only {len(frames)} frames (< {args.min_frames}).")
                    else:
                        npy_path, json_path = save_sample(split_dir, args.prefix, labels, frames, fps)
                        print(f"Saved: {npy_path}")
                        print(f"Saved: {json_path}")
                    frames = []
                    extractor.reset()
                else:
                    print(f"Recording labels: {' '.join(labels)}")
                    frames = []
                    extractor.reset()
                    recording = True

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
