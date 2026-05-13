from __future__ import annotations

import argparse
import csv
import json
import math
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import numpy as np


@dataclass(frozen=True)
class SegmentSample:
    kind: str
    label: str
    video_name: str
    start: int
    end: int
    features: np.ndarray


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare isolated WORD segments with the same labels inside SEN segments.",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--label", required=True, help="Target label to compare, e.g. 가다.")
    parser.add_argument(
        "--compare-labels",
        default="",
        help="Optional comma-separated WORD labels for nearest-label retrieval. Defaults to manifest labels.",
    )
    parser.add_argument("--max-word-per-label", type=int, default=12)
    parser.add_argument("--max-sen", type=int, default=80)
    parser.add_argument("--resample-frames", type=int, default=40)
    parser.add_argument(
        "--center-crop",
        type=float,
        default=1.0,
        help="Keep the center fraction of every segment before resampling, e.g. 0.5 removes boundary motion.",
    )
    parser.add_argument("--normalize", choices=["none", "global-zscore"], default="global-zscore")
    parser.add_argument("--top-examples", type=int, default=20)
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


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
    start = max(0, min(frame_count, int(math.floor(start_sec / duration * frame_count))))
    end = max(start, min(frame_count, int(math.ceil(end_sec / duration * frame_count))))
    return start, end


def fit_global_normalizer(rows: list[dict], feature_index: dict[str, Path]) -> tuple[np.ndarray | None, np.ndarray | None]:
    total = 0
    sum_x: np.ndarray | None = None
    sum_x2: np.ndarray | None = None
    for row in rows:
        path = feature_index.get(Path(row.get("video_name", "")).stem)
        if not path:
            continue
        x = np.nan_to_num(np.load(path).astype(np.float32, copy=False))
        if x.ndim != 2 or x.shape[0] == 0:
            continue
        if sum_x is None:
            sum_x = np.zeros(x.shape[1], dtype=np.float64)
            sum_x2 = np.zeros(x.shape[1], dtype=np.float64)
        sum_x += x.sum(axis=0)
        sum_x2 += np.square(x, dtype=np.float64).sum(axis=0)
        total += x.shape[0]

    if total == 0 or sum_x is None or sum_x2 is None:
        return None, None
    mean = sum_x / total
    variance = np.maximum(sum_x2 / total - mean * mean, 1.0e-8)
    std = np.sqrt(variance)
    std = np.where(std < 1.0e-6, 1.0, std)
    return mean.astype(np.float32), std.astype(np.float32)


def normalize(x: np.ndarray, mode: str, mean: np.ndarray | None, std: np.ndarray | None) -> np.ndarray:
    x = np.nan_to_num(x.astype(np.float32, copy=False))
    if mode == "global-zscore" and mean is not None and std is not None:
        return ((x - mean) / std).astype(np.float32, copy=False)
    return x


def resample_features(features: np.ndarray, target_len: int) -> np.ndarray:
    if features.shape[0] == target_len:
        return features.astype(np.float32, copy=False)
    if features.shape[0] <= 1:
        return np.repeat(features.astype(np.float32, copy=False), target_len, axis=0)

    old_positions = np.arange(features.shape[0], dtype=np.float32)
    new_positions = np.linspace(0, features.shape[0] - 1, target_len, dtype=np.float32)
    out = np.empty((target_len, features.shape[1]), dtype=np.float32)
    for dim in range(features.shape[1]):
        out[:, dim] = np.interp(new_positions, old_positions, features[:, dim])
    return out


