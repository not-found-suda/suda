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
class Prototype:
    label: str
    video_name: str
    features: np.ndarray
    frames: int


@dataclass(frozen=True)
class Candidate:
    label: str
    start: int
    end: int
    distance: float
    score: float
    prototype: str


@dataclass(frozen=True)
class Normalizer:
    mean: np.ndarray | None = None
    std: np.ndarray | None = None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Spot WORD prototypes inside SEN feature streams with template/DTW matching. "
            "Use --oracle-count first to measure whether isolated WORD examples are enough "
            "to recover sentence words without learning full sentence patterns."
        ),
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--labels", required=True, help="Comma-separated target labels.")
    parser.add_argument("--split", default="val", help="SEN split to evaluate. Empty means all splits.")
    parser.add_argument("--prototype-split", default="", help="Optional WORD split filter.")
    parser.add_argument("--max-files", type=int, default=0)
    parser.add_argument("--max-word-prototypes", type=int, default=8)
    parser.add_argument("--window-scales", default="0.7,0.85,1.0,1.15,1.3")
    parser.add_argument("--stride", type=int, default=6)
    parser.add_argument("--distance", choices=["resample", "dtw"], default="resample")
    parser.add_argument("--match-frames", type=int, default=40)
    parser.add_argument("--max-dtw-len", type=int, default=48)
    parser.add_argument(
        "--normalize",
        choices=["none", "zscore", "global-zscore"],
        default="global-zscore",
        help="zscore is per-window; global-zscore uses one dataset-wide mean/std and is usually safer.",
    )
    parser.add_argument("--candidate-limit-per-label", type=int, default=20)
    parser.add_argument("--nms-overlap", type=float, default=0.55)
    parser.add_argument("--threshold", type=float, default=0.0, help="Keep candidates at or below this distance.")
    parser.add_argument("--oracle-count", action="store_true", help="Keep exactly len(reference) best NMS candidates.")
    parser.add_argument(
        "--force-reference-labels",
        action="store_true",
        help=(
            "Diagnostic mode: choose the best non-overlapping candidate for each reference label. "
            "If this fails, isolated WORD prototypes are not matching the same words inside sentences."
        ),
    )
    parser.add_argument("--report-labels", action="store_true")
    parser.add_argument("--top-errors", type=int, default=20)
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
    if not isinstance(segments, list):
        return []
    return segments


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


def row_tokens(row: dict, allowed: set[str]) -> list[str]:
    explicit = (row.get("contains_target") or "").split()
    if explicit:
        return [token for token in explicit if token in allowed]

    tokens: list[str] = []
    for segment in sorted(load_segments(row), key=lambda item: float(item.get("start", 0.0))):
        for label in segment.get("labels", []):
            if label in allowed:
                tokens.append(label)
                break
    return tokens


def row_token_spans(row: dict, allowed: set[str], feature_path: Path, frame_count: int) -> list[tuple[str, int, int]]:
    duration = parse_duration(row, feature_path, frame_count)
    spans: list[tuple[str, int, int]] = []
    for segment in sorted(load_segments(row), key=lambda item: float(item.get("start", 0.0))):
        for label in segment.get("labels", []):
            if label in allowed:
                start, end = segment_frame_bounds(segment, duration, frame_count)
                spans.append((label, start, end))
                break
    return spans


def fit_global_normalizer(rows: list[dict], feature_index: dict[str, Path]) -> Normalizer:
    total = 0
    sum_x: np.ndarray | None = None
    sum_x2: np.ndarray | None = None

    for row in rows:
        feature_path = feature_index.get(Path(row.get("video_name", "")).stem)
        if not feature_path:
            continue
        x = np.nan_to_num(np.load(feature_path).astype(np.float32, copy=False))
        if x.ndim != 2 or x.shape[0] == 0:
            continue
        if sum_x is None:
            sum_x = np.zeros(x.shape[1], dtype=np.float64)
            sum_x2 = np.zeros(x.shape[1], dtype=np.float64)
        sum_x += x.sum(axis=0)
        sum_x2 += np.square(x, dtype=np.float64).sum(axis=0)
        total += x.shape[0]

    if total == 0 or sum_x is None or sum_x2 is None:
        return Normalizer()

    mean = sum_x / total
    variance = np.maximum(sum_x2 / total - mean * mean, 1.0e-8)
    std = np.sqrt(variance)
    std = np.where(std < 1.0e-6, 1.0, std)
    return Normalizer(mean=mean.astype(np.float32), std=std.astype(np.float32))


