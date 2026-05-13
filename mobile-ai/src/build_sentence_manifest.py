from __future__ import annotations

import argparse
import csv
import json
import re
import zipfile
from pathlib import Path


TARGET_LABELS = ["기차", "장난감", "놀다", "가다", "일어나다", "병원", "조심"]
VIDEO_STEM_PATTERN = re.compile(r"(?P<sentence>NIA_SL_SEN\d+)_REAL(?P<real>\d+)_(?P<view>[A-Z])")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a compact CSV manifest from AI Hub real sentence morpheme zip files.",
    )
    parser.add_argument("--morpheme-zip", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "data" / "sentence_manifest.csv",
    )
    parser.add_argument(
        "--labels",
        default=",".join(TARGET_LABELS),
        help="Comma-separated labels to mark in contains_target. Empty means all labels.",
    )
    parser.add_argument("--only-targets", action="store_true")
    return parser.parse_args()


def load_json_from_zip(zip_file: zipfile.ZipFile, name: str) -> dict:
    return json.loads(zip_file.read(name).decode("utf-8-sig"))


def labels_from_payload(payload: dict) -> list[str]:
    labels: list[str] = []
    for item in payload.get("data", []):
        for attr in item.get("attributes", []):
            label = attr.get("name")
            if label:
                labels.append(label)
    return labels


def segments_from_payload(payload: dict) -> list[dict]:
    segments: list[dict] = []
    for item in payload.get("data", []):
        names = [attr.get("name") for attr in item.get("attributes", []) if attr.get("name")]
        if not names:
            continue
        segments.append(
            {
                "start": float(item.get("start", 0.0)),
                "end": float(item.get("end", 0.0)),
                "labels": names,
            }
        )
    return segments


def parse_video_group(video_name: str) -> tuple[str, str, str]:
    match = VIDEO_STEM_PATTERN.search(Path(video_name).stem)
    if not match:
        return "", "", ""
    return match.group("sentence"), match.group("real"), match.group("view")


def build_rows(zip_path: Path, target_labels: set[str], only_targets: bool) -> list[dict]:
    rows: list[dict] = []
    with zipfile.ZipFile(zip_path) as zip_file:
        json_names = [name for name in zip_file.namelist() if name.endswith(".json")]
        for name in sorted(json_names):
            payload = load_json_from_zip(zip_file, name)
            metadata = payload.get("metaData", {})
            video_name = metadata.get("name", "")
            labels = labels_from_payload(payload)
            contains = sorted(set(labels).intersection(target_labels)) if target_labels else labels
            if only_targets and not contains:
                continue

            sentence_id, real_id, view = parse_video_group(video_name)
            rows.append(
                {
                    "json_path": name,
                    "video_name": video_name,
                    "video_url": metadata.get("url", ""),
                    "sentence_id": sentence_id,
                    "real_id": real_id,
                    "view": view,
                    "duration": metadata.get("duration", ""),
                    "labels": " ".join(labels),
                    "contains_target": " ".join(contains),
                    "segments_json": json.dumps(segments_from_payload(payload), ensure_ascii=False),
                }
            )
    return rows


def write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "json_path",
        "video_name",
        "video_url",
        "sentence_id",
        "real_id",
        "view",
        "duration",
        "labels",
        "contains_target",
        "segments_json",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    args = parse_args()
    target_labels = {label.strip() for label in args.labels.split(",") if label.strip()}
    rows = build_rows(args.morpheme_zip, target_labels, args.only_targets)
    write_csv(args.output, rows)
    print(f"rows={len(rows)}")
    print(f"output={args.output}")


if __name__ == "__main__":
    main()
