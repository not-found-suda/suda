import argparse
import json
import time
from collections import Counter, deque
from datetime import datetime
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

try:
    import tensorflow as tf
except ImportError:
    print("TensorFlow is not installed. Please install it with `pip install tensorflow`.")
    raise SystemExit(1)

from PIL import Image, ImageDraw, ImageFont

INPUT_DIM = 332
LANDMARK_DIM = 282
MAX_LEN = 30
CONFIDENCE_THRESHOLD = 0.80
PREDICTION_SMOOTHING = 5
STABLE_MIN_COUNT = 4

BASE_DIR = Path(__file__).resolve().parent / "models"
DEFAULT_MODEL_DIR = "v6_24words_tcn_2"

NEUTRAL_FACE_WINDOW = 45
MIN_NEUTRAL_FACE_SAMPLES = 15
QUESTION_EYEBROW_DELTA_THRESHOLD = 0.055

LEFT_EYEBROW_IDXS = [70, 63, 105, 66]
RIGHT_EYEBROW_IDXS = [336, 296, 334, 293]
LEFT_EYE_CENTER_IDXS = [33, 133]
RIGHT_EYE_CENTER_IDXS = [362, 263]

BASE_FACE_IDXS = [
    70, 63, 105, 66,
    336, 296, 334, 293,
    33, 160, 158, 133, 153, 144,
    362, 385, 387, 263, 373, 380,
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
]
FACEPLUS_IDXS = [
    1, 4,
    152,
    234, 454,
    172, 397,
    148, 377,
    13, 14,
    78, 308,
    82, 312,
]
FACE_IDXS = BASE_FACE_IDXS + FACEPLUS_IDXS
POSE_IDXS = [0, 7, 8, 11, 12, 13, 14]
HAND_TIP_IDXS = [4, 8, 12, 16, 20]
FACE_ANCHOR_IDXS = [1, 13, 152, 234, 454]

LEFT_SHOULDER_IDX = 90
RIGHT_SHOULDER_IDX = 91


def resolve_from_base(path):
    path = Path(path)
    if path.is_absolute():
        return path
    return (BASE_DIR / path).resolve()


def _flatten_landmarks(landmarks, idxs):
    return np.array(
        [[landmarks[idx].x, landmarks[idx].y, landmarks[idx].z] for idx in idxs],
        dtype=np.float32,
    ).flatten()


def _hand_array(hand_landmarks):
    if not hand_landmarks:
        return np.zeros((21, 3), dtype=np.float32), False
    points = np.array([[lm.x, lm.y, lm.z] for lm in hand_landmarks.landmark], dtype=np.float32)
    return points, True


def _hand_face_distances(left_hand, left_present, right_hand, right_present, face_landmarks):
    if not face_landmarks:
        return np.zeros(len(HAND_TIP_IDXS) * 2 * len(FACE_ANCHOR_IDXS), dtype=np.float32)

    anchors = np.array(
        [
            [face_landmarks.landmark[idx].x, face_landmarks.landmark[idx].y, face_landmarks.landmark[idx].z]
            for idx in FACE_ANCHOR_IDXS
        ],
        dtype=np.float32,
    )

    features = []
    for hand_points, is_present in ((left_hand, left_present), (right_hand, right_present)):
        if not is_present:
            features.extend([0.0] * (len(HAND_TIP_IDXS) * len(FACE_ANCHOR_IDXS)))
            continue

        for tip_idx in HAND_TIP_IDXS:
            tip = hand_points[tip_idx]
            features.extend(np.linalg.norm(anchors - tip, axis=1).tolist())

    return np.array(features, dtype=np.float32)


def extract_keypoints(results, last_pose, last_face, normalize_mode):
    left_hand, left_present = _hand_array(results.left_hand_landmarks)
    right_hand, right_present = _hand_array(results.right_hand_landmarks)
    lh = left_hand.flatten()
    rh = right_hand.flatten()

    if results.face_landmarks:
        face = _flatten_landmarks(results.face_landmarks.landmark, FACE_IDXS)
        last_face = face
    else:
        face = last_face if last_face is not None else np.zeros(len(FACE_IDXS) * 3, dtype=np.float32)

    if results.pose_landmarks:
        pose = _flatten_landmarks(results.pose_landmarks.landmark, POSE_IDXS)
        last_pose = pose
    else:
        pose = last_pose if last_pose is not None else np.zeros(len(POSE_IDXS) * 3, dtype=np.float32)

    distances = _hand_face_distances(
        left_hand,
        left_present,
        right_hand,
        right_present,
        results.face_landmarks,
    )

    feature = np.concatenate([lh, rh, face, pose, distances]).astype(np.float32)
    if feature.shape[0] != INPUT_DIM:
        raise ValueError(f"Unexpected feature dim: {feature.shape[0]} != {INPUT_DIM}")

    return normalize_features(feature, normalize_mode), last_pose, last_face


