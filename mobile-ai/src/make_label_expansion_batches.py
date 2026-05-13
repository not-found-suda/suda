from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


DEFAULT_BASELINE_LABELS = [
    "가능",
    "가다",
    "공항",
    "급하다",
    "기차",
    "맞다",
    "명동",
    "방법",
    "병원",
    "불가능",
    "불량",
    "빨리",
    "사진기",
    "샛길",
    "서울역",
    "알려주다",
    "얼마",
    "없다",
    "에어컨",
    "원하다",
    "지름길",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create cumulative label-expansion batches and a runnable bash "
            "script for SEN window spotter experiments."
        ),
    )
    parser.add_argument("--ranked-labels", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--baseline-labels", default="")
    parser.add_argument("--baseline-label-map", type=Path, default=None)
    parser.add_argument("--batch-size", type=int, default=5)
    parser.add_argument("--max-new-labels", type=int, default=20)
    parser.add_argument("--exclude-new-labels", default="")
    parser.add_argument("--manifest", default="~/Son/mobile-ai/data/mvp_v3_24_sen_rich_manifest.csv")
    parser.add_argument("--feature-root", default="~/Son/mvp-v3-24-rich/features_332")
    parser.add_argument("--experiment-root", default="~/Son/mvp-v3-24-rich")
    parser.add_argument("--artifact-root", default="~/Son/mobile-ai/artifacts")
    parser.add_argument("--log-root", default="~/Son/logs")
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--batch-size-train", type=int, default=8)
    parser.add_argument("--score-threshold", type=float, default=0.99)
    parser.add_argument("--margin-threshold", type=float, default=0.95)
    parser.add_argument("--max-detections", type=int, default=8)
    return parser.parse_args()


def read_ranked_labels(path: Path) -> list[str]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file, delimiter="\t")
        if not reader.fieldnames or "label" not in reader.fieldnames:
            raise ValueError(f"{path} must be a TSV from select_sen_top_labels.py with a label column")
        return [row["label"].strip() for row in reader if row.get("label", "").strip()]


def read_label_map(path: Path) -> list[str]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    labels = payload.get("labels")
    if not isinstance(labels, list):
        raise ValueError(f"{path} does not contain a labels array")
    return [str(label) for label in labels if str(label) != "<background>"]


def parse_label_csv(raw: str) -> list[str]:
    return [label.strip() for label in raw.split(",") if label.strip()]


def unique(labels: list[str]) -> list[str]:
    seen: set[str] = set()
    output: list[str] = []
    for label in labels:
        if label in seen:
            continue
        seen.add(label)
        output.append(label)
    return output


def shell_single_quote(value: str) -> str:
    return "'" + value.replace("'", "'\"'\"'") + "'"


def bash_path_expr(value: str) -> str:
    if value.startswith("~/"):
        return '"$HOME/' + value[2:] + '"'
    return shell_single_quote(value)


