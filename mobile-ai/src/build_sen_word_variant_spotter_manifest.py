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
    "가능",
    "가다",
    "공항",
    "급하다",
    "기차",
    "맞다",
    "명동",
    "방법",
    "병원",
    "불가능",
    "불량",
    "빨리",
    "사진기",
    "샛길",
    "서울역",
    "알려주다",
    "얼마",
    "없다",
    "에어컨",
    "원하다",
    "지름길",
]


FIELDNAMES = [
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build a domain-variant word spotter manifest. Existing SEN window "
            "positives become label__SEN, matching isolated WORD clips become "
            "label__WORD, and both are decoded back to label later."
        ),
    )
    parser.add_argument("--sen-window-manifest", type=Path, required=True)
    parser.add_argument("--word-manifest", type=Path, required=True)
    parser.add_argument("--word-feature-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--labels", default=",".join(DEFAULT_LABELS))
    parser.add_argument("--negative-label", default="<background>")
    parser.add_argument("--sen-suffix", default="__SEN")
    parser.add_argument("--word-suffix", default="__WORD")
    parser.add_argument("--word-split", default="train")
    parser.add_argument("--val-reals", default="13,14,15,16")
    parser.add_argument("--max-word-per-label", type=int, default=12)
    parser.add_argument(
        "--max-word-per-label-overrides",
        default="",
        help="Comma-separated per-label WORD limits, e.g. '기차=120,지름길=120'.",
    )
    parser.add_argument(
        "--word-window-frames",
        default="",
        help=(
            "Optional comma-separated WORD sliding windows. When omitted, one "
            "annotation crop is used per WORD clip. When set, multiple windows "
            "that overlap the WORD annotation enough are used."
        ),
    )
    parser.add_argument("--word-window-stride", type=int, default=4)
    parser.add_argument("--word-positive-min-coverage", type=float, default=0.60)
    parser.add_argument("--word-positive-min-purity", type=float, default=0.25)
    parser.add_argument("--max-word-windows-per-clip", type=int, default=1)
    parser.add_argument("--min-frames", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_manifest(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in FIELDNAMES})


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


def parse_windows(raw: str) -> list[int]:
    windows = sorted({int(item.strip()) for item in raw.split(",") if item.strip()})
    if any(window <= 1 for window in windows):
        raise ValueError("--word-window-frames must contain integers > 1")
    return windows


def parse_label_limit_overrides(raw: str) -> dict[str, int]:
    overrides: dict[str, int] = {}
    for item in raw.split(","):
        item = item.strip()
        if not item:
            continue
        if "=" not in item:
            raise ValueError("--max-word-per-label-overrides entries must be label=count")
        label, count = item.split("=", maxsplit=1)
        label = label.strip()
        if not label:
            raise ValueError("--max-word-per-label-overrides contains an empty label")
        limit = int(count.strip())
        if limit < 0:
            raise ValueError("--max-word-per-label-overrides counts must be >= 0")
        overrides[label] = limit
    return overrides


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


def word_crop_bounds(row: dict, feature_path: Path, frame_count: int) -> tuple[int, int]:
    duration = parse_duration(row, feature_path, frame_count)
    segments = load_segments(row)
    if segments:
        start, end = segment_frame_bounds(segments[0], duration, frame_count)
        if end > start:
            return start, end
    return 0, frame_count


def output_path(output_root: Path, video_name: str, index: int, start: int, end: int) -> Path:
    stem = Path(video_name).stem
    return output_root / "features" / f"{stem}_word_variant{index:05d}_{start}_{end}.npy"


def word_positive_windows(
    frame_count: int,
    target: tuple[int, int],
    windows: list[int],
    stride: int,
    min_coverage: float,
    min_purity: float,
    max_windows: int,
) -> list[tuple[int, int]]:
    if not windows:
        return [target]

    target_length = max(target[1] - target[0], 1)
    candidates: list[tuple[float, int, int]] = []
    for start, end in iter_windows(frame_count, windows, stride):
        window_length = max(end - start, 1)
        overlap = interval_overlap((start, end), target)
        if overlap <= 0:
            continue
        coverage = overlap / target_length
        purity = overlap / window_length
        if coverage >= min_coverage and purity >= min_purity:
            quality = coverage + purity + interval_iou((start, end), target)
            candidates.append((quality, start, end))

    candidates.sort(key=lambda item: item[0], reverse=True)
    return [(start, end) for _, start, end in candidates[:max_windows]]