def normalize_features(feature, normalize_mode):
    if normalize_mode in ("none", "raw", "off"):
        return np.array(feature, dtype=np.float32)

    landmarks = feature[:LANDMARK_DIM].reshape(-1, 3)
    distances = feature[LANDMARK_DIM:]

    l_shoulder = landmarks[LEFT_SHOULDER_IDX]
    r_shoulder = landmarks[RIGHT_SHOULDER_IDX]
    shoulder_center = (l_shoulder + r_shoulder) / 2.0

    shoulder_width = np.linalg.norm(l_shoulder - r_shoulder)
    if shoulder_width < 1e-6:
        shoulder_width = 1.0

    normalized_landmarks = (landmarks - shoulder_center) / shoulder_width
    normalized_distances = distances / shoulder_width
    return np.concatenate([normalized_landmarks.flatten(), normalized_distances]).astype(np.float32)


def get_normalized_eyebrow_gap(results):
    if not results.face_landmarks:
        return None

    landmarks = results.face_landmarks.landmark
    left_eye = np.mean([[landmarks[idx].x, landmarks[idx].y] for idx in LEFT_EYE_CENTER_IDXS], axis=0)
    right_eye = np.mean([[landmarks[idx].x, landmarks[idx].y] for idx in RIGHT_EYE_CENTER_IDXS], axis=0)
    eye_distance = np.linalg.norm(left_eye - right_eye)
    if eye_distance < 1e-6:
        return None

    eyebrow_y = np.mean([landmarks[idx].y for idx in LEFT_EYEBROW_IDXS + RIGHT_EYEBROW_IDXS])
    eye_y = np.mean([landmarks[idx].y for idx in LEFT_EYE_CENTER_IDXS + RIGHT_EYE_CENTER_IDXS])
    return float((eye_y - eyebrow_y) / eye_distance)


def classify_sentence_type(current_gap, neutral_gaps):
    if current_gap is None or len(neutral_gaps) < MIN_NEUTRAL_FACE_SAMPLES:
        return "평서문", 0.0, None

    neutral_gap = float(np.median(neutral_gaps))
    eyebrow_delta = current_gap - neutral_gap

    if eyebrow_delta >= QUESTION_EYEBROW_DELTA_THRESHOLD:
        return "의문문", eyebrow_delta, neutral_gap

    return "평서문", eyebrow_delta, neutral_gap


def has_visible_hand(results):
    return results.left_hand_landmarks is not None or results.right_hand_landmarks is not None


def pad_sequence(sequence, max_len=MAX_LEN):
    frames = np.array(sequence, dtype=np.float32)
    if len(frames) == 0:
        return np.zeros((max_len, INPUT_DIM), dtype=np.float32)

    if len(frames) > max_len:
        indices = np.linspace(0, len(frames) - 1, max_len, dtype=int)
        return frames[indices]

    if len(frames) < 15:
        indices = np.linspace(0, len(frames) - 1, max_len)
        upsampled = np.zeros((max_len, INPUT_DIM), dtype=np.float32)
        for i, float_idx in enumerate(indices):
            low = int(np.floor(float_idx))
            high = int(np.ceil(float_idx))
            weight = float_idx - low
            if low == high:
                upsampled[i] = frames[low]
            else:
                upsampled[i] = frames[low] * (1 - weight) + frames[high] * weight
        return upsampled

    pad_len = max_len - len(frames)
    padding = np.repeat(frames[-1:], pad_len, axis=0)
    return np.vstack((frames, padding)).astype(np.float32)


def draw_text(frame, lines):
    image = Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    draw = ImageDraw.Draw(image)

    try:
        font = ImageFont.truetype("malgun.ttf", 30)
    except Exception:
        font = ImageFont.load_default()

    y = 25
    for line in lines:
        draw.text((25, y), line, font=font, fill=(0, 255, 0))
        y += 38

    return cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)


