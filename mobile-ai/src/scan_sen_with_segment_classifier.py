from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch

from train_sequence_classifier import SequenceClassifier, collate_batch


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
        description="Scan full SEN videos with a trained SEN-segment word classifier.",
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--split", default="val")
    parser.add_argument("--kind", default="SEN")
    parser.add_argument("--max-files", type=int, default=0)
    parser.add_argument("--window-frames", default="8,12,16,20,24,32,40,52,64")
    parser.add_argument("--stride", type=int, default=4)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--score-threshold", type=float, default=0.70)
    parser.add_argument("--margin-threshold", type=float, default=0.20)
    parser.add_argument("--nms-iou", type=float, default=0.35)
    parser.add_argument("--max-detections", type=int, default=8)
    parser.add_argument("--ignore-labels", default="<background>")
    parser.add_argument(
        "--canonical-suffixes",
        default="",
        help=(
            "Comma-separated suffixes to strip and merge during decoding, "
            "for example '__SEN,__WORD'. Probabilities for labels that share "
            "a canonical form are summed before thresholding."
        ),
    )
    parser.add_argument(
        "--oracle-count",
        action="store_true",
        help="After NMS, keep only as many detections as reference tokens. Useful for diagnosis only.",
    )
    parser.add_argument("--checkpoint-key", choices=["model_state", "final_model_state"], default="model_state")
    parser.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    parser.add_argument("--top-windows", type=int, default=5)
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


def parse_windows(raw: str) -> list[int]:
    windows = sorted({int(item.strip()) for item in raw.split(",") if item.strip()})
    if not windows or any(window <= 1 for window in windows):
        raise ValueError("--window-frames must contain integers > 1")
    return windows


