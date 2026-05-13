from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter, defaultdict
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
            "Build a scan-matched SEN word spotting manifest. Windows are labeled "
            "positive when they overlap a true SEN segment enough, background when "
            "they avoid all true segments, and skipped otherwise."
        ),
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--labels", default=",".join(DEFAULT_LABELS))
    parser.add_argument("--val-reals", default="13,14,15,16")
    parser.add_argument("--window-frames", default="8,12,16,20,24,32,40,52,64")
    parser.add_argument("--stride", type=int, default=4)
    parser.add_argument("--min-frames", type=int, default=4)
    parser.add_argument("--positive-min-coverage", type=float, default=0.60)
    parser.add_argument("--positive-min-purity", type=float, default=0.25)
    parser.add_argument("--ambiguous-min-coverage", type=float, default=0.30)
    parser.add_argument("--max-positive-per-segment", type=int, default=8)
    parser.add_argument("--negative-max-coverage", type=float, default=0.10)
    parser.add_argument("--negative-max-iou", type=float, default=0.05)
    parser.add_argument("--negative-ratio", type=float, default=3.0)
    parser.add_argument("--max-negatives-per-video", type=int, default=64)
    parser.add_argument("--negative-label", default="<background>")
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
        writer.writerows(rows)


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


def real_id(video_name: str) -> str:
    match = re.search(r"_REAL(\d+)_", video_name)
    return match.group(1) if match else ""


def parse_windows(raw: str) -> list[int]:
    windows = sorted({int(item.strip()) for item in raw.split(",") if item.strip()})
    if not windows or any(window <= 1 for window in windows):
        raise ValueError("--window-frames must contain integers > 1")
    return windows


def load_segments(row: dict) -> list[dict]:
    raw = row.get("segments_json") or "[]"
    try:
        segments = json.loads(raw)
    except json.JSONDecodeError:
        return []
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


def interval_overlap(a: tuple[int, int], b: tuple[int, int]) -> int:
    return max(0, min(a[1], b[1]) - max(a[0], b[0]))


def interval_iou(a: tuple[int, int], b: tuple[int, int]) -> float:
    overlap = interval_overlap(a, b)
    union = max(a[1], b[1]) - min(a[0], b[0])
    return overlap / union if union > 0 else 0.0


def iter_windows(frame_count: int, windows: list[int], stride: int) -> list[tuple[int, int]]:
    output: list[tuple[int, int]] = []
    for window in windows:
        if window > frame_count:
            continue
        starts = list(range(0, frame_count - window + 1, stride))
        if starts and starts[-1] != frame_count - window:
            starts.append(frame_count - window)
        elif not starts:
            starts = [0]
        output.extend((start, start + window) for start in starts)
    return output


def window_path(output_root: Path, video_name: str, index: int, suffix: str) -> Path:
    stem = Path(video_name).stem
    return output_root / "features" / f"{stem}_win{index:05d}_{suffix}.npy"


def target_bounds(
    row: dict,
    allowed: set[str],
    duration: float,
    frame_count: int,
) -> list[tuple[int, int, str, int]]:
    bounds: list[tuple[int, int, str, int]] = []
    for segment_index, segment in enumerate(load_segments(row)):
        labels = [label for label in segment.get("labels", []) if label in allowed]
        if len(labels) != 1:
            continue
        start, end = segment_frame_bounds(segment, duration, frame_count)
        if end > start:
            bounds.append((start, end, labels[0], segment_index))
    return bounds


def save_crop(path: Path, features: np.ndarray, start: int, end: int, overwrite: bool) -> None:
    if path.exists() and not overwrite:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    crop = np.nan_to_num(features[start:end]).astype(np.float32, copy=False)
    np.save(path, crop)