def normalize_features(features: np.ndarray, mode: str, normalizer: Normalizer | None = None) -> np.ndarray:
    x = np.nan_to_num(features.astype(np.float32, copy=False))
    if mode == "none":
        return x
    if mode == "global-zscore":
        if normalizer is None or normalizer.mean is None or normalizer.std is None:
            return x
        return ((x - normalizer.mean) / normalizer.std).astype(np.float32, copy=False)

    mean = x.mean(axis=0, keepdims=True)
    std = x.std(axis=0, keepdims=True)
    std = np.where(std < 1.0e-6, 1.0, std)
    return ((x - mean) / std).astype(np.float32, copy=False)


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


def resample_distance(a: np.ndarray, b: np.ndarray, match_frames: int) -> float:
    aa = resample_features(a, match_frames)
    bb = resample_features(b, match_frames)
    return float(np.linalg.norm(aa - bb, axis=1).mean())


def dtw_distance(a: np.ndarray, b: np.ndarray, max_len: int) -> float:
    aa = resample_features(a, min(max_len, max(2, a.shape[0])))
    bb = resample_features(b, min(max_len, max(2, b.shape[0])))
    cost = np.linalg.norm(aa[:, None, :] - bb[None, :, :], axis=2)

    previous = np.full(bb.shape[0] + 1, np.inf, dtype=np.float32)
    previous[0] = 0.0
    for i in range(1, aa.shape[0] + 1):
        current = np.full(bb.shape[0] + 1, np.inf, dtype=np.float32)
        for j in range(1, bb.shape[0] + 1):
            current[j] = cost[i - 1, j - 1] + min(previous[j], current[j - 1], previous[j - 1])
        previous = current

    return float(previous[-1] / max(aa.shape[0], bb.shape[0], 1))


def edit_distance(reference: list[str], hypothesis: list[str]) -> int:
    previous = list(range(len(hypothesis) + 1))
    for i, ref_token in enumerate(reference, start=1):
        current = [i]
        for j, hyp_token in enumerate(hypothesis, start=1):
            cost = 0 if ref_token == hyp_token else 1
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost))
        previous = current
    return previous[-1]


def align_tokens(reference: list[str], hypothesis: list[str]) -> list[tuple[str, str, str]]:
    rows = len(reference) + 1
    cols = len(hypothesis) + 1
    dp = [[0] * cols for _ in range(rows)]
    back: list[list[str]] = [[""] * cols for _ in range(rows)]

    for i in range(1, rows):
        dp[i][0] = i
        back[i][0] = "delete"
    for j in range(1, cols):
        dp[0][j] = j
        back[0][j] = "insert"

    for i in range(1, rows):
        for j in range(1, cols):
            if reference[i - 1] == hypothesis[j - 1]:
                best = (dp[i - 1][j - 1], "match")
            else:
                best = (dp[i - 1][j - 1] + 1, "substitute")
            best = min(best, (dp[i - 1][j] + 1, "delete"), (dp[i][j - 1] + 1, "insert"), key=lambda x: x[0])
            dp[i][j] = best[0]
            back[i][j] = best[1]

    ops: list[tuple[str, str, str]] = []
    i = len(reference)
    j = len(hypothesis)
    while i > 0 or j > 0:
        op = back[i][j]
        if op == "match":
            ops.append((op, reference[i - 1], hypothesis[j - 1]))
            i -= 1
            j -= 1
        elif op == "substitute":
            ops.append((op, reference[i - 1], hypothesis[j - 1]))
            i -= 1
            j -= 1
        elif op == "delete":
            ops.append((op, reference[i - 1], "<empty>"))
            i -= 1
        elif op == "insert":
            ops.append((op, "<extra>", hypothesis[j - 1]))
            j -= 1
        else:
            raise RuntimeError(f"Invalid alignment state at i={i}, j={j}")
    return list(reversed(ops))


def interval_overlap(a: Candidate, b: Candidate) -> float:
    overlap = max(0, min(a.end, b.end) - max(a.start, b.start))
    denom = max(1, min(a.end - a.start, b.end - b.start))
    return overlap / denom


def span_overlap(candidate: Candidate, span: tuple[str, int, int]) -> float:
    _, start, end = span
    overlap = max(0, min(candidate.end, end) - max(candidate.start, start))
    denom = max(1, min(candidate.end - candidate.start, end - start))
    return overlap / denom


