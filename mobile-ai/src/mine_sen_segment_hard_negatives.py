from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter
from pathlib import Path

import numpy as np
import torch

from scan_sen_with_segment_classifier import Candidate, nms, parse_windows, scan_windows
from train_sequence_classifier import SequenceClassifier


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
        description=(
            "Mine high-confidence false-positive SEN windows and append them as "
            "<background> hard negatives for the segment word classifier."
        ),
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--feature-root", type=Path, required=True)
    parser.add_argument("--base-manifest", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--labels", default=",".join(DEFAULT_LABELS))
    parser.add_argument("--split", default="train")
    parser.add_argument("--kind", default="SEN")
    parser.add_argument("--window-frames", default="8,12,16,20,24,32,40,52,64")
    parser.add_argument("--stride", type=int, default=4)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--score-threshold", type=float, default=0.90)
    parser.add_argument("--margin-threshold", type=float, default=0.60)
    parser.add_argument(
        "--max-any-label-iou",
        type=float,
        default=0.10,
        help="Keep only candidates with IoU <= this against every true target segment.",
    )
    parser.add_argument("--nms-iou", type=float, default=0.25)
    parser.add_argument("--max-per-video", type=int, default=8)
    parser.add_argument("--max-total", type=int, default=0)
    parser.add_argument("--negative-label", default="<background>")
    parser.add_argument("--checkpoint-key", choices=["model_state", "final_model_state"], default="model_state")
    parser.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_manifest(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
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
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fieldnames})


def build_feature_index(feature_root: Path) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        index.setdefault(path.stem, path)
    return index


def load_segments(row: dict) -> list[dict]:
    raw = row.get("segments_json") or "[]"
    try:
        segments = json.loads(raw)
    except json.JSONDecodeError:
        return []
    return segments if isinstance(segments, list) else []


def parse_duration(row: dict, feature_path: Path, frame_count: int) -> float:
    raw = row.get("duration", "")
    if raw:
        try:
            duration = float(raw)
            if duration > 0.0:
                return duration
        except ValueError:
            pass

    metadata_path = feature_path.with_suffix(".json")
    if metadata_path.exists():
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        fps = float(metadata.get("fps", 0.0) or 0.0)
        if fps > 0.0:
            return frame_count / fps

    return float(frame_count)


def segment_frame_bounds(segment: dict, duration: float, frame_count: int) -> tuple[int, int]:
    start_sec = max(float(segment.get("start", 0.0)), 0.0)
    end_sec = max(float(segment.get("end", 0.0)), start_sec)
    if duration <= 0.0:
        duration = float(frame_count)
    start = max(0, min(frame_count, int(math.floor(start_sec / duration * frame_count))))
    end = max(start, min(frame_count, int(math.ceil(end_sec / duration * frame_count))))
    return start, end


def interval_iou(a: tuple[int, int], b: tuple[int, int]) -> float:
    overlap = max(0, min(a[1], b[1]) - max(a[0], b[0]))
    union = max(a[1], b[1]) - min(a[0], b[0])
    return overlap / union if union > 0 else 0.0


def target_bounds(row: dict, allowed: set[str], feature_path: Path, frame_count: int) -> list[tuple[int, int, str]]:
    duration = parse_duration(row, feature_path, frame_count)
    bounds: list[tuple[int, int, str]] = []
    for segment in load_segments(row):
        labels = [label for label in segment.get("labels", []) if label in allowed]
        if len(labels) != 1:
            continue
        start, end = segment_frame_bounds(segment, duration, frame_count)
        if end > start:
            bounds.append((start, end, labels[0]))
    return bounds


def hard_negative_path(output_root: Path, video_name: str, index: int, start: int, end: int) -> Path:
    stem = Path(video_name).stem
    return output_root / "features" / f"{stem}_hardneg{index:03d}_{start}_{end}.npy"


def load_model(path: Path, checkpoint_key: str, device: str) -> tuple[SequenceClassifier, dict[int, str]]:
    checkpoint = torch.load(path, map_location=device)
    id_to_label = {int(index): label for index, label in checkpoint["id_to_label"].items()}
    model = SequenceClassifier(
        input_dim=int(checkpoint.get("input_dim", 332)),
        class_count=int(checkpoint.get("class_count", len(id_to_label))),
        hidden_size=int(checkpoint.get("hidden_size", 128)),
        layers=int(checkpoint.get("layers", 2)),
        dropout=float(checkpoint.get("dropout", 0.2)),
    ).to(device)
    model.load_state_dict(checkpoint[checkpoint_key])
    return model, id_to_label


