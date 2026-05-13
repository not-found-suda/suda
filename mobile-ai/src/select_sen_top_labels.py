from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Rank SEN segment labels by support and emit a comma-separated label "
            "list for word-spotter manifest expansion."
        ),
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument(
        "--feature-root",
        type=Path,
        default=None,
        help="Optional feature root. When provided, only videos with .npy features are counted.",
    )
    parser.add_argument("--kind", default="SEN")
    parser.add_argument("--val-reals", default="13,14,15,16")
    parser.add_argument("--top-k", type=int, default=50)
    parser.add_argument("--min-total-segments", type=int, default=1)
    parser.add_argument("--min-train-segments", type=int, default=1)
    parser.add_argument("--min-val-segments", type=int, default=1)
    parser.add_argument("--exclude-labels", default="<background>")
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--labels-output", type=Path, default=None)
    parser.add_argument(
        "--print-labels-only",
        action="store_true",
        help="Print only the selected comma-separated labels for shell substitution.",
    )
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def build_feature_index(feature_root: Path | None) -> set[str] | None:
    if feature_root is None:
        return None
    return {path.stem for path in feature_root.rglob("*.npy")}


def real_id(video_name: str) -> str:
    match = re.search(r"_REAL(\d+)_", video_name)
    return match.group(1) if match else ""


def load_segments(row: dict) -> list[dict]:
    raw = row.get("segments_json") or "[]"
    try:
        segments = json.loads(raw)
    except json.JSONDecodeError:
        return []
    return segments if isinstance(segments, list) else []


def segment_labels(segment: dict) -> list[str]:
    labels = segment.get("labels")
    if isinstance(labels, list):
        return [str(label) for label in labels if str(label)]

    # Some source JSONs store labels under attributes.
    attributes = segment.get("attributes")
    if isinstance(attributes, list):
        output: list[str] = []
        for attribute in attributes:
            if isinstance(attribute, dict) and attribute.get("name"):
                output.append(str(attribute["name"]))
        return output

    label = segment.get("label")
    return [str(label)] if label else []


def split_name(video_name: str, val_reals: set[str]) -> str:
    return "val" if real_id(video_name) in val_reals else "train"


def main() -> None:
    args = parse_args()
    val_reals = {item.strip() for item in args.val_reals.split(",") if item.strip()}
    excluded = {item.strip() for item in args.exclude_labels.split(",") if item.strip()}
    feature_index = build_feature_index(args.feature_root)

    segment_counts: Counter[str] = Counter()
    train_segment_counts: Counter[str] = Counter()
    val_segment_counts: Counter[str] = Counter()
    video_sets: dict[str, set[str]] = defaultdict(set)
    train_video_sets: dict[str, set[str]] = defaultdict(set)
    val_video_sets: dict[str, set[str]] = defaultdict(set)
    stats: Counter[str] = Counter()

    for row in read_rows(args.manifest):
        if row.get("kind") != args.kind:
            stats["skipped_kind"] += 1
            continue

        video_name = row.get("video_name", "")
        if not video_name:
            stats["missing_video_name"] += 1
            continue
        if feature_index is not None and Path(video_name).stem not in feature_index:
            stats["missing_feature"] += 1
            continue

        split = split_name(video_name, val_reals)
        seen_in_video: set[str] = set()
        for segment in load_segments(row):
            labels = [label for label in segment_labels(segment) if label not in excluded]
            if not labels:
                stats["segments_without_label"] += 1
                continue
            for label in labels:
                segment_counts[label] += 1
                seen_in_video.add(label)
                if split == "val":
                    val_segment_counts[label] += 1
                else:
                    train_segment_counts[label] += 1

        for label in seen_in_video:
            video_sets[label].add(video_name)
            if split == "val":
                val_video_sets[label].add(video_name)
            else:
                train_video_sets[label].add(video_name)

    ranked_rows: list[dict[str, str | int]] = []
    for label, total_segments in segment_counts.items():
        train_segments = train_segment_counts[label]
        val_segments = val_segment_counts[label]
        if total_segments < args.min_total_segments:
            continue
        if train_segments < args.min_train_segments:
            continue
        if val_segments < args.min_val_segments:
            continue
        ranked_rows.append(
            {
                "rank": 0,
                "label": label,
                "total_segments": total_segments,
                "train_segments": train_segments,
                "val_segments": val_segments,
                "total_videos": len(video_sets[label]),
                "train_videos": len(train_video_sets[label]),
                "val_videos": len(val_video_sets[label]),
            },
        )

    ranked_rows.sort(
        key=lambda row: (
            -int(row["total_segments"]),
            -int(row["val_segments"]),
            -int(row["train_segments"]),
            str(row["label"]),
        ),
    )
    selected_rows = ranked_rows[: args.top_k]
    for index, row in enumerate(ranked_rows, start=1):
        row["rank"] = index

    labels = [str(row["label"]) for row in selected_rows]
    labels_csv = ",".join(labels)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        fieldnames = [
            "rank",
            "label",
            "total_segments",
            "train_segments",
            "val_segments",
            "total_videos",
            "train_videos",
            "val_videos",
        ]
        with args.output.open("w", encoding="utf-8-sig", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=fieldnames, delimiter="\t")
            writer.writeheader()
            writer.writerows(ranked_rows)

    if args.labels_output:
        args.labels_output.parent.mkdir(parents=True, exist_ok=True)
        args.labels_output.write_text(labels_csv + "\n", encoding="utf-8")

    if args.print_labels_only:
        print(labels_csv)
        return

    print(f"eligible_labels={len(ranked_rows)}")
    print(f"selected_labels={len(labels)}")
    print(f"labels_csv={labels_csv}")
    print("stats", dict(stats))
    print()
    print("top_labels")
    for row in selected_rows:
        print(
            "\t".join(
                str(row[key])
                for key in [
                    "rank",
                    "label",
                    "total_segments",
                    "train_segments",
                    "val_segments",
                    "total_videos",
                    "train_videos",
                    "val_videos",
                ]
            ),
        )


if __name__ == "__main__":
    main()
