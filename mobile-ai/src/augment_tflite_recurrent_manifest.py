from __future__ import annotations

import argparse
import csv
import random
from pathlib import Path

import numpy as np


FIELDNAMES = [
    "split",
    "kind",
    "video_name",
    "feature_path",
    "label",
    "frames",
    "source_video",
    "segment_index",
    "segment_start",
    "segment_end",
]


HAND_DIMS = 126


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create an augmented manifest for fixed-length recurrent/TFLite sign classifiers.",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument(
        "--labels",
        required=True,
        help="Comma-separated base labels to augment. Suffixes like __SEN are ignored for matching.",
    )
    parser.add_argument(
        "--label-suffix",
        default="__SEN",
        help="Only augment labels ending with this suffix. Use an empty value to include all suffixes.",
    )
    parser.add_argument("--copies-per-row", type=int, default=2)
    parser.add_argument("--speed-min", type=float, default=0.80)
    parser.add_argument("--speed-max", type=float, default=1.25)
    parser.add_argument("--crop-jitter", type=int, default=3)
    parser.add_argument("--noise-std", type=float, default=0.01)
    parser.add_argument("--hand-dropout", type=float, default=0.02)
    parser.add_argument("--scale-std", type=float, default=0.03)
    parser.add_argument("--min-frames", type=int, default=8)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--overwrite", action="store_true")
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


def base_label(label: str) -> str:
    return label.replace("__SEN", "").replace("__WORD", "")


def temporal_resample(features: np.ndarray, target_len: int) -> np.ndarray:
    if len(features) == target_len:
        return features.astype(np.float32, copy=False)
    if len(features) <= 1:
        return np.repeat(features, target_len, axis=0).astype(np.float32, copy=False)

    old_positions = np.arange(len(features), dtype=np.float32)
    new_positions = np.linspace(0, len(features) - 1, target_len, dtype=np.float32)
    output = np.empty((target_len, features.shape[1]), dtype=np.float32)
    for dim in range(features.shape[1]):
        output[:, dim] = np.interp(new_positions, old_positions, features[:, dim])
    return output


def random_crop(features: np.ndarray, jitter: int, min_frames: int, rng: random.Random) -> np.ndarray:
    if jitter <= 0 or len(features) <= min_frames + 2:
        return features

    trim_start = rng.randint(0, jitter)
    trim_end = rng.randint(0, jitter)
    if len(features) - trim_start - trim_end < min_frames:
        return features
    return features[trim_start : len(features) - trim_end]


def augment_features(
    features: np.ndarray,
    args: argparse.Namespace,
    rng: random.Random,
) -> np.ndarray:
    output = np.nan_to_num(features.astype(np.float32, copy=False))
    output = random_crop(output, args.crop_jitter, args.min_frames, rng)

    speed = rng.uniform(args.speed_min, args.speed_max)
    target_len = max(args.min_frames, int(round(len(output) / speed)))
    output = temporal_resample(output, target_len)

    if args.scale_std > 0:
        scale = rng.gauss(1.0, args.scale_std)
        output = output * np.float32(scale)

    if args.noise_std > 0:
        sampled = np.fromiter(
            (rng.normalvariate(0.0, args.noise_std) for _ in range(output.size)),
            dtype=np.float32,
            count=output.size,
        ).reshape(output.shape)
        output = output + sampled

    if args.hand_dropout > 0 and output.shape[1] >= HAND_DIMS:
        keep = np.ones((len(output), 1), dtype=np.float32)
        for index in range(len(output)):
            if rng.random() < args.hand_dropout:
                keep[index, 0] = 0.0
        output[:, :HAND_DIMS] *= keep

    return np.nan_to_num(output).astype(np.float32, copy=False)


def should_augment(row: dict, focus_labels: set[str], label_suffix: str) -> bool:
    if row.get("split") != "train":
        return False

    label = row.get("label", "")
    if label_suffix and not label.endswith(label_suffix):
        return False

    return base_label(label) in focus_labels


def main() -> None:
    args = parse_args()
    if args.copies_per_row < 0:
        raise ValueError("--copies-per-row must be >= 0")
    if args.speed_min <= 0 or args.speed_max <= 0 or args.speed_min > args.speed_max:
        raise ValueError("--speed-min and --speed-max must be positive and ordered")

    rng = random.Random(args.seed)
    focus_labels = {item.strip() for item in args.labels.split(",") if item.strip()}
    if not focus_labels:
        raise ValueError("--labels must contain at least one label")

    output_features = args.output_root / "features_aug"
    source_rows = read_rows(args.manifest)
    output_rows = list(source_rows)
    added = 0
    skipped = 0

    for row_index, row in enumerate(source_rows):
        if not should_augment(row, focus_labels, args.label_suffix):
            continue

        feature_path = Path(row.get("feature_path", ""))
        if not feature_path.exists():
            skipped += 1
            continue

        features = np.load(feature_path).astype(np.float32, copy=False)
        if features.ndim != 2 or len(features) < args.min_frames:
            skipped += 1
            continue

        for copy_index in range(args.copies_per_row):
            augmented = augment_features(features, args, rng)
            if len(augmented) < args.min_frames:
                skipped += 1
                continue

            out_path = output_features / f"aug_{row_index:06d}_{copy_index}_{feature_path.stem}.npy"
            out_path.parent.mkdir(parents=True, exist_ok=True)
            if args.overwrite or not out_path.exists():
                np.save(out_path, augmented)

            output_row = dict(row)
            output_row["kind"] = f"{row.get('kind', '')}_AUG".strip("_")
            output_row["video_name"] = f"{row.get('video_name', feature_path.name)}:aug:{copy_index}"
            output_row["feature_path"] = str(out_path)
            output_row["frames"] = int(augmented.shape[0])
            output_row["segment_index"] = f"{row.get('segment_index', '')}:aug:{copy_index}"
            output_rows.append(output_row)
            added += 1

    manifest_path = args.output_root / "sen_word_variant_spotter_manifest.csv"
    write_rows(manifest_path, output_rows)

    print(f"manifest={manifest_path}")
    print(f"base_rows={len(source_rows)}")
    print(f"added_rows={added}")
    print(f"skipped_rows={skipped}")
    print(f"total_rows={len(output_rows)}")


if __name__ == "__main__":
    main()
