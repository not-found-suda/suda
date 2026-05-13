from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import tensorflow as tf


@dataclass(frozen=True)
class Candidate:
    start: int
    end: int
    label: str
    score: float
    margin: float
    second_label: str
    second_score: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scan full SEN feature streams with a fixed30 TFLite word spotter.",
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--label-map", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--split", default="val")
    parser.add_argument("--kind", default="SEN")
    parser.add_argument("--max-files", type=int, default=0)
    parser.add_argument("--window-sets", default="16,20,24,32,40,52,64;24,32,40,52,64;32,40,52,64")
    parser.add_argument("--stride", type=int, default=4)
    parser.add_argument("--score-thresholds", default="0.80,0.85,0.90,0.92,0.95,0.97")
    parser.add_argument("--margin-thresholds", default="0.30,0.35,0.40,0.45,0.50,0.60")
    parser.add_argument("--nms-ious", default="0.35,0.25,0.20,0.15")
    parser.add_argument("--max-detections", default="4,6,8")
    parser.add_argument("--ignore-labels", default="<background>")
    parser.add_argument("--canonical-suffixes", default="__SEN,__WORD")
    parser.add_argument("--top-configs", type=int, default=10)
    parser.add_argument("--show-examples", type=int, default=12)
    parser.add_argument("--top-errors", type=int, default=20)
    return parser.parse_args()


def parse_int_list(raw: str) -> list[int]:
    return [int(item.strip()) for item in raw.split(",") if item.strip()]


def parse_float_list(raw: str) -> list[float]:
    return [float(item.strip()) for item in raw.split(",") if item.strip()]


def parse_window_sets(raw: str) -> list[tuple[int, ...]]:
    return [tuple(parse_int_list(part)) for part in raw.split(";") if part.strip()]


