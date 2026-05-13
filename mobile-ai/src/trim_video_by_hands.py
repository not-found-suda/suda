from __future__ import annotations

import argparse
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

import cv2
import mediapipe as mp

from extract_videos_332 import DEFAULT_TASK_PATH, VIDEO_EXTENSIONS, create_holistic_landmarker


@dataclass(frozen=True)
class TrimBounds:
    start_frame: int
    end_frame: int
    raw_start_frame: int
    raw_end_frame: int
    total_frames: int
    hand_frames: int
    fps: float

    @property
    def start_sec(self) -> float:
        return self.start_frame / self.fps

    @property
    def end_sec(self) -> float:
        return self.end_frame / self.fps

    @property
    def duration_sec(self) -> float:
        return max(0.0, self.end_sec - self.start_sec)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Trim sign videos to the hand-active region, with optional padding. "
            "Useful for mobile selfie test clips without manual start/end labels."
        ),
    )
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument("--input", type=Path, help="Single video file to trim.")
    input_group.add_argument("--input-dir", type=Path, help="Directory containing videos to trim.")
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--recursive", action="store_true", help="Scan input-dir recursively.")
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument(
        "--backend",
        choices=("tasks", "solutions"),
        default="solutions",
        help="MediaPipe backend. Use solutions with mobile-ai-legacy.",
    )
    parser.add_argument(
        "--task-path",
        type=Path,
        default=DEFAULT_TASK_PATH,
        help="Path to holistic_landmarker.task for --backend tasks.",
    )
    parser.add_argument(
        "--padding-sec",
        type=float,
        default=0.5,
        help="Seconds to keep before first hand frame and after last hand frame.",
    )
    parser.add_argument(
        "--gap-sec",
        type=float,
        default=0.35,
        help="Fill no-hand gaps shorter than this many seconds inside the active region.",
    )
    parser.add_argument(
        "--min-active-sec",
        type=float,
        default=0.25,
        help="Ignore active regions shorter than this many seconds.",
    )
    parser.add_argument(
        "--suffix",
        default="_mobile_trim",
        help="Suffix appended to output video stems.",
    )
    parser.add_argument(
        "--prefer-ffmpeg",
        action="store_true",
        help="Use ffmpeg stream copy when available to preserve phone rotation metadata.",
    )
    return parser.parse_args()


def iter_videos(args: argparse.Namespace) -> list[Path]:
    if args.input:
        return [args.input]

    pattern = "**/*" if args.recursive else "*"
    return sorted(
        path
        for path in args.input_dir.glob(pattern)
        if path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS
    )


