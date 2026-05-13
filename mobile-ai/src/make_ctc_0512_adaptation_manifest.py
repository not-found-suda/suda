from __future__ import annotations

import argparse
import csv
from pathlib import Path

import numpy as np


FIELDNAMES = [
    "split",
    "kind",
    "video_name",
    "feature_path",
    "label_path",
    "frames",
    "duration",
    "target_labels",
    "nonblank_frames",
]


TARGETS = {
    "기차가다": "기차 가다",
    "방법알려주다": "방법 알려주다",
    "방법없다알려주다": "방법 없다 알려주다",
    "병원가다": "병원 가다",
    "빨리병원가다": "빨리 병원 가다",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Append 0512 user-domain CTC train/val rows to an existing dataset manifest.",
    )
    parser.add_argument("--base-manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--train-repeat",
        type=int,
        default=40,
        help="How many times to repeat each selected 0512 training clip.",
    )
    parser.add_argument(
        "--train-index",
        default="1",
        help="Comma-separated recording indices to use as adaptation train clips.",
    )
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_rows(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in FIELDNAMES})


def target_for_stem(stem: str) -> tuple[str, str] | None:
    for prefix, labels in TARGETS.items():
        if stem.startswith(prefix):
            return prefix, labels
    return None


def recording_index(stem: str, phrase: str) -> str:
    return stem.removeprefix(phrase)


def build_user_row(path: Path, split: str, phrase: str, labels: str, repeat_index: int | None) -> dict:
    features = np.load(path, mmap_mode="r")
    video_name = path.with_suffix(".mp4").name
    if repeat_index is not None:
        video_name = f"{video_name}:adapt:{repeat_index}"

    return {
        "split": split,
        "kind": "USER_0512",
        "video_name": video_name,
        "feature_path": str(path),
        "label_path": "",
        "frames": int(features.shape[0]),
        "duration": "",
        "target_labels": labels,
        "nonblank_frames": int(features.shape[0]),
    }


def main() -> None:
    args = parse_args()
    train_indices = {item.strip() for item in args.train_index.split(",") if item.strip()}
    rows = read_rows(args.base_manifest)

    added_train = 0
    added_val = 0
    for path in sorted(args.feature_root.glob("*.npy")):
        match = target_for_stem(path.stem)
        if match is None:
            continue

        phrase, labels = match
        index = recording_index(path.stem, phrase)
        if index in train_indices:
            for repeat_index in range(args.train_repeat):
                rows.append(build_user_row(path, "train", phrase, labels, repeat_index))
                added_train += 1
        else:
            rows.append(build_user_row(path, "val", phrase, labels, None))
            added_val += 1

    write_rows(args.output, rows)
    print(f"output={args.output}")
    print(f"base_rows={len(read_rows(args.base_manifest))}")
    print(f"added_train_rows={added_train}")
    print(f"added_val_rows={added_val}")
    print(f"total_rows={len(rows)}")


if __name__ == "__main__":
    main()