def is_true_segment_overlap(
    candidate: Candidate,
    bounds: list[tuple[int, int, str]],
    max_iou: float,
) -> bool:
    interval = (candidate.start, candidate.end)
    return any(interval_iou(interval, (start, end)) > max_iou for start, end, _ in bounds)


def main() -> None:
    args = parse_args()
    allowed = {label.strip() for label in args.labels.split(",") if label.strip()}
    windows = parse_windows(args.window_frames)
    source_rows = read_rows(args.source_manifest)
    base_rows = read_rows(args.base_manifest)
    feature_index = build_feature_index(args.feature_root)
    model, id_to_label = load_model(args.model, args.checkpoint_key, args.device)

    source_rows = [row for row in source_rows if row.get("split") == args.split]
    source_rows = [row for row in source_rows if row.get("kind") == args.kind]

    output_rows = list(base_rows)
    hard_negative_rows: list[dict] = []
    stats: Counter[str] = Counter()
    args.output_root.joinpath("features").mkdir(parents=True, exist_ok=True)

    for row in source_rows:
        if args.max_total and len(hard_negative_rows) >= args.max_total:
            break

        video_name = row.get("video_name", "")
        feature_path = feature_index.get(Path(video_name).stem)
        if not feature_path:
            stats["missing_features"] += 1
            continue

        features = np.load(feature_path).astype(np.float32, copy=False)
        if features.ndim != 2 or features.shape[0] == 0:
            stats["bad_features"] += 1
            continue

        bounds = target_bounds(row, allowed, feature_path, int(features.shape[0]))
        if not bounds:
            stats["no_target_segments"] += 1
            continue

        raw_candidates = scan_windows(
            model=model,
            features=features,
            id_to_label=id_to_label,
            windows=windows,
            stride=args.stride,
            batch_size=args.batch_size,
            device=args.device,
        )
        candidates = [
            candidate
            for candidate in raw_candidates
            if candidate.label != args.negative_label
            and candidate.score >= args.score_threshold
            and candidate.margin >= args.margin_threshold
            and not is_true_segment_overlap(candidate, bounds, args.max_any_label_iou)
        ]
        selected = nms(candidates, args.nms_iou)[: args.max_per_video]
        stats["scanned_videos"] += 1
        stats["raw_candidates"] += len(raw_candidates)
        stats["kept_candidates"] += len(selected)

        for index, candidate in enumerate(selected):
            if args.max_total and len(hard_negative_rows) >= args.max_total:
                break
            crop = np.nan_to_num(features[candidate.start : candidate.end]).astype(np.float32, copy=False)
            out_path = hard_negative_path(args.output_root, video_name, index, candidate.start, candidate.end)
            if args.overwrite or not out_path.exists():
                np.save(out_path, crop)
            hard_negative_rows.append(
                {
                    "split": args.split,
                    "kind": f"{args.kind}_HARD_NEG",
                    "video_name": (
                        f"{video_name}:hardneg{index:03d}:"
                        f"{candidate.start}-{candidate.end}:"
                        f"fp={candidate.label}:{candidate.score:.3f}"
                    ),
                    "feature_path": str(out_path),
                    "label": args.negative_label,
                    "frames": int(crop.shape[0]),
                    "source_video": video_name,
                    "segment_index": f"hardneg{index}",
                    "segment_start": candidate.start,
                    "segment_end": candidate.end,
                }
            )

    output_rows.extend(hard_negative_rows)
    manifest_path = args.output_root / "sen_segment_classifier_manifest.csv"
    write_manifest(manifest_path, output_rows)

    print(f"manifest={manifest_path}")
    print(f"base_rows={len(base_rows)}")
    print(f"hard_negative_rows={len(hard_negative_rows)}")
    print(f"rows={len(output_rows)}")
    print("split", dict(Counter(row.get("split", "") for row in output_rows)))
    print("kind", dict(Counter(row.get("kind", "") for row in output_rows)))
    print("label_counts")
    for label, count in Counter(row.get("label", "") for row in output_rows).most_common():
        print(f"{label}\t{count}")
    print("stats", dict(stats))


if __name__ == "__main__":
    main()