def non_max_suppression(candidates: list[Candidate], max_overlap: float) -> list[Candidate]:
    selected: list[Candidate] = []
    for candidate in sorted(candidates, key=lambda item: item.score):
        if all(interval_overlap(candidate, kept) <= max_overlap for kept in selected):
            selected.append(candidate)
    return selected


def choose_reference_labeled_candidates(
    candidates: list[Candidate],
    reference_words: list[str],
    max_overlap: float,
) -> list[Candidate]:
    selected: list[Candidate] = []
    cursor = 0
    for label in reference_words:
        matching = [
            candidate
            for candidate in candidates
            if candidate.label == label
            and candidate.end > cursor
            and all(interval_overlap(candidate, kept) <= max_overlap for kept in selected)
        ]
        if not matching:
            continue

        best = min(matching, key=lambda item: (item.score, max(0, cursor - item.start)))
        selected.append(best)
        cursor = max(cursor, best.end)

    return selected


def calibrate_label_candidates(candidates: list[Candidate]) -> list[Candidate]:
    if not candidates:
        return []

    distances = np.asarray([candidate.distance for candidate in candidates], dtype=np.float32)
    center = float(np.median(distances))
    spread = float(np.median(np.abs(distances - center)))
    if spread < 1.0e-6:
        spread = float(np.std(distances))
    if spread < 1.0e-6:
        spread = 1.0

    return [
        Candidate(
            label=candidate.label,
            start=candidate.start,
            end=candidate.end,
            distance=candidate.distance,
            score=(candidate.distance - center) / spread,
            prototype=candidate.prototype,
        )
        for candidate in candidates
    ]


def build_prototypes(
    rows: list[dict],
    feature_index: dict[str, Path],
    labels: list[str],
    args: argparse.Namespace,
    normalizer: Normalizer,
) -> dict[str, list[Prototype]]:
    allowed = set(labels)
    grouped: dict[str, list[Prototype]] = defaultdict(list)

    for row in rows:
        if row.get("kind") != "WORD":
            continue
        if args.prototype_split and row.get("split") != args.prototype_split:
            continue

        tokens = row_tokens(row, allowed)
        if not tokens:
            continue

        feature_path = feature_index.get(Path(row.get("video_name", "")).stem)
        if not feature_path:
            continue

        features = np.load(feature_path).astype(np.float32)
        duration = parse_duration(row, feature_path, int(features.shape[0]))
        segments = load_segments(row)

        for label in tokens:
            if len(grouped[label]) >= args.max_word_prototypes:
                continue

            crop = features
            for segment in segments:
                if label in segment.get("labels", []):
                    start, end = segment_frame_bounds(segment, duration, int(features.shape[0]))
                    if end > start:
                        crop = features[start:end]
                    break

            if crop.shape[0] < 2:
                continue
            crop = normalize_features(crop, args.normalize, normalizer)
            grouped[label].append(
                Prototype(
                    label=label,
                    video_name=row.get("video_name", ""),
                    features=crop,
                    frames=int(crop.shape[0]),
                )
            )

    return grouped


def spot_candidates(
    features: np.ndarray,
    prototypes: dict[str, list[Prototype]],
    scales: list[float],
    args: argparse.Namespace,
    normalizer: Normalizer,
) -> list[Candidate]:
    candidates: list[Candidate] = []
    frame_count = int(features.shape[0])
    normalized = normalize_features(features, args.normalize, normalizer)

    for label, label_prototypes in prototypes.items():
        label_candidates: list[Candidate] = []
        for prototype in label_prototypes:
            for scale in scales:
                window = max(2, int(round(prototype.frames * scale)))
                if window > frame_count:
                    continue
                for start in range(0, frame_count - window + 1, args.stride):
                    end = start + window
                    chunk = normalized[start:end]
                    if args.distance == "dtw":
                        distance = dtw_distance(prototype.features, chunk, args.max_dtw_len)
                    else:
                        distance = resample_distance(prototype.features, chunk, args.match_frames)
                    label_candidates.append(Candidate(label, start, end, distance, distance, prototype.video_name))

        label_candidates = calibrate_label_candidates(label_candidates)
        label_candidates.sort(key=lambda item: item.score)
        candidates.extend(label_candidates[: args.candidate_limit_per_label])

    return candidates