def main() -> None:
    args = parse_args()
    allowed = {label.strip() for label in args.labels.split(",") if label.strip()}
    val_reals = {item.strip() for item in args.val_reals.split(",") if item.strip()}
    rng = Random(args.seed)
    word_windows = parse_windows(args.word_window_frames)
    word_limit_overrides = parse_label_limit_overrides(args.max_word_per_label_overrides)

    output_rows: list[dict] = []
    stats: Counter[str] = Counter()

    for row in read_rows(args.sen_window_manifest):
        label = row.get("label", "")
        if label == args.negative_label:
            output_rows.append(dict(row))
            stats["sen_negative_rows"] += 1
        elif label in allowed:
            output = dict(row)
            output["kind"] = "SEN_WIN_DOMAIN"
            output["label"] = f"{label}{args.sen_suffix}"
            output_rows.append(output)
            stats["sen_positive_rows"] += 1
        else:
            stats["skipped_sen_label"] += 1

    word_rows = read_rows(args.word_manifest)
    feature_index = build_feature_index(args.word_feature_root)
    candidates_by_label: dict[str, list[dict]] = defaultdict(list)
    for row in word_rows:
        if row.get("kind") != "WORD":
            stats["skipped_non_word"] += 1
            continue
        labels = [label for label in row_labels(row) if label in allowed]
        if len(labels) != 1:
            stats["skipped_word_label"] += 1
            continue
        video_name = row.get("video_name", "")
        if Path(video_name).stem not in feature_index:
            stats["missing_word_feature"] += 1
            continue
        candidates_by_label[labels[0]].append(row)

    selected_word_rows: list[tuple[str, dict]] = []
    for label, rows in sorted(candidates_by_label.items()):
        rng.shuffle(rows)
        limit = word_limit_overrides.get(label, args.max_word_per_label)
        selected_word_rows.extend((label, row) for row in rows[:limit])
    rng.shuffle(selected_word_rows)

    output_index = 0
    for _, (label, row) in enumerate(selected_word_rows):
        video_name = row.get("video_name", "")
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            continue
        features = np.load(feature_path).astype(np.float32, copy=False)
        if features.ndim != 2 or features.shape[0] == 0:
            stats["bad_word_feature"] += 1
            continue
        start, end = word_crop_bounds(row, feature_path, int(features.shape[0]))
        if end - start < args.min_frames:
            stats["short_word_crop"] += 1
            continue

        selected_windows = word_positive_windows(
            frame_count=int(features.shape[0]),
            target=(start, end),
            windows=word_windows,
            stride=args.word_window_stride,
            min_coverage=args.word_positive_min_coverage,
            min_purity=args.word_positive_min_purity,
            max_windows=args.max_word_windows_per_clip,
        )
        if not selected_windows:
            selected_windows = [(start, end)]
            stats["word_window_fallback"] += 1

        split = args.word_split or ("val" if real_id(video_name) in val_reals else "train")
        for window_start, window_end in selected_windows:
            if window_end - window_start < args.min_frames:
                continue
            crop = np.nan_to_num(features[window_start:window_end]).astype(np.float32, copy=False)
            out_path = output_path(args.output_root, video_name, output_index, window_start, window_end)
            output_index += 1
            out_path.parent.mkdir(parents=True, exist_ok=True)
            if args.overwrite or not out_path.exists():
                np.save(out_path, crop)

            output_rows.append(
                {
                    "split": split,
                    "kind": "WORD_DOMAIN",
                    "video_name": f"{video_name}:word_domain:{window_start}-{window_end}",
                    "feature_path": str(out_path),
                    "label": f"{label}{args.word_suffix}",
                    "frames": int(crop.shape[0]),
                    "source_video": video_name,
                    "segment_index": "word_domain",
                    "segment_start": window_start,
                    "segment_end": window_end,
                }
            )
            stats["word_positive_rows"] += 1

    manifest_path = args.output_root / "sen_word_variant_spotter_manifest.csv"
    write_manifest(manifest_path, output_rows)

    print(f"manifest={manifest_path}")
    print(f"rows={len(output_rows)}")
    print("split", dict(Counter(row.get("split", "") for row in output_rows)))
    print("kind", dict(Counter(row.get("kind", "") for row in output_rows)))
    print("labels", len(Counter(row.get("label", "") for row in output_rows)))
    print("stats", dict(stats))
    print()
    print("label_counts")
    for label, count in Counter(row.get("label", "") for row in output_rows).most_common():
        print(f"{label}\t{count}")


if __name__ == "__main__":
    main()
