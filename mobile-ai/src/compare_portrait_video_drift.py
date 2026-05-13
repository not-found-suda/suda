from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch

from extract_videos_332 import DEFAULT_TASK_PATH, VIDEO_EXTENSIONS, extract_video
from scan_sen_with_segment_classifier import collapse_repeats, nms, scan_windows
from train_sequence_classifier import SequenceClassifier


@dataclass(frozen=True)
class ScanResult:
    text: str
    candidates: list


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Compare original landscape videos with portrait-transformed variants. "
            "Reports feature drift and word-spotting prediction changes."
        ),
    )
    parser.add_argument("--original-dir", type=Path, required=True)
    parser.add_argument("--variant-dir", type=Path, required=True)
    parser.add_argument(
        "--original-feature-root",
        type=Path,
        default=None,
        help="Optional pre-extracted .npy feature root for original videos.",
    )
    parser.add_argument(
        "--variant-feature-root",
        type=Path,
        default=None,
        help="Optional pre-extracted .npy feature root for portrait variant videos.",
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--variant-suffix", default="_portrait_crop")
    parser.add_argument("--recursive", action="store_true")
    parser.add_argument("--backend", choices=("tasks", "solutions"), default="solutions")
    parser.add_argument("--task-path", type=Path, default=DEFAULT_TASK_PATH)
    parser.add_argument("--hand-forward-fill", action="store_true")
    parser.add_argument("--window-frames", default="8,12,16,20,24,32,40,52,64")
    parser.add_argument("--stride", type=int, default=4)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--score-threshold", type=float, default=0.99)
    parser.add_argument("--margin-threshold", type=float, default=0.95)
    parser.add_argument("--nms-iou", type=float, default=0.35)
    parser.add_argument("--max-detections", type=int, default=8)
    parser.add_argument("--ignore-labels", default="<background>")
    parser.add_argument("--resample-frames", type=int, default=40)
    parser.add_argument("--checkpoint-key", choices=["model_state", "final_model_state"], default="model_state")
    parser.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    return parser.parse_args()


def parse_windows(raw: str) -> list[int]:
    windows = sorted({int(item.strip()) for item in raw.split(",") if item.strip()})
    if not windows or any(window <= 1 for window in windows):
        raise ValueError("--window-frames must contain integers > 1")
    return windows


def iter_videos(root: Path, recursive: bool) -> list[Path]:
    pattern = "**/*" if recursive else "*"
    return sorted(
        path
        for path in root.glob(pattern)
        if path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS
    )


def build_variant_index(variant_dir: Path, recursive: bool, suffix: str) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in iter_videos(variant_dir, recursive):
        stem = path.stem
        if stem.endswith(suffix):
            index[stem[: -len(suffix)]] = path
    return index


def build_feature_index(feature_root: Path, suffix: str = "") -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path in sorted(feature_root.rglob("*.npy")):
        stem = path.stem
        key = stem[: -len(suffix)] if suffix and stem.endswith(suffix) else stem
        index.setdefault(key, path)
    return index


def load_feature_stats(feature_path: Path) -> dict:
    json_path = feature_path.with_suffix(".json")
    if not json_path.exists():
        features = np.load(feature_path, mmap_mode="r")
        return {
            "frames": int(features.shape[0]),
            "has_hand_frames": -1,
        }
    payload = json.loads(json_path.read_text(encoding="utf-8"))
    return {
        "frames": int(payload.get("frames", 0)),
        "has_hand_frames": int(payload.get("has_hand_frames", -1)),
    }


def load_or_extract_features(
    video_path: Path,
    feature_path: Path | None,
    args: argparse.Namespace,
) -> tuple[np.ndarray, dict]:
    if feature_path is not None:
        features = np.load(feature_path).astype(np.float32, copy=False)
        return features, load_feature_stats(feature_path)

    return extract_video(
        path=video_path,
        hand_forward_fill=args.hand_forward_fill,
        task_path=args.task_path,
        backend=args.backend,
    )


def load_model(args: argparse.Namespace) -> tuple[SequenceClassifier, dict[int, str]]:
    checkpoint = torch.load(args.model, map_location=args.device)
    id_to_label = {int(index): label for index, label in checkpoint["id_to_label"].items()}
    model = SequenceClassifier(
        input_dim=int(checkpoint.get("input_dim", 332)),
        class_count=int(checkpoint.get("class_count", len(id_to_label))),
        hidden_size=int(checkpoint.get("hidden_size", 128)),
        layers=int(checkpoint.get("layers", 2)),
        dropout=float(checkpoint.get("dropout", 0.2)),
    ).to(args.device)
    model.load_state_dict(checkpoint[args.checkpoint_key])
    model.eval()
    return model, id_to_label


def resample_features(features: np.ndarray, target_frames: int) -> np.ndarray:
    if features.shape[0] == target_frames:
        return features.astype(np.float32, copy=False)
    if features.shape[0] <= 1:
        return np.repeat(features[:1], target_frames, axis=0).astype(np.float32, copy=False)

    old = np.arange(features.shape[0], dtype=np.float32)
    new = np.linspace(0, features.shape[0] - 1, target_frames, dtype=np.float32)
    out = np.empty((target_frames, features.shape[1]), dtype=np.float32)
    for dim in range(features.shape[1]):
        out[:, dim] = np.interp(new, old, features[:, dim])
    return out


def feature_distance(original: np.ndarray, variant: np.ndarray, target_frames: int) -> tuple[float, float, float]:
    original_resampled = resample_features(np.nan_to_num(original), target_frames)
    variant_resampled = resample_features(np.nan_to_num(variant), target_frames)
    diff = original_resampled - variant_resampled
    frame_l2 = np.linalg.norm(diff, axis=1)
    return float(frame_l2.mean()), float(np.abs(diff).mean()), float(frame_l2.max())


def scan_feature_sequence(
    model: SequenceClassifier,
    features: np.ndarray,
    id_to_label: dict[int, str],
    args: argparse.Namespace,
    windows: list[int],
) -> ScanResult:
    ignored_labels = {label.strip() for label in args.ignore_labels.split(",") if label.strip()}
    raw_candidates = scan_windows(
        model=model,
        features=np.nan_to_num(features).astype(np.float32, copy=False),
        id_to_label=id_to_label,
        windows=windows,
        stride=args.stride,
        batch_size=args.batch_size,
        device=args.device,
    )
    filtered = [
        candidate
        for candidate in raw_candidates
        if candidate.label not in ignored_labels
        and candidate.score >= args.score_threshold
        and candidate.margin >= args.margin_threshold
    ]
    selected = collapse_repeats(nms(filtered, args.nms_iou))[: args.max_detections]
    text = " ".join(candidate.label for candidate in selected) if selected else "-"
    return ScanResult(text=text, candidates=selected)


def format_candidates(candidates: list) -> str:
    if not candidates:
        return "-"
    return " ".join(
        f"{candidate.label}@{candidate.start}-{candidate.end}:{candidate.score:.3f}"
        for candidate in candidates
    )


def main() -> None:
    args = parse_args()
    windows = parse_windows(args.window_frames)
    model, id_to_label = load_model(args)
    originals = iter_videos(args.original_dir, args.recursive)
    variants = build_variant_index(args.variant_dir, args.recursive, args.variant_suffix)
    original_features = (
        build_feature_index(args.original_feature_root)
        if args.original_feature_root is not None
        else {}
    )
    variant_features = (
        build_feature_index(args.variant_feature_root, suffix=args.variant_suffix)
        if args.variant_feature_root is not None
        else {}
    )

    print(
        "video\torig_pred\tvariant_pred\tsame\tmean_l2\tmean_abs\tmax_l2\t"
        "orig_hands\tvariant_hands\torig_frames\tvariant_frames"
    )
    compared = 0
    changed = 0
    for original_path in originals:
        variant_path = variants.get(original_path.stem)
        if variant_path is None:
            print(f"missing_variant\t{original_path.name}")
            continue

        original_feature_path = original_features.get(original_path.stem)
        variant_feature_path = variant_features.get(original_path.stem)
        if args.original_feature_root is not None and original_feature_path is None:
            print(f"missing_original_feature\t{original_path.name}")
            continue
        if args.variant_feature_root is not None and variant_feature_path is None:
            print(f"missing_variant_feature\t{variant_path.name}")
            continue

        original_sequence, original_stats = load_or_extract_features(
            video_path=original_path,
            feature_path=original_feature_path,
            args=args,
        )
        variant_sequence, variant_stats = load_or_extract_features(
            video_path=variant_path,
            feature_path=variant_feature_path,
            args=args,
        )
        orig_scan = scan_feature_sequence(model, original_sequence, id_to_label, args, windows)
        variant_scan = scan_feature_sequence(model, variant_sequence, id_to_label, args, windows)
        mean_l2, mean_abs, max_l2 = feature_distance(
            original_sequence,
            variant_sequence,
            args.resample_frames,
        )
        same = orig_scan.text == variant_scan.text
        compared += 1
        changed += int(not same)
        print(
            f"{original_path.name}\t{orig_scan.text}\t{variant_scan.text}\t{same}\t"
            f"{mean_l2:.4f}\t{mean_abs:.4f}\t{max_l2:.4f}\t"
            f"{original_stats['has_hand_frames']}/{original_stats['frames']}\t"
            f"{variant_stats['has_hand_frames']}/{variant_stats['frames']}\t"
            f"{original_stats['frames']}\t{variant_stats['frames']}"
        )
        print(f"  orig_hits\t{format_candidates(orig_scan.candidates)}")
        print(f"  variant_hits\t{format_candidates(variant_scan.candidates)}")

    print(f"summary compared={compared} changed={changed} change_rate={changed / max(compared, 1):.4f}")


if __name__ == "__main__":
    main()