def hand_activity_with_solutions(path: Path) -> tuple[list[bool], float]:
    if not hasattr(mp, "solutions") or not hasattr(mp.solutions, "holistic"):
        raise RuntimeError(
            "mediapipe.solutions.holistic is not available. "
            "Use conda activate mobile-ai-legacy or pass --backend tasks."
        )

    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video: {path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    activity: list[bool] = []
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
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            image_rgb.flags.writeable = False
            results = holistic.process(image_rgb)
            activity.append(
                results.left_hand_landmarks is not None
                or results.right_hand_landmarks is not None
            )

    cap.release()
    return activity, float(fps)


def hand_activity_with_tasks(path: Path, task_path: Path) -> tuple[list[bool], float]:
    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video: {path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    activity: list[bool] = []
    with create_holistic_landmarker(task_path) as landmarker:
        frame_index = 0
        while True:
            success, frame = cap.read()
            if not success:
                break
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=image_rgb)
            timestamp_ms = int(round(frame_index / fps * 1000.0))
            results = landmarker.detect_for_video(mp_image, timestamp_ms)
            activity.append(
                results.left_hand_landmarks is not None
                or results.right_hand_landmarks is not None
            )
            frame_index += 1

    cap.release()
    return activity, float(fps)


def smooth_activity(activity: list[bool], gap_frames: int) -> list[bool]:
    if not activity or gap_frames <= 0:
        return activity

    smoothed = activity[:]
    index = 0
    while index < len(activity):
        if activity[index]:
            index += 1
            continue
        gap_start = index
        while index < len(activity) and not activity[index]:
            index += 1
        gap_end = index
        has_active_before = gap_start > 0 and activity[gap_start - 1]
        has_active_after = gap_end < len(activity) and activity[gap_end]
        if has_active_before and has_active_after and gap_end - gap_start <= gap_frames:
            for fill_index in range(gap_start, gap_end):
                smoothed[fill_index] = True
    return smoothed


def find_bounds(
    activity: list[bool],
    fps: float,
    padding_sec: float,
    gap_sec: float,
    min_active_sec: float,
) -> TrimBounds | None:
    if not activity:
        return None

    gap_frames = max(0, int(round(gap_sec * fps)))
    min_active_frames = max(1, int(round(min_active_sec * fps)))
    padding_frames = max(0, int(round(padding_sec * fps)))
    smoothed = smooth_activity(activity, gap_frames)

    active_indices = [index for index, is_active in enumerate(smoothed) if is_active]
    if not active_indices:
        return None

    raw_start = min(active_indices)
    raw_end = max(active_indices) + 1
    if raw_end - raw_start < min_active_frames:
        return None

    start = max(0, raw_start - padding_frames)
    end = min(len(activity), raw_end + padding_frames)
    return TrimBounds(
        start_frame=start,
        end_frame=end,
        raw_start_frame=raw_start,
        raw_end_frame=raw_end,
        total_frames=len(activity),
        hand_frames=sum(1 for item in activity if item),
        fps=fps,
    )


def trim_with_ffmpeg(input_path: Path, output_path: Path, bounds: TrimBounds, overwrite: bool) -> bool:
    if shutil.which("ffmpeg") is None:
        return False

    command = [
        "ffmpeg",
        "-y" if overwrite else "-n",
        "-hide_banner",
        "-loglevel",
        "error",
        "-ss",
        f"{bounds.start_sec:.3f}",
        "-to",
        f"{bounds.end_sec:.3f}",
        "-i",
        str(input_path),
        "-c",
        "copy",
        str(output_path),
    ]
    subprocess.run(command, check=True)
    return True


def trim_with_opencv(input_path: Path, output_path: Path, bounds: TrimBounds, overwrite: bool) -> None:
    if output_path.exists() and not overwrite:
        raise FileExistsError(f"Output exists: {output_path}")

    cap = cv2.VideoCapture(str(input_path))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video: {input_path}")

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS) or bounds.fps
    output_path.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(output_path),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        (width, height),
    )
    if not writer.isOpened():
        cap.release()
        raise RuntimeError(f"Could not create output video: {output_path}")

    frame_index = 0
    while True:
        success, frame = cap.read()
        if not success:
            break
        if bounds.start_frame <= frame_index < bounds.end_frame:
            writer.write(frame)
        if frame_index >= bounds.end_frame:
            break
        frame_index += 1

    writer.release()
    cap.release()


def output_path_for(output_root: Path, input_path: Path, suffix: str) -> Path:
    return output_root / f"{input_path.stem}{suffix}.mp4"


def process_video(args: argparse.Namespace, input_path: Path) -> None:
    if args.backend == "solutions":
        activity, fps = hand_activity_with_solutions(input_path)
    else:
        activity, fps = hand_activity_with_tasks(input_path, args.task_path)

    bounds = find_bounds(
        activity=activity,
        fps=fps,
        padding_sec=args.padding_sec,
        gap_sec=args.gap_sec,
        min_active_sec=args.min_active_sec,
    )
    if bounds is None:
        print(f"skip {input_path.name}: no stable hand-active region")
        return

    output_path = output_path_for(args.output_root, input_path, args.suffix)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if args.prefer_ffmpeg and trim_with_ffmpeg(input_path, output_path, bounds, args.overwrite):
        writer = "ffmpeg"
    else:
        trim_with_opencv(input_path, output_path, bounds, args.overwrite)
        writer = "opencv"

    print(
        f"saved {output_path} "
        f"frames={bounds.start_frame}-{bounds.end_frame}/{bounds.total_frames} "
        f"raw={bounds.raw_start_frame}-{bounds.raw_end_frame} "
        f"time={bounds.start_sec:.2f}-{bounds.end_sec:.2f}s "
        f"hands={bounds.hand_frames}/{bounds.total_frames} "
        f"writer={writer}"
    )


def main() -> None:
    args = parse_args()
    videos = iter_videos(args)
    if not videos:
        raise SystemExit("No videos found.")

    for input_path in videos:
        try:
            process_video(args, input_path)
        except Exception as exc:
            print(f"failed {input_path}: {exc}")


if __name__ == "__main__":
    main()