def parse_suffixes(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def canonical_label(label: str, suffixes: list[str]) -> str:
    for suffix in suffixes:
        if suffix and label.endswith(suffix):
            return label[: -len(suffix)]
    return label


def read_label_map(path: Path) -> list[str]:
    with path.open("r", encoding="utf-8") as file:
        payload = json.load(file)
    if "labels" in payload:
        return list(payload["labels"])
    if "id_to_label" in payload:
        return [label for _, label in sorted(payload["id_to_label"].items(), key=lambda item: int(item[0]))]
    return [label for _, label in sorted(payload.items(), key=lambda item: int(item[0]))]


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


def reference_tokens(row: dict, known_labels: set[str]) -> list[str]:
    labels = [label for label in (row.get("contains_target") or "").split() if label in known_labels]
    if labels:
        return labels

    try:
        segments = json.loads(row.get("segments_json") or "[]")
    except json.JSONDecodeError:
        return []

    tokens: list[str] = []
    for segment in segments if isinstance(segments, list) else []:
        tokens.extend(label for label in segment.get("labels", []) if label in known_labels)
    return tokens


def edit_distance(ref: list[str], hyp: list[str]) -> int:
    previous = list(range(len(hyp) + 1))
    for i, ref_token in enumerate(ref, start=1):
        current = [i] + [0] * len(hyp)
        for j, hyp_token in enumerate(hyp, start=1):
            current[j] = min(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + int(ref_token != hyp_token),
            )
        previous = current
    return previous[-1]


def edit_ops(ref: list[str], hyp: list[str]) -> list[tuple[str, str, str]]:
    n = len(ref)
    m = len(hyp)
    dp = [[0] * (m + 1) for _ in range(n + 1)]
    back: list[list[tuple[str, int, int] | None]] = [[None] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        dp[i][0] = i
        back[i][0] = ("delete", i - 1, 0)
    for j in range(1, m + 1):
        dp[0][j] = j
        back[0][j] = ("insert", 0, j - 1)
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            choices = [
                (dp[i - 1][j] + 1, ("delete", i - 1, j)),
                (dp[i][j - 1] + 1, ("insert", i, j - 1)),
                (dp[i - 1][j - 1] + int(ref[i - 1] != hyp[j - 1]), ("match", i - 1, j - 1)),
            ]
            dp[i][j], back[i][j] = min(choices, key=lambda item: item[0])

    ops: list[tuple[str, str, str]] = []
    i = n
    j = m
    while i > 0 or j > 0:
        item = back[i][j]
        if item is None:
            break
        op, ref_index, hyp_index = item
        if op == "match":
            if ref[ref_index] != hyp[hyp_index]:
                ops.append(("substitute", ref[ref_index], hyp[hyp_index]))
            i -= 1
            j -= 1
        elif op == "delete":
            ops.append(("delete", ref[ref_index], "<empty>"))
            i -= 1
        else:
            ops.append(("insert", "<extra>", hyp[hyp_index]))
            j -= 1
    return list(reversed(ops))


def resample_features(features: np.ndarray, target_len: int = 30) -> np.ndarray:
    features = np.nan_to_num(features.astype(np.float32, copy=False))
    if features.shape[0] == target_len:
        return features
    if features.shape[0] <= 1:
        return np.repeat(features, target_len, axis=0)

    old_positions = np.arange(features.shape[0], dtype=np.float32)
    new_positions = np.linspace(0, features.shape[0] - 1, target_len, dtype=np.float32)
    output = np.empty((target_len, features.shape[1]), dtype=np.float32)
    for dim in range(features.shape[1]):
        output[:, dim] = np.interp(new_positions, old_positions, features[:, dim])
    return output


def softmax(logits: np.ndarray) -> np.ndarray:
    logits = logits - np.max(logits)
    exp = np.exp(logits)
    return exp / exp.sum()


def candidate_iou(left: Candidate, right: Candidate) -> float:
    overlap = max(0, min(left.end, right.end) - max(left.start, right.start))
    union = max(left.end, right.end) - min(left.start, right.start)
    return overlap / union if union else 0.0


def nms(candidates: list[Candidate], threshold: float) -> list[Candidate]:
    selected: list[Candidate] = []
    for candidate in sorted(candidates, key=lambda item: item.score, reverse=True):
        if all(candidate_iou(candidate, kept) <= threshold for kept in selected):
            selected.append(candidate)
    return selected


def collapse_repeats(candidates: list[Candidate]) -> list[Candidate]:
    collapsed: list[Candidate] = []
    for candidate in sorted(candidates, key=lambda item: (item.start, item.end)):
        if collapsed and collapsed[-1].label == candidate.label:
            if candidate.score > collapsed[-1].score:
                collapsed[-1] = candidate
        else:
            collapsed.append(candidate)
    return collapsed


def scan_windows(
    interpreter: tf.lite.Interpreter,
    input_index: int,
    output_index: int,
    labels: list[str],
    suffixes: list[str],
    features: np.ndarray,
    windows: tuple[int, ...],
    stride: int,
) -> list[Candidate]:
    frame_count = int(features.shape[0])
    starts: list[tuple[int, int]] = []
    seen: set[tuple[int, int]] = set()
    for window in windows:
        if window > frame_count:
            continue
        window_starts = list(range(0, frame_count - window + 1, stride))
        if window_starts and window_starts[-1] != frame_count - window:
            window_starts.append(frame_count - window)
        for start in window_starts:
            end = start + window
            if (start, end) not in seen:
                starts.append((start, end))
                seen.add((start, end))

    candidates: list[Candidate] = []
    for start, end in starts:
        crop = resample_features(features[start:end])
        interpreter.set_tensor(input_index, crop[None].astype(np.float32))
        interpreter.invoke()
        probabilities = softmax(interpreter.get_tensor(output_index)[0])
        scores_by_label: dict[str, float] = {}
        for index, score in enumerate(probabilities):
            label = canonical_label(labels[index], suffixes)
            scores_by_label[label] = scores_by_label.get(label, 0.0) + float(score)
        top = sorted(scores_by_label.items(), key=lambda item: item[1], reverse=True)
        best_label, best_score = top[0]
        second_label, second_score = top[1] if len(top) > 1 else top[0]
        candidates.append(
            Candidate(
                start=start,
                end=end,
                label=best_label,
                score=best_score,
                margin=best_score - second_score,
                second_label=second_label,
                second_score=second_score,
            )
        )
    return candidates


def decode(
    candidates: list[Candidate],
    ignored_labels: set[str],
    score_threshold: float,
    margin_threshold: float,
    nms_iou: float,
    max_detections: int,
) -> list[Candidate]:
    filtered = [
        candidate
        for candidate in candidates
        if candidate.label not in ignored_labels
        and candidate.score >= score_threshold
        and candidate.margin >= margin_threshold
    ]
    selected = collapse_repeats(nms(filtered, nms_iou))
    return selected[:max_detections]


def main() -> None:
    args = parse_args()
    labels = read_label_map(args.label_map)
    suffixes = parse_suffixes(args.canonical_suffixes)
    ignored_labels = {canonical_label(label.strip(), suffixes) for label in args.ignore_labels.split(",") if label.strip()}
    known_labels = {canonical_label(label, suffixes) for label in labels} - ignored_labels

    rows = read_rows(args.manifest)
    rows = [row for row in rows if (not args.kind or row.get("kind") == args.kind)]
    rows = [row for row in rows if (not args.split or row.get("split") == args.split)]
    rows = [row for row in rows if reference_tokens(row, known_labels)]
    if args.max_files > 0:
        rows = rows[: args.max_files]

    feature_index = build_feature_index(args.feature_root)
    interpreter = tf.lite.Interpreter(model_path=str(args.model))
    interpreter.allocate_tensors()
    input_index = interpreter.get_input_details()[0]["index"]
    output_index = interpreter.get_output_details()[0]["index"]

    window_sets = parse_window_sets(args.window_sets)
    score_thresholds = parse_float_list(args.score_thresholds)
    margin_thresholds = parse_float_list(args.margin_thresholds)
    nms_ious = parse_float_list(args.nms_ious)
    max_detection_values = parse_int_list(args.max_detections)

    scans: dict[tuple[int, ...], list[tuple[str, list[str], list[Candidate]]]] = {}
    for windows in window_sets:
        items: list[tuple[str, list[str], list[Candidate]]] = []
        for index, row in enumerate(rows, start=1):
            video_name = row.get("video_name", "")
            feature_path = feature_index.get(Path(video_name).stem)
            if not feature_path:
                print(f"missing_feature {video_name}", flush=True)
                continue
            features = np.load(feature_path).astype(np.float32)
            ref = reference_tokens(row, known_labels)
            raw = scan_windows(
                interpreter=interpreter,
                input_index=input_index,
                output_index=output_index,
                labels=labels,
                suffixes=suffixes,
                features=features,
                windows=windows,
                stride=args.stride,
            )
            items.append((video_name, ref, raw))
            if index % 10 == 0 or index == len(rows):
                print(f"scanned windows={','.join(map(str, windows))} {index}/{len(rows)}", flush=True)
        scans[windows] = items

    results: list[tuple[float, float, float, tuple[int, ...], float, float, int, int, Counter[tuple[str, str, str]]]] = []
    for windows, items in scans.items():
        for score_threshold in score_thresholds:
            for margin_threshold in margin_thresholds:
                for nms_iou in nms_ious:
                    for max_detections in max_detection_values:
                        sequence_correct = 0
                        total_edit = 0
                        total_ref = 0
                        top_errors: Counter[tuple[str, str, str]] = Counter()
                        for _, ref, raw in items:
                            selected = decode(
                                candidates=raw,
                                ignored_labels=ignored_labels,
                                score_threshold=score_threshold,
                                margin_threshold=margin_threshold,
                                nms_iou=nms_iou,
                                max_detections=max_detections,
                            )
                            hyp = [candidate.label for candidate in selected]
                            total_edit += edit_distance(ref, hyp)
                            total_ref += len(ref)
                            sequence_correct += int(ref == hyp)
                            for op in edit_ops(ref, hyp):
                                top_errors[op] += 1

                        total = max(len(items), 1)
                        sequence_acc = sequence_correct / total
                        wer = total_edit / max(total_ref, 1)
                        results.append(
                            (
                                wer,
                                -sequence_acc,
                                score_threshold,
                                windows,
                                margin_threshold,
                                nms_iou,
                                max_detections,
                                len(items),
                                top_errors,
                            )
                        )

    results.sort()
    print("top_configs")
    print("rank\tfiles\tsequence_acc\twer\tscore\tmargin\tnms_iou\tmax_det\twindows")
    for rank, result in enumerate(results[: args.top_configs], start=1):
        wer, neg_acc, score_threshold, windows, margin_threshold, nms_iou, max_detections, total, _ = result
        print(
            f"{rank}\t{total}\t{-neg_acc:.4f}\t{wer:.4f}\t{score_threshold:.2f}\t"
            f"{margin_threshold:.2f}\t{nms_iou:.2f}\t{max_detections}\t{','.join(map(str, windows))}"
        )

    best = results[0]
    wer, neg_acc, score_threshold, windows, margin_threshold, nms_iou, max_detections, _, top_errors = best
    print(
        f"best sequence_acc={-neg_acc:.4f} wer={wer:.4f} score={score_threshold:.2f} "
        f"margin={margin_threshold:.2f} nms_iou={nms_iou:.2f} max_det={max_detections} "
        f"windows={','.join(map(str, windows))}"
    )
    if top_errors:
        print("top_errors")
        print("op\tref\tpred\tcount")
        for (op, ref_token, hyp_token), count in top_errors.most_common(args.top_errors):
            print(f"{op}\t{ref_token}\t{hyp_token}\t{count}")

    if args.show_examples > 0:
        print("examples")
        for video_name, ref, raw in scans[windows][: args.show_examples]:
            selected = decode(
                candidates=raw,
                ignored_labels=ignored_labels,
                score_threshold=score_threshold,
                margin_threshold=margin_threshold,
                nms_iou=nms_iou,
                max_detections=max_detections,
            )
            hyp = [candidate.label for candidate in selected]
            print(f"{video_name}\tref={' '.join(ref)}\tpred={' '.join(hyp) or '<empty>'}\tedit={edit_distance(ref, hyp)}")
            for candidate in selected[:5]:
                print(
                    f"  hit {candidate.start:03d}-{candidate.end:03d}\t{candidate.label}\t"
                    f"score={candidate.score:.3f}\tmargin={candidate.margin:.3f}\t"
                    f"second={candidate.second_label}:{candidate.second_score:.3f}"
                )


if __name__ == "__main__":
    main()
