from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path

import numpy as np
import torch

from train_ctc_labeler import CtcLabeler


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a trained CTC recognizer and greedy-decode word tokens.")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--dataset-manifest", type=Path, required=True)
    parser.add_argument("--vocab", type=Path, required=True)
    parser.add_argument("--max-files", type=int, default=20)
    parser.add_argument("--split", default="")
    parser.add_argument("--kind", default="")
    parser.add_argument("--report-labels", action="store_true")
    parser.add_argument("--top-errors", type=int, default=20)
    parser.add_argument("--device", default="cpu")
    parser.add_argument(
        "--checkpoint-key",
        default="model_state",
        choices=["model_state", "final_model_state"],
        help="model_state is best validation loss; final_model_state is the last epoch if present.",
    )
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def greedy_decode_ids(ids: np.ndarray, id_to_label: dict[int, str], blank_id: int = 0) -> list[str]:
    words: list[str] = []
    previous = blank_id
    for value in ids.tolist():
        label_id = int(value)
        if label_id != blank_id and label_id != previous:
            words.append(id_to_label[label_id])
        previous = label_id
    return words


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
    checkpoint = torch.load(args.model, map_location=args.device)
    vocab = json.loads(args.vocab.read_text(encoding="utf-8"))
    id_to_label = {int(key): value for key, value in vocab["id_to_label"].items()}

    model = CtcLabeler(
        input_dim=int(checkpoint.get("input_dim", 332)),
        class_count=int(checkpoint.get("class_count", len(vocab["label_to_id"]))),
        encoder=str(checkpoint.get("encoder", "gru")),
        hidden_size=int(checkpoint.get("hidden_size", 128)),
        layers=int(checkpoint.get("layers", 2)),
        heads=int(checkpoint.get("heads", 4)),
        ff_size=int(checkpoint.get("ff_size", 512)),
        dropout=float(checkpoint.get("dropout", 0.1)),
    ).to(args.device)
    state_key = args.checkpoint_key
    if state_key not in checkpoint:
        raise KeyError(f"Checkpoint has no {state_key}. Retrain with the updated train_ctc_labeler.py.")
    model.load_state_dict(checkpoint[state_key])
    model.eval()

    rows = read_rows(args.dataset_manifest)
    if args.split:
        rows = [row for row in rows if row.get("split") == args.split]
    if args.kind:
        rows = [row for row in rows if row.get("kind") == args.kind]
    rows = [row for row in rows if (row.get("target_labels") or "").split()]
    if args.max_files > 0:
        rows = rows[: args.max_files]

    total_distance = 0
    total_reference_tokens = 0
    correct_sequences = 0
    label_support: Counter[str] = Counter()
    label_predictions: Counter[str] = Counter()
    label_correct: Counter[str] = Counter()
    errors: Counter[tuple[str, str, str]] = Counter()

    with torch.no_grad():
        for row in rows:
            features = np.load(row["feature_path"]).astype(np.float32)
            x = torch.from_numpy(features).unsqueeze(0).to(args.device)
            lengths = torch.tensor([features.shape[0]], dtype=torch.long, device=args.device)
            logits = model(x, lengths)
            predicted_ids = logits.argmax(dim=-1).squeeze(0).cpu().numpy()
            predicted_words = greedy_decode_ids(predicted_ids, id_to_label)
            reference_words = (row.get("target_labels") or "").split()

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

            print(
                f"{row['video_name']}\t"
                f"ref={' '.join(reference_words)}\t"
                f"pred={' '.join(predicted_words) if predicted_words else '<empty>'}\t"
                f"edit={distance}"
            )

    sequence_acc = correct_sequences / max(len(rows), 1)
    wer = total_distance / max(total_reference_tokens, 1)
    print(f"summary files={len(rows)} sequence_acc={sequence_acc:.4f} wer={wer:.4f}")
    if args.report_labels:
        print_label_report(label_support, label_predictions, label_correct, errors, args.top_errors)


if __name__ == "__main__":
    main()