def build_inference_payload(probs, label_map, confidence_threshold):
    max_idx = int(np.argmax(probs))
    top_indices = np.argsort(probs)[::-1]
    second_idx = int(top_indices[1]) if len(top_indices) > 1 else None

    confidence = float(probs[max_idx])
    second_confidence = float(probs[second_idx]) if second_idx is not None else None
    margin = confidence - second_confidence if second_confidence is not None else confidence
    raw_gloss = label_map[max_idx]
    accepted = confidence >= confidence_threshold

    return {
        "gloss": raw_gloss if accepted else "unknown",
        "rawGloss": raw_gloss,
        "confidence": confidence,
        "margin": float(margin),
        "classIndex": max_idx,
        "secondGloss": label_map[second_idx] if second_idx is not None else None,
        "secondConfidence": second_confidence,
        "accepted": accepted,
        "rejectionReason": None if accepted else "low_confidence",
    }


def dump_utterance(output_dir, glosses, sentence_type, segment_frames, sequence_snapshot, inference_result):
    if not glosses or not segment_frames or sequence_snapshot is None:
        return

    output_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "createdAt": datetime.now().isoformat(timespec="milliseconds"),
        "glosses": list(glosses),
        "sentenceType": sentence_type,
        "segmentFrameCount": len(segment_frames),
        "segmentHandFrameCount": sum(1 for frame in segment_frames if frame["hasHands"]),
        "segmentTimestampsMs": [frame["timestampMs"] for frame in segment_frames],
        "segmentHasHands": [frame["hasHands"] for frame in segment_frames],
        "sequenceTimestampsMs": sequence_snapshot["timestampsMs"],
        "inference": inference_result,
        "sequenceFrames": sequence_snapshot["frames"],
    }

    safe_glosses = "_".join(glosses) if glosses else "empty"
    filename = f"{datetime.now().strftime('%Y%m%d_%H%M%S_%f')[:-3]}_{safe_glosses}.json"
    target_path = output_dir / filename
    target_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Debug dump saved: {target_path}")


def parse_args():
    parser = argparse.ArgumentParser(description="Realtime webcam inference for V6 TFLite models.")
    parser.add_argument("--model-dir", default=DEFAULT_MODEL_DIR, help="Model directory")
    parser.add_argument(
        "--precision",
        choices=["float32", "float16"],
        default="float16",
        help="Precision of the tflite model to load",
    )
    parser.add_argument(
        "--dump-dir",
        default=None,
        help="Optional directory for PC TFLite utterance dumps. Defaults to <model_dir>/pc-sign-pipeline-dumps",
    )
    return parser.parse_args()


