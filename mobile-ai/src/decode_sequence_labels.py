from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collapse frame-level class ids into a word sequence.")
    parser.add_argument("--predictions", type=Path, required=True, help=".npy of [T] ids or [T, C] logits/probs.")
    parser.add_argument("--vocab", type=Path, required=True)
    parser.add_argument("--min-run", type=int, default=3)
    parser.add_argument("--min-confidence", type=float, default=0.0)
    return parser.parse_args()


def collapse_ids(
    ids: np.ndarray,
    id_to_label: dict[int, str],
    min_run: int,
    confidences: np.ndarray | None = None,
    min_confidence: float = 0.0,
) -> list[str]:
    words: list[str] = []
    previous_word = ""
    index = 0
    while index < len(ids):
        label_id = int(ids[index])
        run_end = index + 1
        while run_end < len(ids) and int(ids[run_end]) == label_id:
            run_end += 1

        label = id_to_label.get(label_id, "<unk>")
        run_confidence = 1.0
        if confidences is not None:
            run_confidence = float(np.mean(confidences[index:run_end]))

        if (
            label_id != 0
            and run_end - index >= min_run
            and run_confidence >= min_confidence
            and label != previous_word
        ):
            words.append(label)
            previous_word = label
        if label_id == 0:
            previous_word = ""
        index = run_end
    return words


def main() -> None:
    args = parse_args()
    payload = json.loads(args.vocab.read_text(encoding="utf-8"))
    id_to_label = {int(key): value for key, value in payload["id_to_label"].items()}

    values = np.load(args.predictions)
    if values.ndim == 2:
        shifted = values - values.max(axis=-1, keepdims=True)
        probs = np.exp(shifted) / np.exp(shifted).sum(axis=-1, keepdims=True)
        ids = probs.argmax(axis=-1)
        confidences = probs[np.arange(len(ids)), ids]
    else:
        ids = values.astype(np.int64)
        confidences = None
    words = collapse_ids(ids, id_to_label, args.min_run, confidences, args.min_confidence)
    print(" ".join(words))


if __name__ == "__main__":
    main()
