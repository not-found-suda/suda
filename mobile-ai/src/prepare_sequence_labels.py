from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path

import numpy as np


BLANK_LABEL = "<blank>"
PAD_LABEL_ID = -100


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create frame-level sequence-label arrays from extracted features and AI Hub start/end labels.",
    )
    parser.add_argument("--manifest", type=Path, required=True, help="CSV from build_morpheme_manifest.py.")
    parser.add_argument(
        "--feature-root",
        type=Path,
        required=True,
        help="Directory containing extracted [T, 332] .npy files. Files are matched by video stem.",
    )
    parser.add_argument(
        "--label-manifest",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "config" / "label_manifest_child_mvp.json",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "data" / "sequence_labels_child",
    )
    parser.add_argument("--min-labeled-frames", type=int, default=1)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def load_label_manifest(path: Path) -> tuple[dict[str, int], dict[str, str]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    labels = payload.get("target_labels", [])
    blank_label = payload.get("blank_label", BLANK_LABEL)
    label_to_id = {blank_label: 0}
    for label in labels:
        if label not in label_to_id:
            label_to_id[label] = len(label_to_id)
    canonical = payload.get("canonical_labels", {})
    return label_to_id, canonical


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


def parse_duration(row: dict, feature_path: Path, frame_count: int) -> float:
    duration = row.get("duration", "")
    if duration:
        try:
            value = float(duration)
            if value > 0.0:
                return value
        except ValueError:
            pass

    metadata_path = feature_path.with_suffix(".json")
    if metadata_path.exists():
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        fps = float(metadata.get("fps", 0.0) or 0.0)
        if fps > 0.0:
            return frame_count / fps

    raise ValueError(f"Missing duration for {row.get('video_name') or feature_path.name}")


def load_segments(row: dict) -> list[dict]:
    raw = row.get("segments_json") or "[]"
    segments = json.loads(raw)
    if not isinstance(segments, list):
        raise ValueError("segments_json must be a list")
    return segments


def assign_frame_labels(
    frame_count: int,
    duration: float,
    segments: list[dict],
    label_to_id: dict[str, int],
    canonical: dict[str, str],
) -> np.ndarray:
    labels = np.zeros(frame_count, dtype=np.int64)
    if frame_count <= 0 or duration <= 0.0:
        return labels

    for segment in segments:
        segment_labels = segment.get("labels", [])
        canonical_labels = [canonical.get(label, label) for label in segment_labels]
        known_labels = [label for label in canonical_labels if label in label_to_id and label != BLANK_LABEL]
        if not known_labels:
            continue

        label_id = label_to_id[known_labels[0]]
        start_sec = max(float(segment.get("start", 0.0)), 0.0)
        end_sec = max(float(segment.get("end", 0.0)), start_sec)
        start_index = max(0, min(frame_count, int(math.floor(start_sec / duration * frame_count))))
        end_index = max(start_index, min(frame_count, int(math.ceil(end_sec / duration * frame_count))))
        if end_index > start_index:
            labels[start_index:end_index] = label_id

    return labels


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_dataset_manifest(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
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
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    args = parse_args()
    label_to_id, canonical = load_label_manifest(args.label_manifest)
    feature_index = build_feature_index(args.feature_root.resolve())
    rows = read_rows(args.manifest)

    output_root = args.output_root.resolve()
    labels_dir = output_root / "labels"
    labels_dir.mkdir(parents=True, exist_ok=True)

    dataset_rows: list[dict] = []
    missing = 0
    skipped = 0

    for row in rows:
        video_name = row.get("video_name", "")
        stem = Path(video_name).stem
        feature_path = feature_index.get(stem)
        if not feature_path:
            missing += 1
            continue

        label_path = labels_dir / f"{stem}.npy"
        if label_path.exists() and not args.overwrite:
            frame_labels = np.load(label_path)
            feature = np.load(feature_path, mmap_mode="r")
            duration = parse_duration(row, feature_path, int(feature.shape[0]))
        else:
            feature = np.load(feature_path, mmap_mode="r")
            frame_count = int(feature.shape[0])
            duration = parse_duration(row, feature_path, frame_count)
            frame_labels = assign_frame_labels(
                frame_count=frame_count,
                duration=duration,
                segments=load_segments(row),
                label_to_id=label_to_id,
                canonical=canonical,
            )
            np.save(label_path, frame_labels)

        nonblank_frames = int(np.count_nonzero(frame_labels))
        if nonblank_frames < args.min_labeled_frames:
            skipped += 1
            continue

        dataset_rows.append(
            {
                "split": row.get("split", ""),
                "kind": row.get("kind", ""),
                "video_name": video_name,
                "feature_path": str(feature_path),
                "label_path": str(label_path),
                "frames": int(frame_labels.shape[0]),
                "duration": duration,
                "target_labels": row.get("contains_target", ""),
                "nonblank_frames": nonblank_frames,
            }
        )

    vocab_path = output_root / "vocab.json"
    vocab_payload = {
        "blank_label": BLANK_LABEL,
        "pad_label_id": PAD_LABEL_ID,
        "label_to_id": label_to_id,
        "id_to_label": {str(value): key for key, value in label_to_id.items()},
    }
    vocab_path.write_text(json.dumps(vocab_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    dataset_manifest = output_root / "dataset_manifest.csv"
    write_dataset_manifest(dataset_manifest, dataset_rows)

    print(f"rows={len(rows)}")
    print(f"matched={len(dataset_rows)}")
    print(f"missing_features={missing}")
    print(f"skipped_no_labels={skipped}")
    print(f"dataset_manifest={dataset_manifest}")
    print(f"vocab={vocab_path}")


if __name__ == "__main__":
    main()
