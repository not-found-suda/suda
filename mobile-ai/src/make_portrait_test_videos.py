from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import numpy as np


VIDEO_EXTENSIONS = {".mp4", ".mov", ".avi", ".mkv"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create portrait test videos from existing landscape videos. "
            "Use this to check whether a mobile-like 9:16 frame changes word spotting results."
        ),
    )
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument("--input", type=Path, help="Single video file.")
    input_group.add_argument("--input-dir", type=Path, help="Directory containing videos.")
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--recursive", action="store_true")
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument(
        "--mode",
        choices=("center-crop", "pad", "both"),
        default="both",
        help="center-crop cuts to 9:16. pad keeps the full frame and adds bars.",
    )
    parser.add_argument(
        "--width",
        type=int,
        default=720,
        help="Output portrait width.",
    )
    parser.add_argument(
        "--height",
        type=int,
        default=1280,
        help="Output portrait height.",
    )
    parser.add_argument(
        "--crop-x",
        type=float,
        default=0.5,
        help="Horizontal crop center from 0.0(left) to 1.0(right). Default center.",
    )
    parser.add_argument(
        "--crop-y",
        type=float,
        default=0.5,
        help="Vertical crop center from 0.0(top) to 1.0(bottom). Default center.",
    )
    parser.add_argument(
        "--pad-color",
        default="0,0,0",
        help="BGR padding color, e.g. 0,0,0 or 255,255,255.",
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


def parse_color(value: str) -> tuple[int, int, int]:
    parts = [int(part.strip()) for part in value.split(",")]
    if len(parts) != 3:
        raise ValueError("--pad-color must have three comma-separated BGR values.")
    return tuple(max(0, min(255, part)) for part in parts)


def center_crop_to_aspect(
    frame: np.ndarray,
    target_aspect: float,
    crop_x: float,
    crop_y: float,
) -> np.ndarray:
    height, width = frame.shape[:2]
    source_aspect = width / height

    if source_aspect > target_aspect:
        crop_height = height
        crop_width = int(round(crop_height * target_aspect))
    else:
        crop_width = width
        crop_height = int(round(crop_width / target_aspect))

    crop_width = max(1, min(width, crop_width))
    crop_height = max(1, min(height, crop_height))
    max_x = width - crop_width
    max_y = height - crop_height
    x0 = int(round(max_x * max(0.0, min(1.0, crop_x))))
    y0 = int(round(max_y * max(0.0, min(1.0, crop_y))))
    return frame[y0 : y0 + crop_height, x0 : x0 + crop_width]


def resize_with_padding(
    frame: np.ndarray,
    output_width: int,
    output_height: int,
    color: tuple[int, int, int],
) -> np.ndarray:
    height, width = frame.shape[:2]
    scale = min(output_width / width, output_height / height)
    resized_width = max(1, int(round(width * scale)))
    resized_height = max(1, int(round(height * scale)))
    resized = cv2.resize(frame, (resized_width, resized_height), interpolation=cv2.INTER_AREA)

    canvas = np.full((output_height, output_width, 3), color, dtype=np.uint8)
    x0 = (output_width - resized_width) // 2
    y0 = (output_height - resized_height) // 2
    canvas[y0 : y0 + resized_height, x0 : x0 + resized_width] = resized
    return canvas


def transform_frame(
    frame: np.ndarray,
    mode: str,
    output_width: int,
    output_height: int,
    crop_x: float,
    crop_y: float,
    pad_color: tuple[int, int, int],
) -> np.ndarray:
    if mode == "center-crop":
        cropped = center_crop_to_aspect(
            frame=frame,
            target_aspect=output_width / output_height,
            crop_x=crop_x,
            crop_y=crop_y,
        )
        return cv2.resize(cropped, (output_width, output_height), interpolation=cv2.INTER_AREA)

    if mode == "pad":
        return resize_with_padding(
            frame=frame,
            output_width=output_width,
            output_height=output_height,
            color=pad_color,
        )

    raise ValueError(f"Unsupported mode: {mode}")


def output_path_for(
    input_path: Path,
    output_root: Path,
    mode: str,
) -> Path:
    suffix = "portrait_crop" if mode == "center-crop" else "portrait_pad"
    return output_root / f"{input_path.stem}_{suffix}.mp4"


def convert_video(
    input_path: Path,
    output_path: Path,
    mode: str,
    output_width: int,
    output_height: int,
    crop_x: float,
    crop_y: float,
    pad_color: tuple[int, int, int],
    overwrite: bool,
) -> None:
    if output_path.exists() and not overwrite:
        raise FileExistsError(f"Output exists: {output_path}")

    cap = cv2.VideoCapture(str(input_path))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video: {input_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    input_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    input_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(output_path),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        (output_width, output_height),
    )
    if not writer.isOpened():
        cap.release()
        raise RuntimeError(f"Could not create output video: {output_path}")

    frame_count = 0
    while True:
        success, frame = cap.read()
        if not success:
            break
        writer.write(
            transform_frame(
                frame=frame,
                mode=mode,
                output_width=output_width,
                output_height=output_height,
                crop_x=crop_x,
                crop_y=crop_y,
                pad_color=pad_color,
            )
        )
        frame_count += 1

    writer.release()
    cap.release()
    print(
        f"saved {output_path} mode={mode} "
        f"input={input_width}x{input_height} output={output_width}x{output_height} "
        f"frames={frame_count} fps={fps:.3f}"
    )


def main() -> None:
    args = parse_args()
    if args.width <= 0 or args.height <= 0:
        raise ValueError("--width and --height must be positive.")
    pad_color = parse_color(args.pad_color)
    videos = iter_videos(args)
    if not videos:
        raise SystemExit("No videos found.")

    modes = ["center-crop", "pad"] if args.mode == "both" else [args.mode]
    for input_path in videos:
        for mode in modes:
            output_path = output_path_for(input_path, args.output_root, mode)
            try:
                convert_video(
                    input_path=input_path,
                    output_path=output_path,
                    mode=mode,
                    output_width=args.width,
                    output_height=args.height,
                    crop_x=args.crop_x,
                    crop_y=args.crop_y,
                    pad_color=pad_color,
                    overwrite=args.overwrite,
                )
            except Exception as exc:
                print(f"failed {input_path} mode={mode}: {exc}")


if __name__ == "__main__":
    main()
