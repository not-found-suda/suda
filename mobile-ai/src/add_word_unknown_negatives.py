from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter
from pathlib import Path
from random import Random

import numpy as np


DEFAULT_LABELS = [
    "가다",
    "방법",
    "맞다",
    "없다",
    "불가능",
    "불량",
    "공항",
    "샛길",
    "원하다",
    "사거리",
    "에어컨",
    "병원",
    "가능",
    "빨리",
    "기차",
    "명동",
    "서울역",
    "지름길",
    "사진기",
    "알려주다",
    "얼마",
    "아프다",
    "급하다",
    "괜찮다",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Append non-target WORD clips as <background> unknown-word negatives "
            "for a SEN segment classifier manifest."
        ),
    )
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--base-manifest", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--labels", default=",".join(DEFAULT_LABELS))
    parser.add_argument("--negative-label", default="<background>")
    parser.add_argument("--split", default="train")
    parser.add_argument("--val-reals", default="13,14,15,16")
    parser.add_argument("--max-negatives", type=int, default=1000)
    parser.add_argument("--max-per-label", type=int, default=8)
    parser.add_argument("--min-frames", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_manifest(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "split",
        "kind",
        "video_name",
        "feature_path",
        "label",
        "frames",
        "source_video",
        "segment_index",
        "segment_start",
        "segment_end",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fieldnames})


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


def real_id(video_name: str) -> str:
    match = re.search(r"_REAL(\d+)_", video_name)
    return match.group(1) if match else ""


def load_segments(row: dict) -> list[dict]:
    raw = row.get("segments_json") or "[]"
    try:
        segments = json.loads(raw)
    except json.JSONDecodeError:
        return []
    return segments if isinstance(segments, list) else []


def row_labels(row: dict) -> list[str]:
    labels: list[str] = []
    for segment in load_segments(row):
        labels.extend(str(label) for label in segment.get("labels", []))
    if labels:
        return labels
    return [label for label in (row.get("contains_target") or "").split() if label]


def parse_duration(row: dict, feature_path: Path, frame_count: int) -> float:
    raw = row.get("duration", "")
    if raw:
        try:
            duration = float(raw)
            if duration > 0.0:
                return duration
        except ValueError:
            pass

    metadata_path = feature_path.with_suffix(".json")
    if metadata_path.exists():
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        fps = float(metadata.get("fps", 0.0) or 0.0)
        if fps > 0.0:
            return frame_count / fps

    return float(frame_count)


def segment_frame_bounds(segment: dict, duration: float, frame_count: int) -> tuple[int, int]:
    start_sec = max(float(segment.get("start", 0.0)), 0.0)
    end_sec = max(float(segment.get("end", 0.0)), start_sec)
    if duration <= 0.0:
        duration = float(frame_count)
    start = max(0, min(frame_count, int(math.floor(start_sec / duration * frame_count))))
    end = max(start, min(frame_count, int(math.ceil(end_sec / duration * frame_count))))
    return start, end


def word_crop_bounds(row: dict, feature_path: Path, frame_count: int) -> tuple[int, int]:
    duration = parse_duration(row, feature_path, frame_count)
    segments = load_segments(row)
    if segments:
        start, end = segment_frame_bounds(segments[0], duration, frame_count)
        if end > start:
            return start, end
    return 0, frame_count


def safe_output_path(output_root: Path, video_name: str, index: int, start: int, end: int) -> Path:
    stem = Path(video_name).stem
    return output_root / "features" / f"{stem}_wordunk{index:05d}_{start}_{end}.npy"


def main() -> None:
    args = parse_args()
    allowed = {label.strip() for label in args.labels.split(",") if label.strip()}
    val_reals = {item.strip() for item in args.val_reals.split(",") if item.strip()}
    rng = Random(args.seed)

    source_rows = read_rows(args.source_manifest)
    base_rows = read_rows(args.base_manifest)
    feature_index = build_feature_index(args.feature_root)
    out_features = args.output_root / "features"
    out_features.mkdir(parents=True, exist_ok=True)

    candidates_by_label: dict[str, list[dict]] = {}
    stats: Counter[str] = Counter()
    for row in source_rows:
        if row.get("kind") != "WORD":
            continue
        video_name = row.get("video_name", "")
        labels = row_labels(row)
        if not labels:
            stats["skipped_no_label"] += 1
            continue
        if any(label in allowed for label in labels):
            stats["skipped_target_word"] += 1
            continue
        if args.split and ("val" if real_id(video_name) in val_reals else "train") != args.split:
            stats["skipped_split"] += 1
            continue
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            stats["missing_features"] += 1
            continue
        key = " ".join(labels)
        candidates_by_label.setdefault(key, []).append(row)

    selected_rows: list[dict] = []
    for label, rows in sorted(candidates_by_label.items()):
        rng.shuffle(rows)
        selected_rows.extend(rows[: args.max_per_label])
    rng.shuffle(selected_rows)
    if args.max_negatives > 0:
        selected_rows = selected_rows[: args.max_negatives]

    output_rows = list(base_rows)
    negative_rows: list[dict] = []
    for index, row in enumerate(selected_rows):
        video_name = row.get("video_name", "")
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            continue
        features = np.load(feature_path).astype(np.float32, copy=False)
        if features.ndim != 2 or features.shape[0] == 0:
            stats["bad_features"] += 1
            continue
        start, end = word_crop_bounds(row, feature_path, int(features.shape[0]))
        if end - start < args.min_frames:
            stats["skipped_short"] += 1
            continue
        crop = np.nan_to_num(features[start:end]).astype(np.float32, copy=False)
        out_path = safe_output_path(args.output_root, video_name, index, start, end)
        if args.overwrite or not out_path.exists():
            np.save(out_path, crop)
        labels = " ".join(row_labels(row))
        negative_rows.append(
            {
                "split": args.split,
                "kind": "WORD_UNKNOWN_NEG",
                "video_name": f"{video_name}:word_unknown:{start}-{end}:label={labels}",
                "feature_path": str(out_path),
                "label": args.negative_label,
                "frames": int(crop.shape[0]),
                "source_video": video_name,
                "segment_index": "word_unknown",
                "segment_start": start,
                "segment_end": end,
            }
        )

    output_rows.extend(negative_rows)
    manifest_path = args.output_root / "sen_segment_classifier_manifest.csv"
    write_manifest(manifest_path, output_rows)

    print(f"manifest={manifest_path}")
    print(f"base_rows={len(base_rows)}")
    print(f"word_unknown_negative_rows={len(negative_rows)}")
    print(f"rows={len(output_rows)}")
    print("split", dict(Counter(row.get("split", "") for row in output_rows)))
    print("kind", dict(Counter(row.get("kind", "") for row in output_rows)))
    print("label_counts")
    for label, count in Counter(row.get("label", "") for row in output_rows).most_common():
        print(f"{label}\t{count}")
    print("source_unknown_labels", len(candidates_by_label))
    print("stats", dict(stats))


if __name__ == "__main__":
    main()
