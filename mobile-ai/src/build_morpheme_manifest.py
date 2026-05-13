from __future__ import annotations

import argparse
import csv
import json
import zipfile
from pathlib import Path


DEFAULT_CHILD_LABELS = [
    "엄마",
    "아기",
    "밥",
    "우유",
    "자다",
    "아프다",
    "병원",
    "놀다",
    "장난감",
    "좋다",
    "싫다",
    "조심",
    "배고프다",
    "졸리다",
    "행복",
    "화나다",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a CSV manifest from AI Hub *_morpheme.zip files.",
    )
    parser.add_argument(
        "--morpheme-root",
        type=Path,
        help="Root directory to scan for *morpheme*.zip* files.",
    )
    parser.add_argument(
        "--morpheme-zip",
        type=Path,
        action="append",
        default=[],
        help="Specific morpheme zip file. Can be passed multiple times.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "data" / "morpheme_manifest.csv",
    )
    parser.add_argument(
        "--labels",
        default=",".join(DEFAULT_CHILD_LABELS),
        help="Comma-separated labels to mark. Empty means keep every labeled sample.",
    )
    parser.add_argument("--only-targets", action="store_true")
    return parser.parse_args()


def split_name(path: Path) -> str:
    text = str(path)
    if "1.Training" in text:
        return "train"
    if "2.Validation" in text:
        return "val"
    return ""


def data_kind(path: Path) -> str:
    text = str(path).upper()
    if "CROWD" in text:
        return "CROWD"
    if "WORD" in text:
        return "WORD"
    if "SEN" in text:
        return "SEN"
    if "SYN" in text:
        return "SYN"
    return ""


def iter_zip_paths(args: argparse.Namespace) -> list[Path]:
    paths = [path.resolve() for path in args.morpheme_zip]
    if args.morpheme_root:
        paths.extend(sorted(args.morpheme_root.resolve().rglob("*morpheme*.zip*")))
    unique: dict[str, Path] = {}
    for path in paths:
        if path.is_file():
            unique[str(path)] = path
    return list(unique.values())


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


def build_rows(zip_paths: list[Path], target_labels: set[str], only_targets: bool) -> list[dict]:
    rows: list[dict] = []
    for zip_path in zip_paths:
        with zipfile.ZipFile(zip_path) as zip_file:
            json_names = sorted(name for name in zip_file.namelist() if name.endswith(".json"))
            for name in json_names:
                payload = json.loads(zip_file.read(name).decode("utf-8-sig"))
                metadata = payload.get("metaData", {})
                labels = labels_from_payload(payload)
                contains = sorted(set(labels).intersection(target_labels)) if target_labels else labels
                if only_targets and not contains:
                    continue

                rows.append(
                    {
                        "split": split_name(zip_path),
                        "kind": data_kind(zip_path),
                        "zip_path": str(zip_path),
                        "json_path": name,
                        "video_name": metadata.get("name", ""),
                        "video_url": metadata.get("url", ""),
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
        "split",
        "kind",
        "zip_path",
        "json_path",
        "video_name",
        "video_url",
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
    zip_paths = iter_zip_paths(args)
    rows = build_rows(zip_paths, target_labels, args.only_targets)
    write_csv(args.output, rows)
    print(f"zips={len(zip_paths)}")
    print(f"rows={len(rows)}")
    print(f"output={args.output}")


if __name__ == "__main__":
    main()