def main():
    args = parse_args()
    model_dir = resolve_from_base(args.model_dir)
    dump_dir = Path(args.dump_dir).resolve() if args.dump_dir else (model_dir / "pc-sign-pipeline-dumps")

    tflite_path = model_dir / f"best_sign_model_v6_{args.precision}.tflite"
    config_path = model_dir / "train_config_v6.json"
    label_map_path = model_dir / "label_map_v6.json"

    if not tflite_path.exists() or not config_path.exists() or not label_map_path.exists():
        print(f"Model files not found in {model_dir}. Please make sure you copied the .tflite files here!")
        print(f"Missing: {tflite_path}")
        return

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    with open(label_map_path, "r", encoding="utf-8") as f:
        label_map_raw = json.load(f)
        label_map = {int(k): v for k, v in label_map_raw.items()}

    normalize_mode = config["stats"].get("normalize_mode", "shoulder")
    num_classes = len(label_map)

    print(f"Loading TFLite model from {tflite_path} ...")
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"Loaded {args.precision} TFLite model!")
    print(f"Normalize Mode: {normalize_mode}")
    print(f"Classes: {num_classes}")
    print(f"Dump Dir: {dump_dir}")

    mp_holistic = mp.solutions.holistic
    mp_drawing = mp.solutions.drawing_utils
    cap = cv2.VideoCapture(0)

    frame_window = deque(maxlen=MAX_LEN)
    prediction_window = deque(maxlen=PREDICTION_SMOOTHING)

    current_action = "none"
    current_confidence = 0.0
    last_pose = None
    last_face = None

    locked_action = "none"
    locked_time = 0.0
    last_logged_action = None

    missing_hand_frames = 0
    sentence_buffer = []
    segment_frames = []
    last_sequence_snapshot = None
    last_inference_result = None

    neutral_gaps = deque(maxlen=NEUTRAL_FACE_WINDOW)
    sentence_is_question = False

    with mp_holistic.Holistic(
        static_image_mode=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as holistic:
        while cap.isOpened():
            success, frame = cap.read()
            if not success:
                break

            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(image_rgb)

            hand_visible = has_visible_hand(results)
            eyebrow_gap = get_normalized_eyebrow_gap(results)
            frame_timestamp_ms = int(time.time() * 1000)
            segment_frames.append({
                "timestampMs": frame_timestamp_ms,
                "hasHands": hand_visible,
            })

            if not hand_visible:
                missing_hand_frames += 1
                if eyebrow_gap is not None:
                    neutral_gaps.append(eyebrow_gap)
            else:
                missing_hand_frames = 0
                current_sentence_type, _, _ = classify_sentence_type(eyebrow_gap, neutral_gaps)
                if current_sentence_type == "의문문":
                    sentence_is_question = True

            if missing_hand_frames > 15:
                if sentence_buffer:
                    final_type = "의문문" if sentence_is_question else "평서문"
                    print(f"\n✨ === 최종 문장: {' '.join(sentence_buffer)} ({final_type}) === ✨\n")
                    dump_utterance(
                        output_dir=dump_dir,
                        glosses=sentence_buffer,
                        sentence_type=final_type,
                        segment_frames=segment_frames,
                        sequence_snapshot=last_sequence_snapshot,
                        inference_result=last_inference_result,
                    )
                    sentence_buffer.clear()
                    sentence_is_question = False

                frame_window.clear()
                prediction_window.clear()
                current_action = "none"
                current_confidence = 0.0
                last_pose = None
                last_face = None
                last_logged_action = None
                segment_frames.clear()
                last_sequence_snapshot = None
                last_inference_result = None
            else:
                keypoints, last_pose, last_face = extract_keypoints(results, last_pose, last_face, normalize_mode)
                frame_window.append(keypoints)

            if len(frame_window) >= 15:
                input_array = pad_sequence(frame_window)
                input_tensor = np.expand_dims(input_array, axis=0).astype(np.float32)

                interpreter.set_tensor(input_details[0]["index"], input_tensor)
                interpreter.invoke()
                probs = interpreter.get_tensor(output_details[0]["index"])[0]

                inference_payload = build_inference_payload(probs, label_map, CONFIDENCE_THRESHOLD)
                predicted_label = inference_payload["rawGloss"]
                current_confidence = inference_payload["confidence"]
                last_inference_result = inference_payload
                last_sequence_snapshot = {
                    "timestampsMs": [frame["timestampMs"] for frame in segment_frames[-len(input_array):]],
                    "frames": input_array.astype(np.float32).tolist(),
                }

                if current_confidence >= CONFIDENCE_THRESHOLD:
                    prediction_window.append(predicted_label)
                else:
                    prediction_window.clear()
                    prediction_window.append("none")

                if prediction_window:
                    candidate, count = Counter(prediction_window).most_common(1)[0]
                    if candidate == "none" or count >= STABLE_MIN_COUNT:
                        current_action = candidate

                        if current_action != "none":
                            if current_action != last_logged_action:
                                print(f"👉 Detected: {current_action} (Confidence: {current_confidence:.3f})")
                                locked_action = current_action
                                locked_time = time.time()
                                last_logged_action = current_action

                                if not sentence_buffer or sentence_buffer[-1] != current_action:
                                    sentence_buffer.append(current_action)
                        else:
                            last_logged_action = None

            if time.time() - locked_time > 2.0:
                locked_action = "none"

            mp_drawing.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)

            sentence_str = " ".join(sentence_buffer) if sentence_buffer else ""
            type_str = "의문문" if sentence_is_question else "평서문"

            display_frame = cv2.flip(frame, 1)
            display_frame = draw_text(
                display_frame,
                [
                    f"Model: TFLite V6 ({args.precision})",
                    f"Live Pred: {current_action} ({current_confidence:.2f})",
                    f"Result: {locked_action if locked_action != 'none' else ''}",
                    f"Buffer: {sentence_str} ({type_str})",
                    f"Frames: {len(frame_window)}/{MAX_LEN}",
                    "Quit: q",
                ],
            )

            cv2.imshow("V6 TFLite Real-time Inference", display_frame)

            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    if sentence_buffer:
        final_type = "의문문" if sentence_is_question else "평서문"
        dump_utterance(
            output_dir=dump_dir,
            glosses=sentence_buffer,
            sentence_type=final_type,
            segment_frames=segment_frames,
            sequence_snapshot=last_sequence_snapshot,
            inference_result=last_inference_result,
        )

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
