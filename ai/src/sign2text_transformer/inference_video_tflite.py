import argparse
import json
import os
from collections import deque
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from inference_realtime_tflite import (
    AMBIGUITY_MARGIN_THRESHOLD,
    CONFIDENCE_THRESHOLD,
    DEFAULT_MODEL_DIR,
    DEFAULT_TFLITE_KIND,
    INPUT_DIM,
    MAX_LEN,
    MIN_FRAMES_FOR_PREDICTION,
    TFLiteSignClassifier,
    extract_keypoints,
    find_latest_tflite_pair,
    format_topk,
    has_visible_hand,
    load_label_map,
    pad_sequence,
    resolve_from_base,
)


SEGMENT_END_NO_HANDS_SECONDS = float(os.environ.get("V5_VIDEO_SEGMENT_END_SECONDS", "0.5"))
SLIDING_STRIDE = int(os.environ.get("V5_VIDEO_SLIDING_STRIDE", "5"))
MIN_WORD_WINDOWS = int(os.environ.get("V5_VIDEO_MIN_WORD_WINDOWS", "3"))
IGNORE_EDGE_WINDOWS = int(os.environ.get("V5_VIDEO_IGNORE_EDGE_WINDOWS", "1"))
SPLIT_ON_PAUSE = os.environ.get("V5_VIDEO_SPLIT_ON_PAUSE", "1") == "1"
PAUSE_SECONDS = float(os.environ.get("V5_VIDEO_PAUSE_SECONDS", "0.35"))
PAUSE_MOTION_THRESHOLD = float(os.environ.get("V5_VIDEO_PAUSE_MOTION_THRESHOLD", "0.018"))
HAND_FEATURE_DIM = 126


def parse_args():
    parser = argparse.ArgumentParser(
        description="Run V5 TFLite inference on recorded video files."
    )
    parser.add_argument("videos", nargs="+", help="Video path(s) recorded from phone/camera")
    parser.add_argument(
        "--model-dir",
        default=str(DEFAULT_MODEL_DIR),
        help="Model directory under Transformer_v5_Impl/models",
    )
    parser.add_argument("--kind", choices=("float16", "float32"), default=DEFAULT_TFLITE_KIND)
    parser.add_argument("--model", default="", help="Explicit .tflite path")
    parser.add_argument("--label-map", default="", help="Explicit label_map_v5_*.json path")
    parser.add_argument(
        "--min-frames",
        type=int,
        default=MIN_FRAMES_FOR_PREDICTION,
        help="Minimum visible-hand frames required for a sign segment",
    )
    parser.add_argument(
        "--segment-end-seconds",
        type=float,
        default=SEGMENT_END_NO_HANDS_SECONDS,
        help="Finish a segment after both/no hands are absent for this many seconds",
    )
    parser.add_argument(
        "--stride",
        type=int,
        default=SLIDING_STRIDE,
        help="Sliding-window stride for long segments",
    )
    parser.add_argument(
        "--min-word-windows",
        type=int,
        default=MIN_WORD_WINDOWS,
        help="Keep a word only if it appears in at least this many consecutive stable windows",
    )
    parser.add_argument(
        "--ignore-edge-windows",
        type=int,
        default=IGNORE_EDGE_WINDOWS,
        help="Ignore this many sliding windows at the start/end of each segment",
    )
    parser.add_argument(
        "--split-on-pause",
        action=argparse.BooleanOptionalAction,
        default=SPLIT_ON_PAUSE,
        help="Split a sentence video into word segments when hands pause inside the frame",
    )
    parser.add_argument(
        "--pause-seconds",
        type=float,
        default=PAUSE_SECONDS,
        help="Low-motion duration used as a word boundary when --split-on-pause is enabled",
    )
    parser.add_argument(
        "--pause-motion-threshold",
        type=float,
        default=PAUSE_MOTION_THRESHOLD,
        help="Average hand-landmark motion below this value is treated as a pause",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print detailed per-segment Top-3 and raw window predictions",
    )
    parser.add_argument(
        "--debug-timestamps-ms",
        default="",
        help=(
            "Comma-separated timestamps in milliseconds. "
            "Print feature slices and top1/top2 predictions near those frames."
        ),
    )
    return parser.parse_args()