def write_run_script(
    path: Path,
    args: argparse.Namespace,
) -> None:
    content = f"""#!/usr/bin/env bash
set -euo pipefail

cd ~/Son
MANIFEST={bash_path_expr(args.manifest)}
FEATURE_ROOT={bash_path_expr(args.feature_root)}
EXPERIMENT_ROOT={bash_path_expr(args.experiment_root)}
ARTIFACT_ROOT={bash_path_expr(args.artifact_root)}
LOG_ROOT={bash_path_expr(args.log_root)}
mkdir -p "$LOG_ROOT" "$EXPERIMENT_ROOT" "$ARTIFACT_ROOT"

SCRIPT_DIR="$(cd "$(dirname "${{BASH_SOURCE[0]}}")" && pwd)"
SUMMARY="$LOG_ROOT/label_expansion_batch_summary.tsv"
printf "batch\\tnew_labels\\tsummary\\n" > "$SUMMARY"

for labels_file in "$SCRIPT_DIR"/batch_*.labels.txt; do
  name="$(basename "$labels_file" .labels.txt)"
  labels="$(cat "$labels_file")"
  new_labels="$(cat "$SCRIPT_DIR/${{name}}.new_labels.txt")"
  output_root="$EXPERIMENT_ROOT/sen_window_spotter_${{name}}_bg3"
  model_path="$ARTIFACT_ROOT/sen_window_spotter_${{name}}_bg3.pt"
  log_path="$LOG_ROOT/scan_sen_window_spotter_${{name}}_bg3_val.log"

  echo "=== $name ==="
  echo "new_labels=$new_labels"

  python mobile-ai/src/build_sen_window_spotter_manifest.py \\
    --manifest "$MANIFEST" \\
    --feature-root "$FEATURE_ROOT" \\
    --output-root "$output_root" \\
    --labels "$labels" \\
    --val-reals 13,14,15,16 \\
    --window-frames 8,12,16,20,24,32,40,52,64 \\
    --stride 4 \\
    --positive-min-coverage 0.60 \\
    --positive-min-purity 0.25 \\
    --negative-ratio 3 \\
    --max-positive-per-segment 8 \\
    --max-negatives-per-video 64 \\
    --overwrite

  python mobile-ai/src/train_sequence_classifier.py \\
    --manifest "$output_root/sen_window_spotter_manifest.csv" \\
    --output "$model_path" \\
    --epochs {args.epochs} \\
    --batch-size {args.batch_size_train}

  python mobile-ai/src/scan_sen_with_segment_classifier.py \\
    --model "$model_path" \\
    --manifest "$MANIFEST" \\
    --feature-root "$FEATURE_ROOT" \\
    --split val \\
    --window-frames 8,12,16,20,24,32,40,52,64 \\
    --stride 4 \\
    --score-threshold {args.score_threshold} \\
    --margin-threshold {args.margin_threshold} \\
    --max-detections {args.max_detections} \\
    > "$log_path"

  summary="$(grep "summary" "$log_path" | tail -n 1)"
  printf "%s\\t%s\\t%s\\n" "$name" "$new_labels" "$summary" | tee -a "$SUMMARY"
  tail -n 25 "$log_path"
done

echo
echo "summary_table=$SUMMARY"
cat "$SUMMARY"
"""
    path.write_text(content, encoding="utf-8", newline="\n")


def main() -> None:
    args = parse_args()
    if args.batch_size <= 0:
        raise ValueError("--batch-size must be positive")
    if args.max_new_labels <= 0:
        raise ValueError("--max-new-labels must be positive")

    if args.baseline_label_map:
        baseline = read_label_map(args.baseline_label_map)
    elif args.baseline_labels:
        baseline = parse_label_csv(args.baseline_labels)
    else:
        baseline = DEFAULT_BASELINE_LABELS
    baseline = unique(baseline)

    excluded_new = set(parse_label_csv(args.exclude_new_labels))
    baseline_set = set(baseline)
    ranked_labels = read_ranked_labels(args.ranked_labels)
    new_candidates = [
        label
        for label in ranked_labels
        if label not in baseline_set and label not in excluded_new and label != "<background>"
    ]
    new_candidates = unique(new_candidates)[: args.max_new_labels]

    args.output_dir.mkdir(parents=True, exist_ok=True)
    batches: list[tuple[str, list[str], list[str]]] = []
    for end in range(args.batch_size, len(new_candidates) + 1, args.batch_size):
        name = f"batch_{end:02d}"
        new_labels = new_candidates[:end]
        labels = unique(baseline + new_labels)
        batches.append((name, labels, new_labels))

    if len(new_candidates) % args.batch_size:
        end = len(new_candidates)
        name = f"batch_{end:02d}"
        new_labels = new_candidates
        labels = unique(baseline + new_labels)
        batches.append((name, labels, new_labels))

    for name, labels, new_labels in batches:
        (args.output_dir / f"{name}.labels.txt").write_text(
            ",".join(labels) + "\n",
            encoding="utf-8",
        )
        (args.output_dir / f"{name}.new_labels.txt").write_text(
            ",".join(new_labels) + "\n",
            encoding="utf-8",
        )

    run_script = args.output_dir / "run_batches.sh"
    write_run_script(run_script, args)

    print(f"baseline_labels={len(baseline)}")
    print(f"new_candidates={len(new_candidates)}")
    print(f"batches={len(batches)}")
    print(f"output_dir={args.output_dir}")
    print(f"run_script={run_script}")
    for name, labels, new_labels in batches:
        print(f"{name}\ttotal={len(labels)}\tnew={','.join(new_labels)}")


if __name__ == "__main__":
    main()
