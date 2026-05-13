from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

import numpy as np

from extract_videos_332 import DEFAULT_TASK_PATH, VIDEO_EXTENSIONS, extract_video
from sign_features import INPUT_DIM


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract 332-dim features from videos in a flat directory, preserving video stems.",
    )
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--recursive", action="store_true")
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--hand-forward-fill", action="store_true")
    parser.add_argument("--backend", choices=("tasks", "solutions"), default="solutions")
    parser.add_argument("--task-path", type=Path, default=DEFAULT_TASK_PATH)
    return parser.parse_args()


def iter_videos(source_root: Path, recursive: bool) -> list[Path]:
    pattern = "**/*" if recursive else "*"
    return sorted(
        path
        for path in source_root.glob(pattern)
        if path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS
    )


def save_output(output_root: Path, source_path: Path, sequence: np.ndarray, stats: dict) -> None:
    output_root.mkdir(parents=True, exist_ok=True)
    npy_path = output_root / f"{source_path.stem}.npy"
    json_path = output_root / f"{source_path.stem}.json"
    np.save(npy_path, sequence.astype(np.float32))
    json_path.write_text(
        json.dumps(
            {
                "source_video": str(source_path),
                "feature_dim": INPUT_DIM,
                "created_at": datetime.now().isoformat(timespec="seconds"),
                **stats,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def main() -> None:
    args = parse_args()
    videos = iter_videos(args.source_root, args.recursive)
    print(f"source_root={args.source_root.resolve()}")
    print(f"output_root={args.output_root.resolve()}")
    print(f"videos={len(videos)}")

    for index, video_path in enumerate(videos, start=1):
        npy_path = args.output_root / f"{video_path.stem}.npy"
        json_path = args.output_root / f"{video_path.stem}.json"
        if npy_path.exists() and json_path.exists() and not args.overwrite:
            print(f"[{index}/{len(videos)}] skip existing: {npy_path}")
            continue
        try:
            sequence, stats = extract_video(
                path=video_path,
                hand_forward_fill=args.hand_forward_fill,
                task_path=args.task_path.resolve(),
                backend=args.backend,
            )
            save_output(args.output_root, video_path, sequence, stats)
            print(
                f"[{index}/{len(videos)}] saved {video_path.name}: "
                f"shape={tuple(sequence.shape)}, hands={stats['has_hand_frames']}/{stats['frames']}"
            )
        except Exception as exc:
            print(f"[{index}/{len(videos)}] failed {video_path}: {exc}")


if __name__ == "__main__":
    main()
