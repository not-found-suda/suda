from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path

import numpy as np


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create a CTC manifest whose feature_path entries point to x/dx/ddx delta features.",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-manifest", type=Path, required=True)
    parser.add_argument("--output-feature-root", type=Path, required=True)
    parser.add_argument(
        "--order",
        type=int,
        choices=[1, 2],
        default=2,
        help="1 writes [x, dx]. 2 writes [x, dx, ddx].",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Regenerate feature files even if they already exist.",
    )
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


def delta_features(x: np.ndarray, order: int) -> np.ndarray:
    x = x.astype(np.float32, copy=False)
    dx = np.zeros_like(x, dtype=np.float32)
    if x.shape[0] > 1:
        dx[1:] = x[1:] - x[:-1]

    if order == 1:
        return np.concatenate([x, dx], axis=1).astype(np.float32, copy=False)

    ddx = np.zeros_like(x, dtype=np.float32)
    if x.shape[0] > 1:
        ddx[1:] = dx[1:] - dx[:-1]
    return np.concatenate([x, dx, ddx], axis=1).astype(np.float32, copy=False)


def output_path_for(source: Path, output_root: Path) -> Path:
    resolved = str(source.expanduser().resolve())
    digest = hashlib.sha1(resolved.encode("utf-8")).hexdigest()[:12]
    return output_root / digest / source.name


def main() -> None:
    args = parse_args()
    fieldnames, rows = read_rows(args.manifest)
    if "feature_path" not in fieldnames:
        raise ValueError("Manifest must contain a feature_path column.")

    converted: dict[str, Path] = {}
    generated = 0
    reused = 0
    missing = 0

    for row in rows:
        feature_path = row.get("feature_path", "")
        if not feature_path:
            continue

        source = Path(feature_path).expanduser()
        if not source.exists():
            missing += 1
            continue

        key = str(source.resolve())
        target = converted.get(key)
        if target is None:
            target = output_path_for(source, args.output_feature_root)
            converted[key] = target
            if args.overwrite or not target.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                x = np.load(source)
                np.save(target, delta_features(x, args.order))
                generated += 1
            else:
                reused += 1

        row["feature_path"] = str(target)

    write_rows(args.output_manifest, fieldnames, rows)
    print(f"manifest={args.output_manifest}")
    print(f"feature_root={args.output_feature_root}")
    print(f"rows={len(rows)} unique_features={len(converted)} generated={generated} reused={reused} missing={missing}")
    print(f"feature_dim_multiplier={args.order + 1}")


if __name__ == "__main__":
    main()