def parse_suffixes(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def canonical_label(label: str, suffixes: list[str]) -> str:
    for suffix in suffixes:
        if suffix and label.endswith(suffix):
            return label[: -len(suffix)]
    return label


def reference_tokens(row: dict, known_labels: set[str]) -> list[str]:
    labels = [label for label in (row.get("contains_target") or "").split() if label in known_labels]
    if labels:
        return labels

    raw = row.get("segments_json") or "[]"
    try:
        segments = json.loads(raw)
    except json.JSONDecodeError:
        return []
    tokens: list[str] = []
    for segment in segments if isinstance(segments, list) else []:
        segment_labels = [label for label in segment.get("labels", []) if label in known_labels]
        tokens.extend(segment_labels)
    return tokens


def edit_distance(ref: list[str], hyp: list[str]) -> int:
    previous = list(range(len(hyp) + 1))
    for i, ref_token in enumerate(ref, start=1):
        current = [i] + [0] * len(hyp)
        for j, hyp_token in enumerate(hyp, start=1):
            substitution = previous[j - 1] + int(ref_token != hyp_token)
            insertion = current[j - 1] + 1
            deletion = previous[j] + 1
            current[j] = min(substitution, insertion, deletion)
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
    i, j = n, m
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


def iou(a: Candidate, b: Candidate) -> float:
    overlap = max(0, min(a.end, b.end) - max(a.start, b.start))
    union = max(a.end, b.end) - min(a.start, b.start)
    return overlap / union if union > 0 else 0.0


def nms(candidates: list[Candidate], threshold: float) -> list[Candidate]:
    selected: list[Candidate] = []
    for candidate in sorted(candidates, key=lambda item: item.score, reverse=True):
        if all(iou(candidate, kept) <= threshold for kept in selected):
            selected.append(candidate)
    return selected


def collapse_repeats(candidates: list[Candidate]) -> list[Candidate]:
    collapsed: list[Candidate] = []
    for candidate in sorted(candidates, key=lambda item: (item.start, item.end)):
        if collapsed and collapsed[-1].label == candidate.label:
            previous = collapsed[-1]
            if candidate.score > previous.score:
                collapsed[-1] = candidate
        else:
            collapsed.append(candidate)
    return collapsed


def scan_windows(
    model: SequenceClassifier,
    features: np.ndarray,
    id_to_label: dict[int, str],
    windows: list[int],
    stride: int,
    batch_size: int,
    device: str,
    canonical_suffixes: list[str],
) -> list[Candidate]:
    windows_to_score: list[tuple[int, int, np.ndarray]] = []
    frame_count = int(features.shape[0])
    for window in windows:
        if window > frame_count:
            continue
        for start in range(0, frame_count - window + 1, stride):
            end = start + window
            windows_to_score.append((start, end, features[start:end]))
        if (frame_count - window) % stride:
            start = frame_count - window
            end = frame_count
            windows_to_score.append((start, end, features[start:end]))

    candidates: list[Candidate] = []
    model.eval()
    with torch.no_grad():
        for offset in range(0, len(windows_to_score), batch_size):
            batch_items = windows_to_score[offset : offset + batch_size]
            tensor_batch = [
                (
                    torch.from_numpy(np.nan_to_num(crop).astype(np.float32, copy=False)),
                    int(crop.shape[0]),
                    0,
                )
                for _, _, crop in batch_items
            ]
            batch = collate_batch(tensor_batch)
            logits = model(batch.features.to(device), batch.lengths.to(device))
            probs = torch.softmax(logits, dim=-1).cpu().numpy()
            for (start, end, _), prob in zip(batch_items, probs):
                if canonical_suffixes:
                    scores_by_label: dict[str, float] = {}
                    for index, score_value in enumerate(prob):
                        label = canonical_label(id_to_label[int(index)], canonical_suffixes)
                        scores_by_label[label] = scores_by_label.get(label, 0.0) + float(score_value)
                    top_labels = sorted(scores_by_label.items(), key=lambda item: item[1], reverse=True)
                    best_label, score = top_labels[0]
                    second_label, second_score = top_labels[1] if len(top_labels) > 1 else top_labels[0]
                else:
                    top = prob.argsort()[::-1][:2]
                    best_index = int(top[0])
                    second_index = int(top[1]) if len(top) > 1 else best_index
                    score = float(prob[best_index])
                    second_score = float(prob[second_index])
                    best_label = id_to_label[best_index]
                    second_label = id_to_label[second_index]
                candidates.append(
                    Candidate(
                        start=start,
                        end=end,
                        label=best_label,
                        score=score,
                        margin=score - second_score,
                        second_label=second_label,
                        second_score=second_score,
                    )
                )
    return candidates


def main() -> None:
    args = parse_args()
    windows = parse_windows(args.window_frames)
    canonical_suffixes = parse_suffixes(args.canonical_suffixes)
    checkpoint = torch.load(args.model, map_location=args.device)
    id_to_label = {int(index): label for index, label in checkpoint["id_to_label"].items()}
    ignored_labels = {
        canonical_label(label.strip(), canonical_suffixes)
        for label in args.ignore_labels.split(",")
        if label.strip()
    }
    known_labels = {canonical_label(label, canonical_suffixes) for label in id_to_label.values()} - ignored_labels

    model = SequenceClassifier(
        input_dim=int(checkpoint.get("input_dim", 332)),
        class_count=int(checkpoint.get("class_count", len(id_to_label))),
        hidden_size=int(checkpoint.get("hidden_size", 128)),
        layers=int(checkpoint.get("layers", 2)),
        dropout=float(checkpoint.get("dropout", 0.2)),
    ).to(args.device)
    model.load_state_dict(checkpoint[args.checkpoint_key])

    rows = read_rows(args.manifest)
    feature_index = build_feature_index(args.feature_root)
    if args.split:
        rows = [row for row in rows if row.get("split") == args.split]
    if args.kind:
        rows = [row for row in rows if row.get("kind") == args.kind]
    rows = [row for row in rows if reference_tokens(row, known_labels)]
    if args.max_files > 0:
        rows = rows[: args.max_files]

    sequence_correct = 0
    total_edit = 0
    total_ref = 0
    top_errors: Counter[tuple[str, str, str]] = Counter()

    for row in rows:
        video_name = row.get("video_name", "")
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            print(f"{video_name}\tmissing_feature")
            continue

        features = np.load(feature_path).astype(np.float32)
        raw_candidates = scan_windows(
            model=model,
            features=features,
            id_to_label=id_to_label,
            windows=windows,
            stride=args.stride,
            batch_size=args.batch_size,
            device=args.device,
            canonical_suffixes=canonical_suffixes,
        )
        filtered = [
            candidate
            for candidate in raw_candidates
            if candidate.label not in ignored_labels
            and candidate.score >= args.score_threshold
            and candidate.margin >= args.margin_threshold
        ]
        selected = collapse_repeats(nms(filtered, args.nms_iou))
        ref = reference_tokens(row, known_labels)
        if args.oracle_count:
            selected = sorted(selected, key=lambda item: item.score, reverse=True)[: len(ref)]
            selected = sorted(selected, key=lambda item: (item.start, item.end))
        else:
            selected = selected[: args.max_detections]

        hyp = [candidate.label for candidate in selected]
        edit = edit_distance(ref, hyp)
        total_edit += edit
        total_ref += len(ref)
        sequence_correct += int(ref == hyp)
        for op in edit_ops(ref, hyp):
            top_errors[op] += 1

        print(
            f"{video_name}\tref={' '.join(ref)}\tpred={' '.join(hyp) or '<empty>'}\t"
            f"edit={edit}\twindows={len(raw_candidates)}\tkept={len(selected)}"
        )
        for candidate in selected[: args.top_windows]:
            print(
                f"  hit {candidate.start}-{candidate.end}\t{candidate.label}\t"
                f"score={candidate.score:.3f}\tmargin={candidate.margin:.3f}\t"
                f"second={candidate.second_label}:{candidate.second_score:.3f}"
            )

    total = max(len(rows), 1)
    print(
        f"summary files={len(rows)} sequence_acc={sequence_correct / total:.4f} "
        f"wer={total_edit / max(total_ref, 1):.4f}"
    )
    if top_errors:
        print("top_errors")
        print("op\tref\tpred\tcount")
        for (op, ref_token, hyp_token), count in top_errors.most_common(args.top_errors):
            print(f"{op}\t{ref_token}\t{hyp_token}\t{count}")


if __name__ == "__main__":
    main()
