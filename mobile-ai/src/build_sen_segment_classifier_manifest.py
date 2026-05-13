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
        description="Crop labeled SEN morpheme segments into a word-classifier manifest.",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--labels", default=",".join(DEFAULT_LABELS))
    parser.add_argument("--val-reals", default="13,14,15,16")
    parser.add_argument("--min-frames", type=int, default=4)
    parser.add_argument(
        "--center-crop",
        type=float,
        default=1.0,
        help="Keep the center fraction of each segment before saving, e.g. 0.7.",
    )
    parser.add_argument(
        "--include-word",
        action="store_true",
        help="Also include cropped WORD segments. Default keeps this as a SEN-only experiment.",
    )
    parser.add_argument(
        "--negative-ratio",
        type=float,
        default=0.0,
        help="Add this many background crops per positive crop, sampled away from target segments.",
    )
    parser.add_argument("--negative-label", default="<background>")
    parser.add_argument("--negative-windows", default="8,12,16,20,24,32,40,52,64")
    parser.add_argument("--negative-max-iou", type=float, default=0.10)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


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
    segments = json.loads(raw)
    return segments if isinstance(segments, list) else []


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


def center_crop_bounds(start: int, end: int, fraction: float) -> tuple[int, int]:
    if fraction >= 1.0:
        return start, end
    if fraction <= 0.0:
        raise ValueError("--center-crop must be > 0")

    length = end - start
    keep = max(2, int(round(length * fraction)))
    keep = min(keep, length)
    offset = max(0, (length - keep) // 2)
    return start + offset, start + offset + keep


def safe_segment_path(output_root: Path, video_name: str, segment_index: int, suffix: str = "") -> Path:
    stem = Path(video_name).stem
    suffix_text = f"_{suffix}" if suffix else ""
    return output_root / "features" / f"{stem}_seg{segment_index:03d}{suffix_text}.npy"


def parse_windows(raw: str) -> list[int]:
    windows = sorted({int(item.strip()) for item in raw.split(",") if item.strip()})
    if not windows or any(window <= 1 for window in windows):
        raise ValueError("--negative-windows must contain integers > 1")
    return windows


def interval_iou(a: tuple[int, int], b: tuple[int, int]) -> float:
    overlap = max(0, min(a[1], b[1]) - max(a[0], b[0]))
    union = max(a[1], b[1]) - min(a[0], b[0])
    return overlap / union if union > 0 else 0.0


def sample_negative_bounds(
    frame_count: int,
    positive_bounds: list[tuple[int, int]],
    windows: list[int],
    count: int,
    max_iou: float,
    rng: Random,
) -> list[tuple[int, int]]:
    if count <= 0:
        return []

    candidates: list[tuple[int, int]] = []
    for window in windows:
        if window > frame_count:
            continue
        stride = max(1, window // 2)
        for start in range(0, frame_count - window + 1, stride):
            end = start + window
            if all(interval_iou((start, end), positive) <= max_iou for positive in positive_bounds):
                candidates.append((start, end))
        if (frame_count - window) % stride:
            start = frame_count - window
            end = frame_count
            if all(interval_iou((start, end), positive) <= max_iou for positive in positive_bounds):
                candidates.append((start, end))

    rng.shuffle(candidates)
    selected: list[tuple[int, int]] = []
    for candidate in candidates:
        if all(interval_iou(candidate, kept) <= max_iou for kept in selected):
            selected.append(candidate)
            if len(selected) >= count:
                break
    return selected


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
        writer.writerows(rows)


def main() -> None:
    args = parse_args()
    allowed = {label.strip() for label in args.labels.split(",") if label.strip()}
    val_reals = {item.strip() for item in args.val_reals.split(",") if item.strip()}
    negative_windows = parse_windows(args.negative_windows)
    rng = Random(args.seed)
    if not allowed:
        raise ValueError("No labels selected.")

    rows = read_rows(args.manifest)
    feature_index = build_feature_index(args.feature_root)
    out_features = args.output_root / "features"
    out_features.mkdir(parents=True, exist_ok=True)

    output_rows: list[dict] = []
    stats = Counter()
    label_counts = Counter()

    for row in rows:
        kind = row.get("kind", "")
        if kind == "WORD" and not args.include_word:
            stats["skipped_word"] += 1
            continue
        if kind not in {"SEN", "WORD"}:
            stats["skipped_kind"] += 1
            continue

        video_name = row.get("video_name", "")
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            stats["missing_features"] += 1
            continue

        features = np.load(feature_path).astype(np.float32, copy=False)
        if features.ndim != 2 or features.shape[0] == 0:
            stats["bad_features"] += 1
            continue

        duration = parse_duration(row, feature_path, int(features.shape[0]))
        segments = load_segments(row)
        positive_bounds: list[tuple[int, int]] = []
        positive_count_for_row = 0
        for segment_index, segment in enumerate(segments):
            segment_labels = [label for label in segment.get("labels", []) if label in allowed]
            if not segment_labels:
                stats["skipped_no_target_label"] += 1
                continue
            if len(segment_labels) > 1:
                stats["skipped_multi_target_label"] += 1
                continue

            label = segment_labels[0]
            start, end = segment_frame_bounds(segment, duration, int(features.shape[0]))
            positive_bounds.append((start, end))
            start, end = center_crop_bounds(start, end, args.center_crop)
            if end - start < args.min_frames:
                stats["skipped_short"] += 1
                continue

            crop = np.nan_to_num(features[start:end]).astype(np.float32, copy=False)
            segment_path = safe_segment_path(args.output_root, video_name, segment_index)
            if args.overwrite or not segment_path.exists():
                np.save(segment_path, crop)

            label_counts[label] += 1
            output_rows.append(
                {
                    "split": "val" if real_id(video_name) in val_reals else "train",
                    "kind": f"{kind}_SEG",
                    "video_name": f"{video_name}:seg{segment_index:03d}:{start}-{end}",
                    "feature_path": str(segment_path),
                    "label": label,
                    "frames": int(crop.shape[0]),
                    "source_video": video_name,
                    "segment_index": segment_index,
                    "segment_start": start,
                    "segment_end": end,
                }
            )
            stats["written"] += 1
            positive_count_for_row += 1

        negative_count = int(round(positive_count_for_row * args.negative_ratio))
        for negative_index, (start, end) in enumerate(
            sample_negative_bounds(
                frame_count=int(features.shape[0]),
                positive_bounds=positive_bounds,
                windows=negative_windows,
                count=negative_count,
                max_iou=args.negative_max_iou,
                rng=rng,
            )
        ):
            if end - start < args.min_frames:
                continue
            crop = np.nan_to_num(features[start:end]).astype(np.float32, copy=False)
            segment_path = safe_segment_path(args.output_root, video_name, negative_index, "neg")
            if args.overwrite or not segment_path.exists():
                np.save(segment_path, crop)

            label_counts[args.negative_label] += 1
            output_rows.append(
                {
                    "split": "val" if real_id(video_name) in val_reals else "train",
                    "kind": f"{kind}_NEG",
                    "video_name": f"{video_name}:neg{negative_index:03d}:{start}-{end}",
                    "feature_path": str(segment_path),
                    "label": args.negative_label,
                    "frames": int(crop.shape[0]),
                    "source_video": video_name,
                    "segment_index": f"neg{negative_index}",
                    "segment_start": start,
                    "segment_end": end,
                }
            )
            stats["written_negative"] += 1

    manifest_path = args.output_root / "sen_segment_classifier_manifest.csv"
    write_manifest(manifest_path, output_rows)

    print(f"manifest={manifest_path}")
    print(f"rows={len(output_rows)}")
    print("split", dict(Counter(row["split"] for row in output_rows)))
    print("kind", dict(Counter(row["kind"] for row in output_rows)))
    print("labels", len(label_counts))
    print("stats", dict(stats))
    print()
    print("label_counts")
    for label, count in label_counts.most_common():
        print(f"{label}\t{count}")


if __name__ == "__main__":
    main()
