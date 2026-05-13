from __future__ import annotations

import argparse
import csv
import random
from pathlib import Path

import numpy as np


HAND_DIMS = 126


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create a lightly augmented CTC dataset manifest.")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument(
        "--labels",
        default="",
        help="Optional comma-separated target labels. If set, augment rows containing at least one of them.",
    )
    parser.add_argument("--copies-per-row", type=int, default=1)
    parser.add_argument("--speed-min", type=float, default=0.85)
    parser.add_argument("--speed-max", type=float, default=1.20)
    parser.add_argument(
        "--length-aware-speed",
        action="store_true",
        help="Limit speed-up so short clips do not become too short.",
    )
    parser.add_argument(
        "--min-target-ratio",
        type=float,
        default=0.60,
        help="Minimum augmented length as a ratio of the cropped source length when --length-aware-speed is set.",
    )
    parser.add_argument(
        "--min-target-frames",
        type=int,
        default=15,
        help="Minimum augmented length in frames when --length-aware-speed is set.",
    )
    parser.add_argument("--crop-jitter", type=int, default=2)
    parser.add_argument("--noise-std", type=float, default=0.006)
    parser.add_argument("--scale-std", type=float, default=0.02)
    parser.add_argument("--hand-dropout", type=float, default=0.01)
    parser.add_argument("--min-frames", type=int, default=8)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def read_rows(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        return list(reader.fieldnames or []), list(reader)


def write_rows(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fieldnames})


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


def augment_features(features: np.ndarray, args: argparse.Namespace, rng: random.Random) -> np.ndarray:
    output = np.nan_to_num(features.astype(np.float32, copy=False))
    output = random_crop(output, args.crop_jitter, args.min_frames, rng)

    speed = rng.uniform(args.speed_min, args.speed_max)
    target_len = max(args.min_frames, int(round(len(output) / speed)))
    if args.length_aware_speed:
        min_target_len = max(args.min_target_frames, int(round(len(output) * args.min_target_ratio)))
        target_len = max(target_len, min_target_len)
    output = temporal_resample(output, target_len)

    if args.scale_std > 0:
        output = output * np.float32(rng.gauss(1.0, args.scale_std))

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


def should_augment(row: dict[str, str], focus_labels: set[str]) -> bool:
    if row.get("split") != "train":
        return False
    target_labels = (row.get("target_labels") or "").split()
    if not target_labels:
        return False
    return not focus_labels or any(label in focus_labels for label in target_labels)


def main() -> None:
    args = parse_args()
    if args.copies_per_row < 0:
        raise ValueError("--copies-per-row must be >= 0")
    if args.speed_min <= 0 or args.speed_max <= 0 or args.speed_min > args.speed_max:
        raise ValueError("--speed-min and --speed-max must be positive and ordered")
    if not 0 < args.min_target_ratio <= 1:
        raise ValueError("--min-target-ratio must be in (0, 1].")
    if args.min_target_frames < args.min_frames:
        raise ValueError("--min-target-frames must be >= --min-frames.")

    fieldnames, rows = read_rows(args.manifest)
    if "feature_path" not in fieldnames or "target_labels" not in fieldnames:
        raise ValueError("Manifest must contain feature_path and target_labels columns.")

    rng = random.Random(args.seed)
    focus_labels = {item.strip() for item in args.labels.split(",") if item.strip()}
    output_features = args.output_root / "features_aug"
    output_rows = list(rows)
    added = 0
    skipped = 0

    for row_index, row in enumerate(rows):
        if not should_augment(row, focus_labels):
            continue

        feature_path = Path(row.get("feature_path", "")).expanduser()
        if not feature_path.exists():
            skipped += 1
            continue

        features = np.load(feature_path)
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
            output_row["label_path"] = ""
            output_row["frames"] = str(int(augmented.shape[0]))
            output_row["nonblank_frames"] = str(int(augmented.shape[0]))
            output_rows.append(output_row)
            added += 1

    manifest_path = args.output_root / "dataset_manifest.csv"
    write_rows(manifest_path, fieldnames, output_rows)
    print(f"manifest={manifest_path}")
    print(f"base_rows={len(rows)}")
    print(f"added_rows={added}")
    print(f"skipped_rows={skipped}")
    print(f"total_rows={len(output_rows)}")


if __name__ == "__main__":
    main()
