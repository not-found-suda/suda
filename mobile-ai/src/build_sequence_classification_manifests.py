from __future__ import annotations

import argparse
import csv
import re
from collections import Counter, defaultdict
from pathlib import Path


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
        description="Build WORD and SEN intent classification manifests from an AI Hub morpheme manifest.",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--labels", default=",".join(DEFAULT_LABELS))
    parser.add_argument("--val-reals", default="13,14,15,16")
    parser.add_argument("--word-val-per-label", type=int, default=2)
    parser.add_argument("--min-intent-count", type=int, default=8)
    parser.add_argument("--max-intents", type=int, default=30)
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


def real_id(video_name: str) -> str:
    match = re.search(r"_REAL(\d+)_", video_name)
    return match.group(1) if match else ""


def target_sequence(row: dict, allowed: set[str]) -> str:
    labels = [label for label in (row.get("contains_target") or "").split() if label in allowed]
    return " ".join(labels)


def write_manifest(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = ["split", "kind", "video_name", "feature_path", "label", "frames"]
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    args = parse_args()
    allowed = {label.strip() for label in args.labels.split(",") if label.strip()}
    val_reals = {item.strip() for item in args.val_reals.split(",") if item.strip()}

    rows = read_rows(args.manifest)
    feature_index = build_feature_index(args.feature_root)

    word_rows: list[dict] = []
    intent_source: dict[str, list[dict]] = defaultdict(list)

    for row in rows:
        video_name = row.get("video_name", "")
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            continue

        sequence = target_sequence(row, allowed)
        if not sequence:
            continue

        if row.get("kind") == "WORD":
            tokens = sequence.split()
            if len(tokens) != 1:
                continue
            feature = feature_path
            word_rows.append(
                {
                    "split": "val" if real_id(video_name) in val_reals else "train",
                    "kind": "WORD",
                    "video_name": video_name,
                    "feature_path": str(feature),
                    "label": tokens[0],
                    "frames": "",
                }
            )
        elif row.get("kind") == "SEN":
            intent_source[sequence].append(row)

    intent_counts = Counter({sequence: len(group) for sequence, group in intent_source.items()})
    selected_intents = [
        sequence
        for sequence, count in intent_counts.most_common()
        if count >= args.min_intent_count
    ][: args.max_intents]

    intent_rows: list[dict] = []
    for sequence in selected_intents:
        for row in intent_source[sequence]:
            video_name = row.get("video_name", "")
            feature_path = feature_index.get(Path(video_name).stem)
            if not feature_path:
                continue
            intent_rows.append(
                {
                    "split": "val" if real_id(video_name) in val_reals else "train",
                    "kind": "SEN",
                    "video_name": video_name,
                    "feature_path": str(feature_path),
                    "label": sequence,
                    "frames": "",
                }
            )

    for out_row in word_rows + intent_rows:
        try:
            import numpy as np

            out_row["frames"] = int(np.load(out_row["feature_path"], mmap_mode="r").shape[0])
        except Exception:
            out_row["frames"] = ""

    if word_rows and not any(row["split"] == "val" for row in word_rows):
        by_label: dict[str, list[dict]] = defaultdict(list)
        for row in word_rows:
            by_label[row["label"]].append(row)
        for label_rows in by_label.values():
            label_rows.sort(key=lambda row: row["video_name"])
            for row in label_rows[-args.word_val_per_label :]:
                row["split"] = "val"

    args.output_root.mkdir(parents=True, exist_ok=True)
    word_path = args.output_root / "word_classifier_manifest.csv"
    intent_path = args.output_root / "intent_classifier_manifest.csv"
    write_manifest(word_path, word_rows)
    write_manifest(intent_path, intent_rows)

    print(f"word_manifest={word_path}")
    print(f"word_rows={len(word_rows)}")
    print("word_split", dict(Counter(row["split"] for row in word_rows)))
    print("word_labels", len({row["label"] for row in word_rows}))
    print()
    print(f"intent_manifest={intent_path}")
    print(f"intent_rows={len(intent_rows)}")
    print("intent_split", dict(Counter(row["split"] for row in intent_rows)))
    print("intent_labels", len(selected_intents))
    print("selected_intents")
    for sequence in selected_intents:
        print(f"{intent_counts[sequence]}\t{sequence}")


if __name__ == "__main__":
    main()
