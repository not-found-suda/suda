from __future__ import annotations

import argparse
import csv
import json
from datetime import datetime
from pathlib import Path

import numpy as np

from extract_videos_332 import DEFAULT_TASK_PATH, VIDEO_EXTENSIONS, extract_video
from sign_features import INPUT_DIM


SCHEMA = "sequence_332_v1"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract [T, 332] features for videos listed in a morpheme manifest CSV.",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, required=True, help="Root containing downloaded mp4 files.")
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "data" / "sequence_332",
    )
    parser.add_argument("--labels", default="", help="Optional comma-separated labels to keep.")
    parser.add_argument("--kind", default="", help="Optional kind filter, e.g. WORD or SEN.")
    parser.add_argument("--split", default="", help="Optional split filter, e.g. train or val.")
    parser.add_argument("--max-files", type=int, default=0)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--hand-forward-fill", action="store_true")
    parser.add_argument("--task-path", type=Path, default=DEFAULT_TASK_PATH)
    parser.add_argument(
        "--backend",
        choices=("tasks", "solutions"),
        default="tasks",
        help="MediaPipe backend. 'solutions' can be useful on headless servers.",
    )
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def build_video_index(source_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(source_root.rglob("*")):
        if path.suffix.lower() in VIDEO_EXTENSIONS:
            index.setdefault(path.name, path)
    return index


def row_has_label(row: dict, keep_labels: set[str]) -> bool:
    if not keep_labels:
        return True
    labels = set((row.get("contains_target") or row.get("labels") or "").split())
    return bool(labels.intersection(keep_labels))


def filter_rows(rows: list[dict], args: argparse.Namespace) -> list[dict]:
    keep_labels = {label.strip() for label in args.labels.split(",") if label.strip()}
    selected: list[dict] = []
    seen_videos: set[str] = set()
    for row in rows:
        video_name = row.get("video_name", "")
        if not video_name or video_name in seen_videos:
            continue
        if args.kind and row.get("kind") != args.kind:
            continue
        if args.split and row.get("split") != args.split:
            continue
        if not row_has_label(row, keep_labels):
            continue
        seen_videos.add(video_name)
        selected.append(row)
    return selected


def output_paths(output_root: Path, row: dict) -> tuple[Path, Path]:
    split = row.get("split") or "unknown"
    kind = row.get("kind") or "unknown"
    stem = Path(row["video_name"]).stem
    directory = output_root / split / kind
    return directory / f"{stem}.npy", directory / f"{stem}.json"


def save_output(
    npy_path: Path,
    json_path: Path,
    source_path: Path,
    row: dict,
    sequence: np.ndarray,
    stats: dict,
) -> None:
    npy_path.parent.mkdir(parents=True, exist_ok=True)
    np.save(npy_path, sequence.astype(np.float32))
    metadata = {
        "schema": SCHEMA,
        "source_video": str(source_path),
        "video_name": row.get("video_name", ""),
        "split": row.get("split", ""),
        "kind": row.get("kind", ""),
        "labels": row.get("labels", ""),
        "contains_target": row.get("contains_target", ""),
        "duration": row.get("duration", ""),
        "feature_dim": INPUT_DIM,
        "created_at": datetime.now().isoformat(timespec="seconds"),
        **stats,
    }
    json_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    args = parse_args()
    rows = filter_rows(read_rows(args.manifest), args)
    video_index = build_video_index(args.source_root.resolve())
    rows = [row for row in rows if row.get("video_name", "") in video_index]
    if args.max_files > 0:
        rows = rows[: args.max_files]
    output_root = args.output_root.resolve()

    print(f"manifest_rows={len(rows)}")
    print(f"indexed_videos={len(video_index)}")
    print(f"output_root={output_root}")

    missing = 0
    for index, row in enumerate(rows, start=1):
        video_name = row["video_name"]
        source_path = video_index.get(video_name)
        if not source_path:
            missing += 1
            print(f"[{index}/{len(rows)}] missing video: {video_name}")
            continue

        npy_path, json_path = output_paths(output_root, row)
        if npy_path.exists() and json_path.exists() and not args.overwrite:
            print(f"[{index}/{len(rows)}] skip existing: {npy_path}")
            continue

        try:
            sequence, stats = extract_video(
                source_path,
                hand_forward_fill=args.hand_forward_fill,
                task_path=args.task_path.resolve(),
                backend=args.backend,
            )
            save_output(npy_path, json_path, source_path, row, sequence, stats)
            print(
                f"[{index}/{len(rows)}] saved {video_name}: "
                f"shape={tuple(sequence.shape)}, hands={stats['has_hand_frames']}/{stats['frames']}"
            )
        except Exception as exc:
            print(f"[{index}/{len(rows)}] failed {video_name}: {exc}")
    print(f"missing_videos={missing}")


if __name__ == "__main__":
    main()