def main() -> None:
    args = parse_args()
    allowed = {label.strip() for label in args.labels.split(",") if label.strip()}
    val_reals = {item.strip() for item in args.val_reals.split(",") if item.strip()}
    windows = parse_windows(args.window_frames)
    rng = Random(args.seed)

    rows = read_rows(args.manifest)
    feature_index = build_feature_index(args.feature_root)
    output_rows: list[dict] = []
    stats: Counter[str] = Counter()
    label_counts: Counter[str] = Counter()

    for row in rows:
        if row.get("kind") != "SEN":
            stats["skipped_non_sen"] += 1
            continue

        video_name = row.get("video_name", "")
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            stats["missing_features"] += 1
            continue

        features = np.load(feature_path).astype(np.float32, copy=False)
        if features.ndim != 2 or features.shape[0] < args.min_frames:
            stats["bad_features"] += 1
            continue

        frame_count = int(features.shape[0])
        duration = parse_duration(row, feature_path, frame_count)
        bounds = target_bounds(row, allowed, duration, frame_count)
        if not bounds:
            stats["no_target_segments"] += 1
            continue

        positives_by_segment: dict[int, list[tuple[float, int, int, str]]] = defaultdict(list)
        negative_candidates: list[tuple[int, int]] = []

        for start, end in iter_windows(frame_count, windows, args.stride):
            if end - start < args.min_frames:
                continue
            window = (start, end)
            scored: list[tuple[float, float, float, str, int]] = []
            for seg_start, seg_end, label, segment_index in bounds:
                overlap = interval_overlap(window, (seg_start, seg_end))
                if overlap <= 0:
                    continue
                segment_length = max(seg_end - seg_start, 1)
                window_length = max(end - start, 1)
                coverage = overlap / segment_length
                purity = overlap / window_length
                iou = interval_iou(window, (seg_start, seg_end))
                scored.append((coverage, purity, iou, label, segment_index))

            if not scored:
                negative_candidates.append(window)
                continue

            scored.sort(key=lambda item: (item[0], item[1], item[2]), reverse=True)
            best_coverage, best_purity, best_iou, best_label, best_segment_index = scored[0]
            other_coverages = [
                coverage
                for coverage, _, _, _, segment_index in scored[1:]
                if segment_index != best_segment_index
            ]

            if (
                best_coverage >= args.positive_min_coverage
                and best_purity >= args.positive_min_purity
                and all(coverage < args.ambiguous_min_coverage for coverage in other_coverages)
            ):
                quality = best_coverage + best_purity + best_iou
                positives_by_segment[best_segment_index].append((quality, start, end, best_label))
                continue

            if (
                best_coverage <= args.negative_max_coverage
                and best_iou <= args.negative_max_iou
                and all(coverage <= args.negative_max_coverage for coverage, *_ in scored)
            ):
                negative_candidates.append(window)
            else:
                stats["skipped_ambiguous"] += 1

        selected_positives: list[tuple[int, int, str, int]] = []
        for segment_index, candidates in positives_by_segment.items():
            candidates.sort(key=lambda item: item[0], reverse=True)
            for _, start, end, label in candidates[: args.max_positive_per_segment]:
                selected_positives.append((start, end, label, segment_index))

        rng.shuffle(negative_candidates)
        negative_count = min(
            len(negative_candidates),
            int(round(len(selected_positives) * args.negative_ratio)),
            args.max_negatives_per_video,
        )
        selected_negatives = negative_candidates[:negative_count]
        split = "val" if real_id(video_name) in val_reals else "train"

        for local_index, (start, end, label, segment_index) in enumerate(selected_positives):
            path = window_path(args.output_root, video_name, local_index, "pos")
            save_crop(path, features, start, end, args.overwrite)
            output_rows.append(
                {
                    "split": split,
                    "kind": "SEN_WIN",
                    "video_name": f"{video_name}:win{local_index:05d}:{start}-{end}",
                    "feature_path": str(path),
                    "label": label,
                    "frames": end - start,
                    "source_video": video_name,
                    "segment_index": segment_index,
                    "segment_start": start,
                    "segment_end": end,
                }
            )
            label_counts[label] += 1
            stats["written_positive"] += 1

        for local_index, (start, end) in enumerate(selected_negatives):
            path = window_path(args.output_root, video_name, local_index, "neg")
            save_crop(path, features, start, end, args.overwrite)
            output_rows.append(
                {
                    "split": split,
                    "kind": "SEN_WIN_NEG",
                    "video_name": f"{video_name}:neg{local_index:05d}:{start}-{end}",
                    "feature_path": str(path),
                    "label": args.negative_label,
                    "frames": end - start,
                    "source_video": video_name,
                    "segment_index": f"neg{local_index}",
                    "segment_start": start,
                    "segment_end": end,
                }
            )
            label_counts[args.negative_label] += 1
            stats["written_negative"] += 1

        stats["scanned_videos"] += 1

    manifest_path = args.output_root / "sen_window_spotter_manifest.csv"
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