def center_crop_features(features: np.ndarray, fraction: float) -> np.ndarray:
    if fraction >= 1.0 or features.shape[0] <= 2:
        return features
    if fraction <= 0.0:
        raise ValueError("--center-crop must be > 0")

    keep = max(2, int(round(features.shape[0] * fraction)))
    keep = min(keep, features.shape[0])
    start = max(0, (features.shape[0] - keep) // 2)
    end = start + keep
    return features[start:end]


def distance(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.linalg.norm(a - b, axis=1).mean())


def collect_segments(
    rows: list[dict],
    feature_index: dict[str, Path],
    labels: set[str],
    normalize_mode: str,
    mean: np.ndarray | None,
    std: np.ndarray | None,
    resample_frames: int,
    center_crop: float,
) -> list[SegmentSample]:
    samples: list[SegmentSample] = []
    for row in rows:
        kind = row.get("kind", "")
        if kind not in {"WORD", "SEN"}:
            continue
        feature_path = feature_index.get(Path(row.get("video_name", "")).stem)
        if not feature_path:
            continue
        features = np.load(feature_path).astype(np.float32)
        duration = parse_duration(row, feature_path, int(features.shape[0]))
        for segment in load_segments(row):
            segment_labels = [label for label in segment.get("labels", []) if label in labels]
            if not segment_labels:
                continue
            label = segment_labels[0]
            start, end = segment_frame_bounds(segment, duration, int(features.shape[0]))
            if end <= start + 1:
                continue
            crop = normalize(features[start:end], normalize_mode, mean, std)
            crop = center_crop_features(crop, center_crop)
            crop = resample_features(crop, resample_frames)
            samples.append(SegmentSample(kind, label, row.get("video_name", ""), start, end, crop))
    return samples


def summarize(values: list[float], name: str) -> None:
    if not values:
        print(f"{name}\tcount=0")
        return
    arr = np.asarray(values, dtype=np.float32)
    print(
        f"{name}\tcount={len(values)}\tmean={arr.mean():.4f}\tmedian={np.median(arr):.4f}\t"
        f"p90={np.percentile(arr, 90):.4f}\tmin={arr.min():.4f}\tmax={arr.max():.4f}"
    )


def main() -> None:
    args = parse_args()
    rows = read_rows(args.manifest)
    feature_index = build_feature_index(args.feature_root)
    mean, std = fit_global_normalizer(rows, feature_index) if args.normalize == "global-zscore" else (None, None)

    if args.compare_labels:
        compare_labels = {label.strip() for label in args.compare_labels.split(",") if label.strip()}
    else:
        compare_labels = {
            label
            for row in rows
            for label in (row.get("contains_target") or "").split()
        }
    compare_labels.add(args.label)

    samples = collect_segments(
        rows=rows,
        feature_index=feature_index,
        labels=compare_labels,
        normalize_mode=args.normalize,
        mean=mean,
        std=std,
        resample_frames=args.resample_frames,
        center_crop=args.center_crop,
    )

    word_by_label: dict[str, list[SegmentSample]] = defaultdict(list)
    sen_target: list[SegmentSample] = []
    word_target: list[SegmentSample] = []
    for sample in samples:
        if sample.kind == "WORD":
            word_by_label[sample.label].append(sample)
            if sample.label == args.label:
                word_target.append(sample)
        elif sample.kind == "SEN" and sample.label == args.label:
            sen_target.append(sample)

    for label in list(word_by_label):
        word_by_label[label] = word_by_label[label][: args.max_word_per_label]
    word_target = word_by_label.get(args.label, [])
    sen_target = sen_target[: args.max_sen]

    print(f"label={args.label}")
    print(f"word_target_segments={len(word_target)}")
    print(f"sen_target_segments={len(sen_target)}")
    print(f"word_labels_for_retrieval={len(word_by_label)}")
    print(f"center_crop={args.center_crop}")

    word_self: list[float] = []
    for i, sample in enumerate(word_target):
        others = [other for j, other in enumerate(word_target) if i != j]
        if not others:
            continue
        word_self.append(min(distance(sample.features, other.features) for other in others))

    sen_to_word: list[float] = []
    retrieval_correct = 0
    retrieval_rows: list[tuple[float, str, SegmentSample, str]] = []
    for sample in sen_target:
        same_label_dist = min(distance(sample.features, proto.features) for proto in word_target)
        sen_to_word.append(same_label_dist)

        best_label = ""
        best_distance = float("inf")
        best_video = ""
        for label, prototypes in word_by_label.items():
            for prototype in prototypes:
                d = distance(sample.features, prototype.features)
                if d < best_distance:
                    best_distance = d
                    best_label = label
                    best_video = prototype.video_name
        retrieval_correct += int(best_label == args.label)
        retrieval_rows.append((best_distance, best_label, sample, best_video))

    print()
    summarize(word_self, "WORD_to_WORD_nearest_same_label")
    summarize(sen_to_word, "SEN_to_WORD_nearest_same_label")
    print(f"SEN_segment_nearest_WORD_label_acc={retrieval_correct / max(len(sen_target), 1):.4f}")

    print()
    print("nearest_label_counts")
    print(dict(Counter(best_label for _, best_label, _, _ in retrieval_rows).most_common(20)))

    print()
    print("closest_sen_examples")
    for best_distance, best_label, sample, best_video in sorted(retrieval_rows, key=lambda item: item[0])[: args.top_examples]:
        print(
            f"distance={best_distance:.4f}\tnearest={best_label}\t"
            f"sen={sample.video_name}:{sample.start}-{sample.end}\tword={best_video}"
        )

    print()
    print("farthest_sen_examples")
    for best_distance, best_label, sample, best_video in sorted(retrieval_rows, key=lambda item: item[0], reverse=True)[
        : args.top_examples
    ]:
        print(
            f"distance={best_distance:.4f}\tnearest={best_label}\t"
            f"sen={sample.video_name}:{sample.start}-{sample.end}\tword={best_video}"
        )


if __name__ == "__main__":
    main()