def print_label_report(
    label_support: Counter[str],
    label_predictions: Counter[str],
    label_correct: Counter[str],
    errors: Counter[tuple[str, str, str]],
    top_errors: int,
) -> None:
    print("per_label")
    print("label\tsupport\tpredicted\tcorrect\tprecision\trecall")
    for label in sorted(label_support):
        support = label_support[label]
        predicted = label_predictions[label]
        correct = label_correct[label]
        precision = correct / predicted if predicted else 0.0
        recall = correct / support if support else 0.0
        print(f"{label}\t{support}\t{predicted}\t{correct}\t{precision:.4f}\t{recall:.4f}")

    if errors:
        print("top_errors")
        print("op\tref\tpred\tcount")
        for (op, ref, pred), count in errors.most_common(top_errors):
            print(f"{op}\t{ref}\t{pred}\t{count}")


def main() -> None:
    args = parse_args()
    labels = [label.strip() for label in args.labels.split(",") if label.strip()]
    allowed = set(labels)
    scales = [float(item) for item in args.window_scales.split(",") if item.strip()]

    rows = read_rows(args.manifest)
    feature_index = build_feature_index(args.feature_root)
    normalizer = fit_global_normalizer(rows, feature_index) if args.normalize == "global-zscore" else Normalizer()
    prototypes = build_prototypes(rows, feature_index, labels, args, normalizer)

    print("prototypes")
    for label in labels:
        print(f"{label}\t{len(prototypes.get(label, []))}")

    missing_prototypes = [label for label in labels if not prototypes.get(label)]
    if missing_prototypes:
        raise ValueError(f"Missing WORD prototypes for labels: {', '.join(missing_prototypes)}")

    eval_rows = [row for row in rows if row.get("kind") == "SEN" and row_tokens(row, allowed)]
    if args.split:
        eval_rows = [row for row in eval_rows if row.get("split") == args.split]
    if args.max_files > 0:
        eval_rows = eval_rows[: args.max_files]

    total_distance = 0
    total_reference_tokens = 0
    correct_sequences = 0
    label_support: Counter[str] = Counter()
    label_predictions: Counter[str] = Counter()
    label_correct: Counter[str] = Counter()
    errors: Counter[tuple[str, str, str]] = Counter()

    for row in eval_rows:
        reference_words = row_tokens(row, allowed)
        feature_path = feature_index.get(Path(row.get("video_name", "")).stem)
        if not feature_path:
            continue

        features = np.load(feature_path).astype(np.float32)
        reference_spans = row_token_spans(row, allowed, feature_path, int(features.shape[0]))
        candidates = spot_candidates(features, prototypes, scales, args, normalizer)
        if args.force_reference_labels:
            selected = choose_reference_labeled_candidates(candidates, reference_words, args.nms_overlap)
        else:
            selected = non_max_suppression(candidates, args.nms_overlap)

        if args.force_reference_labels:
            pass
        elif args.oracle_count:
            selected = selected[: len(reference_words)]
        elif args.threshold > 0.0:
            selected = [candidate for candidate in selected if candidate.distance <= args.threshold]
        else:
            selected = []

        selected = sorted(selected, key=lambda item: (item.start, item.end, item.distance))
        predicted_words = [candidate.label for candidate in selected]

        distance = edit_distance(reference_words, predicted_words)
        total_distance += distance
        total_reference_tokens += len(reference_words)
        correct_sequences += int(distance == 0)

        for label in reference_words:
            label_support[label] += 1
        for label in predicted_words:
            label_predictions[label] += 1
        for op, ref, pred in align_tokens(reference_words, predicted_words):
            if op == "match":
                label_correct[ref] += 1
            else:
                errors[(op, ref, pred)] += 1

        mean_distance = float(np.mean([candidate.distance for candidate in selected])) if selected else 0.0
        mean_score = float(np.mean([candidate.score for candidate in selected])) if selected else 0.0
        overlaps = [
            span_overlap(candidate, span)
            for candidate, span in zip(selected, reference_spans)
            if candidate.label == span[0]
        ]
        mean_overlap = float(np.mean(overlaps)) if overlaps else 0.0
        print(
            f"{row['video_name']}\t"
            f"ref={' '.join(reference_words)}\t"
            f"pred={' '.join(predicted_words) if predicted_words else '<empty>'}\t"
            f"edit={distance}\tmean_distance={mean_distance:.4f}\t"
            f"mean_score={mean_score:.4f}\tmean_overlap={mean_overlap:.4f}"
        )

    sequence_acc = correct_sequences / max(len(eval_rows), 1)
    wer = total_distance / max(total_reference_tokens, 1)
    print(f"summary files={len(eval_rows)} sequence_acc={sequence_acc:.4f} wer={wer:.4f}")
    if args.report_labels:
        print_label_report(label_support, label_predictions, label_correct, errors, args.top_errors)


if __name__ == "__main__":
    main()
