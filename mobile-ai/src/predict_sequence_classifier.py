from __future__ import annotations

import argparse
import csv
from collections import Counter
from pathlib import Path

import numpy as np
import torch

from train_sequence_classifier import SequenceClassifier


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate a trained sequence-level WORD/intent classifier.")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--split", default="val")
    parser.add_argument("--kind", default="")
    parser.add_argument("--max-files", type=int, default=0)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--checkpoint-key", choices=["model_state", "final_model_state"], default="model_state")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--report-labels", action="store_true")
    parser.add_argument("--top-errors", type=int, default=20)
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def main() -> None:
    args = parse_args()
    checkpoint = torch.load(args.model, map_location=args.device)
    id_to_label = {int(index): label for index, label in checkpoint["id_to_label"].items()}
    label_to_id = {label: index for index, label in id_to_label.items()}

    model = SequenceClassifier(
        input_dim=int(checkpoint.get("input_dim", 332)),
        class_count=int(checkpoint.get("class_count", len(id_to_label))),
        hidden_size=int(checkpoint.get("hidden_size", 128)),
        layers=int(checkpoint.get("layers", 2)),
        dropout=float(checkpoint.get("dropout", 0.2)),
    ).to(args.device)
    model.load_state_dict(checkpoint[args.checkpoint_key])
    model.eval()

    rows = read_rows(args.manifest)
    if args.split:
        rows = [row for row in rows if row.get("split") == args.split]
    if args.kind:
        rows = [row for row in rows if row.get("kind") == args.kind]
    rows = [row for row in rows if row.get("label") in label_to_id]
    if args.max_files > 0:
        rows = rows[: args.max_files]

    top1_correct = 0
    topk_correct = 0
    support: Counter[str] = Counter()
    predicted: Counter[str] = Counter()
    correct: Counter[str] = Counter()
    errors: Counter[tuple[str, str]] = Counter()

    with torch.no_grad():
        for row in rows:
            features = np.load(row["feature_path"]).astype(np.float32)
            x = torch.from_numpy(features).unsqueeze(0).to(args.device)
            lengths = torch.tensor([features.shape[0]], dtype=torch.long, device=args.device)
            logits = model(x, lengths)
            probs = torch.softmax(logits, dim=-1).squeeze(0).cpu().numpy()
            top_indices = probs.argsort()[::-1][: args.top_k]
            top_labels = [id_to_label[int(index)] for index in top_indices]
            top_scores = [float(probs[int(index)]) for index in top_indices]

            ref = row["label"]
            hyp = top_labels[0]
            top1_correct += int(hyp == ref)
            topk_correct += int(ref in top_labels)
            support[ref] += 1
            predicted[hyp] += 1
            if hyp == ref:
                correct[ref] += 1
            else:
                errors[(ref, hyp)] += 1

            top_text = " ".join(f"{label}:{score:.3f}" for label, score in zip(top_labels, top_scores))
            print(f"{row['video_name']}\tref={ref}\tpred={hyp}\ttop={top_text}")

    total = max(len(rows), 1)
    print(f"summary files={len(rows)} top1_acc={top1_correct / total:.4f} top{args.top_k}_acc={topk_correct / total:.4f}")

    if args.report_labels:
        print("per_label")
        print("label\tsupport\tpredicted\tcorrect\tprecision\trecall")
        for label in sorted(support):
            precision = correct[label] / predicted[label] if predicted[label] else 0.0
            recall = correct[label] / support[label] if support[label] else 0.0
            print(f"{label}\t{support[label]}\t{predicted[label]}\t{correct[label]}\t{precision:.4f}\t{recall:.4f}")

        if errors:
            print("top_errors")
            print("ref\tpred\tcount")
            for (ref, hyp), count in errors.most_common(args.top_errors):
                print(f"{ref}\t{hyp}\t{count}")


if __name__ == "__main__":
    main()
