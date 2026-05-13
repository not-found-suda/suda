from __future__ import annotations

import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path


DEFAULT_VAL_SEQUENCES = [
    "가다 원하다",
    "불량 불가능",
    "빨리 가능",
]

DEFAULT_RELATED_SEQUENCES = [
    "가다 원하다 방법",
    "가다 원하다 가다 방법",
    "불가능 불량",
    "에어컨 불가능",
    "에어컨 불량",
    "가다 빨리",
    "가다 빨리 방법",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create a semi-composition holdout: WORD rows stay in train, selected SEN "
            "sequence types go to val, and related partial/complementary SEN sequence "
            "types stay in train."
        ),
    )
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--val-sequences",
        default="|".join(DEFAULT_VAL_SEQUENCES),
        help="Pipe-separated exact SEN target sequences to hold out for validation.",
    )
    parser.add_argument(
        "--related-sequences",
        default="|".join(DEFAULT_RELATED_SEQUENCES),
        help="Pipe-separated sequence types expected to remain in train for support.",
    )
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def sequence(row: dict) -> str:
    return " ".join((row.get("contains_target") or "").split())


def write_rows(path: Path, rows: list[dict], fieldnames) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    args = parse_args()
    rows = read_rows(args.input)
    if not rows:
        raise ValueError(f"No rows found: {args.input}")

    val_sequences = {item.strip() for item in args.val_sequences.split("|") if item.strip()}
    related_sequences = {item.strip() for item in args.related_sequences.split("|") if item.strip()}

    seq_groups: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        if row.get("kind") == "SEN" and sequence(row):
            seq_groups[sequence(row)].append(row)

    missing_val = sorted(seq for seq in val_sequences if seq not in seq_groups)
    if missing_val:
        raise ValueError(f"Missing validation sequence types: {missing_val}")

    for row in rows:
        if row.get("kind") == "WORD":
            row["split"] = "train"
        elif row.get("kind") == "SEN" and sequence(row) in val_sequences:
            row["split"] = "val"
        else:
            row["split"] = "train"

    train_labels: Counter[str] = Counter()
    val_labels: Counter[str] = Counter()
    for row in rows:
        labels = sequence(row).split()
        if row.get("split") == "train":
            train_labels.update(labels)
        elif row.get("split") == "val":
            val_labels.update(labels)

    missing_train_labels = sorted(label for label in val_labels if train_labels[label] == 0)
    if missing_train_labels:
        raise ValueError(f"Validation labels absent from train: {missing_train_labels}")

    write_rows(args.output, rows, rows[0].keys())

    print(f"written {args.output}")
    print(f"rows {len(rows)}")
    print("split", dict(Counter(row.get("split", "") for row in rows)))
    print()
    print("val_sequences")
    for seq in sorted(val_sequences):
        print(f"{len(seq_groups[seq])}\t{seq}")
    print()
    print("related_train_sequences")
    for seq in sorted(related_sequences):
        count = sum(1 for row in seq_groups.get(seq, []) if row.get("split") == "train")
        print(f"{count}\t{seq}")
    print()
    print("val_labels", dict(sorted(val_labels.items())))
    print("missing_train_labels", missing_train_labels)


if __name__ == "__main__":
    main()