def resolve_model_files(args):
    if args.model and args.label_map:
        return resolve_from_base(args.model), resolve_from_base(args.label_map)
    return find_latest_tflite_pair(args.model_dir, args.kind)


def hand_motion(prev_keypoints, keypoints):
    if prev_keypoints is None:
        return float("inf")

    prev_hands = prev_keypoints[:HAND_FEATURE_DIM]
    hands = keypoints[:HAND_FEATURE_DIM]
    return float(np.linalg.norm(hands - prev_hands) / np.sqrt(HAND_FEATURE_DIM))


def append_segment(segments, segment, min_frames):
    if len(segment) >= min_frames:
        segments.append(np.asarray(segment, dtype=np.float32))


def collect_segments(
    video_path,
    min_frames,
    segment_end_seconds,
    split_on_pause=True,
    pause_seconds=PAUSE_SECONDS,
    pause_motion_threshold=PAUSE_MOTION_THRESHOLD,
):
    video_path = Path(video_path)
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Could not open video: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS)
    if not fps or fps <= 1e-6:
        fps = 30.0
    end_gap_frames = max(1, int(round(fps * segment_end_seconds)))
    pause_frames = max(1, int(round(fps * pause_seconds)))

    segments = []
    current_segment = []
    no_hand_count = 0
    still_count = 0
    frame_count = 0
    visible_frame_count = 0
    last_pose = None
    last_face = None
    prev_keypoints = None
    waiting_for_motion = False

    mp_holistic = mp.solutions.holistic
    with mp_holistic.Holistic(
        static_image_mode=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while True:
            success, frame = cap.read()
            if not success:
                break

            frame_count += 1
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(image_rgb)

            if has_visible_hand(results):
                keypoints, last_pose, last_face = extract_keypoints(results, last_pose, last_face)
                motion = hand_motion(prev_keypoints, keypoints)
                prev_keypoints = keypoints
                visible_frame_count += 1
                no_hand_count = 0

                if waiting_for_motion and motion <= pause_motion_threshold:
                    continue

                waiting_for_motion = False
                current_segment.append(keypoints)

                if split_on_pause and len(current_segment) >= min_frames:
                    if motion <= pause_motion_threshold:
                        still_count += 1
                    else:
                        still_count = 0

                    if still_count >= pause_frames:
                        trimmed_segment = current_segment[:-still_count]
                        append_segment(
                            segments,
                            trimmed_segment if trimmed_segment else current_segment,
                            min_frames,
                        )
                        current_segment = []
                        still_count = 0
                        waiting_for_motion = True
            else:
                no_hand_count += 1
                if current_segment and no_hand_count >= end_gap_frames:
                    append_segment(segments, current_segment, min_frames)
                    current_segment = []
                    still_count = 0
                    waiting_for_motion = False
                    last_pose = None
                    last_face = None
                    prev_keypoints = None

    cap.release()

    if current_segment:
        append_segment(segments, current_segment, min_frames)

    return {
        "video": str(video_path),
        "fps": fps,
        "frames": frame_count,
        "visible_frames": visible_frame_count,
        "segments": segments,
        "split_on_pause": split_on_pause,
        "pause_frames": pause_frames,
        "pause_motion_threshold": pause_motion_threshold,
    }


def predict_segment(classifier, segment, stride):
    full_input, _ = pad_sequence(segment)
    probs_list = [classifier.predict(full_input)]

    if len(segment) > MAX_LEN:
        stride = max(1, stride)
        for start in range(0, len(segment) - MAX_LEN + 1, stride):
            window = segment[start : start + MAX_LEN]
            window_input, _ = pad_sequence(window)
            probs_list.append(classifier.predict(window_input))

        if (len(segment) - MAX_LEN) % stride != 0:
            window = segment[-MAX_LEN:]
            window_input, _ = pad_sequence(window)
            probs_list.append(classifier.predict(window_input))

    probs = np.mean(np.stack(probs_list, axis=0), axis=0)
    probs = probs / max(float(np.sum(probs)), 1e-8)
    return probs, len(probs_list)


def iter_segment_windows(segment, stride):
    if len(segment) <= MAX_LEN:
        yield 0, len(segment), segment
        return

    stride = max(1, stride)
    last_start = len(segment) - MAX_LEN
    starts = list(range(0, last_start + 1, stride))
    if starts[-1] != last_start:
        starts.append(last_start)

    for start in starts:
        end = start + MAX_LEN
        yield start, end, segment[start:end]


def summarize_prediction(probs, label_map):
    top_indices = np.argsort(probs)[::-1]
    top_idx = int(top_indices[0])
    second_idx = int(top_indices[1]) if len(top_indices) > 1 else top_idx
    label = label_map[top_idx]
    second_label = label_map[second_idx]
    confidence = float(probs[top_idx])
    second_confidence = float(probs[second_idx])
    margin = confidence - second_confidence
    is_stable = confidence >= CONFIDENCE_THRESHOLD and margin >= AMBIGUITY_MARGIN_THRESHOLD
    return {
        "label": label,
        "confidence": confidence,
        "top2Label": second_label,
        "top2Confidence": second_confidence,
        "margin": margin,
        "top3": format_topk(probs, label_map),
        "stable": is_stable,
    }


def format_prediction_row(summary, confirmed_word=""):
    confirmed = confirmed_word or "-"
    return (
        f"{summary['label']:<8} "
        f"{summary['confidence']:.3f} "
        f"{summary['top2Label']:<8} "
        f"{summary['top2Confidence']:.3f} "
        f"{summary['margin']:.3f} "
        f"{confirmed}"
    )


def print_prediction_table(title, rows):
    if not rows:
        return

    print(title)
    print("top1     top1_conf top2     top2_conf margin confirmed")
    for row in rows:
        print(row)


def parse_timestamps_ms(value):
    if not value:
        return []

    timestamps = []
    for token in value.split(","):
        token = token.strip()
        if not token:
            continue
        timestamps.append(float(token))
    return timestamps


def format_slice(values):
    return "[" + ",".join(f"{float(value):.5f}" for value in values) + "]"


def update_confirmation_state(summary, state, min_word_windows):
    if summary is None or not summary["stable"]:
        state["current_label"] = None
        state["current_count"] = 0
        return ""

    if summary["label"] == state["current_label"]:
        state["current_count"] += 1
    else:
        state["current_label"] = summary["label"]
        state["current_count"] = 1

    if (
        state["current_count"] >= min_word_windows
        and state["current_label"] != state["last_confirmed_label"]
    ):
        state["last_confirmed_label"] = state["current_label"]
        return state["current_label"]

    return ""


def debug_video_timestamps(video_path, classifier, label_map, timestamps_ms, args):
    video_path = Path(video_path)
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Could not open video: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS)
    if not fps or fps <= 1e-6:
        fps = 30.0

    target_frames = {
        max(1, int(round(timestamp / 1000.0 * fps)) + 1): timestamp
        for timestamp in timestamps_ms
    }
    records = {}

    frame_window = deque(maxlen=MAX_LEN)
    last_pose = None
    last_face = None
    state = {
        "current_label": None,
        "current_count": 0,
        "last_confirmed_label": None,
    }

    frame_index = 0
    mp_holistic = mp.solutions.holistic
    with mp_holistic.Holistic(
        static_image_mode=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while True:
            success, frame = cap.read()
            if not success:
                break

            frame_index += 1
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(image_rgb)

            feature = None
            summary = None
            if has_visible_hand(results):
                feature, last_pose, last_face = extract_keypoints(results, last_pose, last_face)
                frame_window.append(feature)
                input_array, _ = pad_sequence(frame_window)
                probs = classifier.predict(input_array)
                summary = summarize_prediction(probs, label_map)
            else:
                frame_window.clear()
                last_pose = None
                last_face = None

            confirmed_word = update_confirmation_state(summary, state, args.min_word_windows)

            if frame_index in target_frames:
                records[target_frames[frame_index]] = {
                    "frame": frame_index,
                    "actualMs": (frame_index - 1) / fps * 1000.0,
                    "seq": len(frame_window),
                    "feature": feature,
                    "summary": summary,
                    "confirmed": confirmed_word,
                }

    cap.release()

    print(f"\n[Timestamp Debug] {video_path.name}")
    print(f"- fps: {fps:.3f}")
    print(f"- requested ms: {[int(x) if float(x).is_integer() else x for x in timestamps_ms]}")

    for timestamp in timestamps_ms:
        record = records.get(timestamp)
        if record is None:
            print(f"\n# requested={timestamp:.0f}ms")
            print("- no frame was processed for this timestamp")
            continue

        feature = record["feature"]
        summary = record["summary"]
        print(f"\n# requested={timestamp:.0f}ms nearest_frame={record['frame']} actual={record['actualMs']:.1f}ms seq={record['seq']}/{MAX_LEN}")

        if feature is None:
            print("feature: none (no visible hand)")
        else:
            print(f"feature[0:10]={format_slice(feature[0:10])}")
            print(f"feature[126:136]={format_slice(feature[126:136])}")
            print(f"feature[261:271]={format_slice(feature[261:271])}")
            print(f"feature[282:292]={format_slice(feature[282:292])}")

        if summary is None:
            print("top1: none")
            print("top2: none")
            print("margin: none")
        else:
            label_to_idx = {label: idx for idx, label in label_map.items()}
            print(
                f"top1: index={label_to_idx[summary['label']]} "
                f"label={summary['label']} confidence={summary['confidence']:.4f}"
            )
            print(
                f"top2: label={summary['top2Label']} "
                f"confidence={summary['top2Confidence']:.4f}"
            )
            print(f"margin: {summary['margin']:.4f}")

        print(f"confirmed: {record['confirmed'] or '-'}")

    print()


def clean_word_sequence(words):
    adjacent_deduped = []
    for word in words:
        if not adjacent_deduped or adjacent_deduped[-1] != word:
            adjacent_deduped.append(word)

    # Remove short A-B-A noise, then dedupe once more.
    changed = True
    cleaned = adjacent_deduped
    while changed and len(cleaned) >= 3:
        changed = False
        compact = []
        idx = 0
        while idx < len(cleaned):
            if idx + 2 < len(cleaned) and cleaned[idx] == cleaned[idx + 2]:
                compact.append(cleaned[idx])
                idx += 3
                changed = True
            else:
                compact.append(cleaned[idx])
                idx += 1
        cleaned = compact

    final_words = []
    for word in cleaned:
        if not final_words or final_words[-1] != word:
            final_words.append(word)
    return final_words


def stable_runs_from_windows(window_results, min_word_windows):
    runs = []
    current = None

    for result in window_results:
        label = result["label"] if result["stable"] else None
        if label is None:
            if current is not None:
                runs.append(current)
                current = None
            continue

        if current is not None and current["label"] == label:
            current["count"] += 1
            current["endFrame"] = result["endFrame"]
            current["confidences"].append(result["confidence"])
            current["margins"].append(result["margin"])
        else:
            if current is not None:
                runs.append(current)
            current = {
                "label": label,
                "count": 1,
                "startFrame": result["startFrame"],
                "endFrame": result["endFrame"],
                "confidences": [result["confidence"]],
                "margins": [result["margin"]],
            }

    if current is not None:
        runs.append(current)

    accepted_runs = []
    for run in runs:
        if run["count"] < min_word_windows:
            continue
        accepted_runs.append(
            {
                "label": run["label"],
                "count": int(run["count"]),
                "startFrame": int(run["startFrame"]),
                "endFrame": int(run["endFrame"]),
                "meanConfidence": float(np.mean(run["confidences"])),
                "meanMargin": float(np.mean(run["margins"])),
            }
        )

    return runs, accepted_runs


def confirm_words_from_windows(window_results, min_word_windows):
    raw_words = []
    rows = []
    current_label = None
    current_count = 0
    last_confirmed_label = None

    for summary in window_results:
        confirmed_word = ""
        if summary["stable"]:
            if summary["label"] == current_label:
                current_count += 1
            else:
                current_label = summary["label"]
                current_count = 1

            if current_count >= min_word_windows and current_label != last_confirmed_label:
                confirmed_word = current_label
                raw_words.append(confirmed_word)
                last_confirmed_label = current_label
        else:
            current_label = None
            current_count = 0

        rows.append(format_prediction_row(summary, confirmed_word))

    return raw_words, rows


def predict_segment_words(classifier, segment, label_map, stride, min_word_windows, ignore_edge_windows):
    window_results = []

    for start, end, window in iter_segment_windows(segment, stride):
        input_array, _ = pad_sequence(window)
        probs = classifier.predict(input_array)
        summary = summarize_prediction(probs, label_map)
        summary["startFrame"] = int(start)
        summary["endFrame"] = int(end)
        window_results.append(summary)

    ignored_edge_windows = 0
    filtered_window_results = window_results
    if ignore_edge_windows > 0 and len(window_results) > ignore_edge_windows * 2:
        filtered_window_results = window_results[ignore_edge_windows:-ignore_edge_windows]
        ignored_edge_windows = ignore_edge_windows * 2

    all_runs, accepted_runs = stable_runs_from_windows(filtered_window_results, min_word_windows)
    raw_words, prediction_rows = confirm_words_from_windows(filtered_window_results, min_word_windows)

    return {
        "rawWords": raw_words,
        "words": clean_word_sequence(raw_words),
        "windows": window_results,
        "filteredWindows": filtered_window_results,
        "ignoredEdgeWindows": int(ignored_edge_windows),
        "runs": accepted_runs,
        "allRuns": all_runs,
        "predictionRows": prediction_rows,
    }


def evaluate_video(video_path, classifier, label_map, args, video_index=None):
    info = collect_segments(
        video_path,
        args.min_frames,
        args.segment_end_seconds,
        split_on_pause=args.split_on_pause,
        pause_seconds=args.pause_seconds,
        pause_motion_threshold=args.pause_motion_threshold,
    )
    prefix = f"{video_index}. " if video_index is not None else ""

    if args.verbose:
        print("\n[Video]")
        print(f"- path: {info['video']}")
        print(f"- fps: {info['fps']:.2f}")
        print(f"- frames: {info['frames']}")
        print(f"- visible-hand frames: {info['visible_frames']}")
        print(f"- detected segments: {len(info['segments'])}")
        print(
            f"- split on pause: {info['split_on_pause']} "
            f"(pause_frames={info['pause_frames']}, "
            f"motion_threshold={info['pause_motion_threshold']})"
        )

    if not info["segments"]:
        print(f"{prefix}{Path(video_path).name}: none")
        if args.verbose:
            print("- reason: no segment had enough visible-hand frames")
        return {
            "video": info["video"],
            "segments": [],
            "rawWords": [],
            "words": [],
            "gloss": "",
        }

    results = []
    sentence_raw_words = []
    prediction_rows = []
    for idx, segment in enumerate(info["segments"], start=1):
        probs, window_count = predict_segment(classifier, segment, args.stride)
        summary = summarize_prediction(probs, label_map)
        word_sequence = predict_segment_words(
            classifier,
            segment,
            label_map,
            args.stride,
            args.min_word_windows,
            args.ignore_edge_windows,
        )
        summary["segment"] = idx
        summary["frames"] = int(len(segment))
        summary["windows"] = int(window_count)
        summary["rawWords"] = word_sequence["rawWords"]
        summary["words"] = word_sequence["words"]
        summary["windowPredictions"] = word_sequence["windows"]
        summary["ignoredEdgeWindows"] = word_sequence["ignoredEdgeWindows"]
        summary["runs"] = word_sequence["runs"]
        results.append(summary)

        sentence_raw_words.extend(word_sequence["rawWords"])
        prediction_rows.extend(word_sequence["predictionRows"])

        if args.verbose:
            print(
                f"[Segment {idx}] "
                f"frames={len(segment)} windows={window_count} "
                f"prediction={summary['label']} "
                f"confidence={summary['confidence']:.3f} "
                f"margin={summary['margin']:.3f} "
                f"stable={summary['stable']}"
            )
            print(f"  Top-3: {summary['top3']}")
            print(f"  Ignored edge windows: {summary['ignoredEdgeWindows']}")
            print(f"  Accepted runs: {summary['runs'] if summary['runs'] else []}")
            print(f"  Raw words: {summary['rawWords'] if summary['rawWords'] else []}")
            print(f"  Cleaned words: {summary['words'] if summary['words'] else []}")
            print("  Sentence word source: confirmed sliding-window state machine")

    final_words = clean_word_sequence(sentence_raw_words)
    gloss = " ".join(final_words)
    print_prediction_table(f"\n[{prefix}{Path(video_path).name} Predictions]", prediction_rows)
    if args.verbose:
        print(f"[Final raw words] {sentence_raw_words if sentence_raw_words else []}")
        print(f"[Final words] {final_words if final_words else []}")
        print(f"[Final gloss] {gloss if gloss else 'none'}")

    print(f"{prefix}{Path(video_path).name}: {gloss if gloss else 'none'}")

    return {
        "video": info["video"],
        "segments": results,
        "rawWords": sentence_raw_words,
        "words": final_words,
        "gloss": gloss,
    }


def main():
    args = parse_args()
    model_path, label_map_path = resolve_model_files(args)

    if model_path is None or not model_path.exists():
        print("V5 TFLite model file was not found.")
        print(f"Configured directory: {resolve_from_base(args.model_dir)}")
        print(f"Requested kind: {args.kind}")
        return

    if label_map_path is None or not label_map_path.exists():
        print("V5 label map file was not found.")
        print(f"Configured directory: {resolve_from_base(args.model_dir)}")
        return

    label_map = load_label_map(label_map_path)
    classifier = TFLiteSignClassifier(model_path)

    if args.verbose:
        print("[Model]")
        print(f"- tflite: {model_path}")
        print(f"- label map: {label_map_path}")
        print(f"- details: {classifier.describe()}")
        print(
            "- thresholds: "
            f"confidence={CONFIDENCE_THRESHOLD}, margin={AMBIGUITY_MARGIN_THRESHOLD}"
        )
    else:
        print(f"Model: {model_path.name}")

    timestamps_ms = parse_timestamps_ms(args.debug_timestamps_ms)
    if timestamps_ms:
        for video in args.videos:
            debug_video_timestamps(video, classifier, label_map, timestamps_ms, args)
        return

    all_results = {}
    print("\n[Final Gloss Summary]")
    for idx, video in enumerate(args.videos, start=1):
        all_results[str(video)] = evaluate_video(video, classifier, label_map, args, video_index=idx)

    if args.verbose:
        print("\n[JSON Summary]")
        print(json.dumps(all_results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
