import argparse
import json
import os
import time
from collections import Counter, deque
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from PIL import Image, ImageDraw, ImageFont


INPUT_DIM = 332
LANDMARK_DIM = 282
MAX_LEN = 30
CONFIDENCE_THRESHOLD = float(os.environ.get("V5_CONFIDENCE_THRESHOLD", "0.75"))
AMBIGUITY_MARGIN_THRESHOLD = float(os.environ.get("V5_MARGIN_THRESHOLD", "0.08"))
PREDICTION_SMOOTHING = int(os.environ.get("V5_PREDICTION_SMOOTHING", "6"))
STABLE_MIN_COUNT = int(os.environ.get("V5_STABLE_MIN_COUNT", "4"))
MIN_FRAMES_FOR_PREDICTION = int(os.environ.get("V5_MIN_FRAMES_FOR_PREDICTION", "15"))
DEBUG_TOPK_EVERY_N_FRAMES = int(os.environ.get("V5_DEBUG_TOPK_EVERY_N_FRAMES", "15"))
CAMERA_INDEX = int(os.environ.get("V5_CAMERA_INDEX", "0"))
LOCK_ACTION_UNTIL_NO_HANDS = os.environ.get("V5_LOCK_ACTION_UNTIL_NO_HANDS", "1") == "1"
UNLOCK_NO_HANDS_SECONDS = float(os.environ.get("V5_UNLOCK_NO_HANDS_SECONDS", "0.35"))

NEUTRAL_FACE_WINDOW = 45
MIN_NEUTRAL_FACE_SAMPLES = 15
QUESTION_EYEBROW_DELTA_THRESHOLD = 0.055

BASE_DIR = Path(__file__).resolve().parent / "models"
DEFAULT_MODEL_DIR = Path("7words_test_great")
DEFAULT_TFLITE_KIND = os.environ.get("V5_TFLITE_KIND", "float16")
MODEL_PATH = os.environ.get("V5_TFLITE_MODEL_PATH", "")
LABEL_MAP_PATH = os.environ.get("V5_LABEL_MAP_PATH", "")
DEFAULT_LABELS = [
    "기차",
    "장난감",
    "놀다",
    "가다",
    "일어나다",
    "병원",
    "조심",
]

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
LEFT_EYEBROW_IDXS = [70, 63, 105, 66]
RIGHT_EYEBROW_IDXS = [336, 296, 334, 293]
LEFT_EYE_CENTER_IDXS = [33, 133]
RIGHT_EYE_CENTER_IDXS = [362, 263]


def import_tflite_interpreter():
    try:
        from tflite_runtime.interpreter import Interpreter

        return Interpreter, "tflite_runtime"
    except ImportError:
        try:
            import tensorflow as tf

            return tf.lite.Interpreter, "tensorflow"
        except ImportError as e:
            raise ImportError(
                "TFLite runtime was not found. Install tensorflow or tflite-runtime first."
            ) from e


def resolve_from_base(path):
    path = Path(path)
    if path.is_absolute():
        return path
    return (BASE_DIR / path).resolve()


def find_latest_tflite_pair(model_dir, kind=DEFAULT_TFLITE_KIND):
    model_dir = resolve_from_base(model_dir)
    candidates = []

    for tflite_path in model_dir.glob(f"*_{kind}.tflite"):
        suffix = extract_suffix(tflite_path)
        label_map_path = model_dir / f"label_map_v5_{suffix}.json"
        if suffix is not None and label_map_path.exists():
            candidates.append((suffix, tflite_path, label_map_path))

    if not candidates:
        for tflite_path in model_dir.glob("*.tflite"):
            suffix = extract_suffix(tflite_path)
            label_map_path = model_dir / f"label_map_v5_{suffix}.json"
            if suffix is not None and label_map_path.exists():
                candidates.append((suffix, tflite_path, label_map_path))

    legacy_label_map = model_dir / "label_map_v5.json"
    legacy_tflite = model_dir / f"best_sign_model_v5_{kind}.tflite"
    if legacy_tflite.exists() and legacy_label_map.exists():
        candidates.append((0, legacy_tflite, legacy_label_map))

    if not candidates:
        return None, None

    _, tflite_path, label_map_path = sorted(candidates, key=lambda item: item[0])[-1]
    return tflite_path, label_map_path


def extract_suffix(tflite_path):
    stem = tflite_path.stem
    for marker in ("_float32", "_float16"):
        if stem.endswith(marker):
            stem = stem[: -len(marker)]

    prefix = "best_sign_model_v5_"
    if stem.startswith(prefix):
        suffix = stem.replace(prefix, "")
        if suffix.isdigit():
            return int(suffix)

    return None


def load_label_map(label_map_path):
    with open(label_map_path, "r", encoding="utf-8") as f:
        raw = json.load(f)

    if isinstance(raw, dict) and "classes" in raw:
        raw = raw["classes"]

    if isinstance(raw, list):
        return {idx: label for idx, label in enumerate(raw)}

    if isinstance(raw, dict):
        return {int(k): v for k, v in raw.items()}

    return {idx: label for idx, label in enumerate(DEFAULT_LABELS)}


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
            [
                face_landmarks.landmark[idx].x,
                face_landmarks.landmark[idx].y,
                face_landmarks.landmark[idx].z,
            ]
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


def extract_keypoints(results, last_pose, last_face):
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

    return normalize_features(feature), last_pose, last_face


def normalize_features(feature):
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


def has_visible_hand(results):
    return results.left_hand_landmarks is not None or results.right_hand_landmarks is not None


def are_both_hands_missing(results):
    return results.left_hand_landmarks is None and results.right_hand_landmarks is None


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


def pad_sequence(sequence, max_len=MAX_LEN):
    frames = np.array(sequence, dtype=np.float32)
    if len(frames) == 0:
        return np.zeros((max_len, INPUT_DIM), dtype=np.float32), 0

    if len(frames) > max_len:
        indices = np.linspace(0, len(frames) - 1, max_len, dtype=int)
        return frames[indices], max_len

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
        return upsampled, max_len

    pad_len = max_len - len(frames)
    padding = np.repeat(frames[-1:], pad_len, axis=0)
    return np.vstack((frames, padding)).astype(np.float32), max_len


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


def format_topk(probs, label_map, k=3):
    top_indices = np.argsort(probs)[::-1][:k]
    return ", ".join(f"{label_map[int(idx)]}:{float(probs[idx]):.3f}" for idx in top_indices)


def softmax(logits):
    logits = logits.astype(np.float32)
    logits = logits - np.max(logits)
    exp = np.exp(logits)
    return exp / np.sum(exp)


class TFLiteSignClassifier:
    def __init__(self, tflite_path):
        Interpreter, runtime_name = import_tflite_interpreter()
        self.runtime_name = runtime_name
        self.interpreter = Interpreter(model_path=str(tflite_path))
        self.interpreter.allocate_tensors()
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()

        self.input_index = self.input_details[0]["index"]
        self.output_index = self.output_details[0]["index"]
        self.input_dtype = self.input_details[0]["dtype"]

    def predict(self, input_array):
        batched = np.expand_dims(input_array, axis=0).astype(self.input_dtype)
        self.interpreter.set_tensor(self.input_index, batched)
        self.interpreter.invoke()
        output = self.interpreter.get_tensor(self.output_index)[0].astype(np.float32)

    # Current TFLite exports include softmax, but keep this robust for logits.
        if np.any(output < -1e-6) or not np.isclose(float(np.sum(output)), 1.0, atol=1e-3):
            output = softmax(output)
        return output

    def describe(self):
        return {
            "runtime": self.runtime_name,
            "input_shape": self.input_details[0]["shape"].tolist(),
            "input_dtype": str(self.input_details[0]["dtype"]),
            "output_shape": self.output_details[0]["shape"].tolist(),
            "output_dtype": str(self.output_details[0]["dtype"]),
        }


def parse_args():
    parser = argparse.ArgumentParser(description="Realtime webcam inference for V5 TFLite models.")
    parser.add_argument("--model-dir", default=str(DEFAULT_MODEL_DIR), help="Model directory under Transformer_v5_Impl/models")
    parser.add_argument("--kind", choices=("float16", "float32"), default=DEFAULT_TFLITE_KIND)
    parser.add_argument("--model", default=MODEL_PATH, help="Explicit .tflite path")
    parser.add_argument("--label-map", default=LABEL_MAP_PATH, help="Explicit label_map_v5_*.json path")
    return parser.parse_args()


def main():
    args = parse_args()

    if args.model and args.label_map:
        model_path = resolve_from_base(args.model)
        label_map_path = resolve_from_base(args.label_map)
    else:
        model_path, label_map_path = find_latest_tflite_pair(args.model_dir, args.kind)

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

    print(f"V5 TFLite model loaded: {model_path}")
    print(f"Label map loaded: {label_map_path}")
    print(f"TFLite details: {classifier.describe()}")
    print(
        "Runtime thresholds: "
        f"confidence={CONFIDENCE_THRESHOLD}, margin={AMBIGUITY_MARGIN_THRESHOLD}, "
        f"smoothing={PREDICTION_SMOOTHING}, stable_min={STABLE_MIN_COUNT}, "
        f"lock_until_no_hands={LOCK_ACTION_UNTIL_NO_HANDS}"
    )

    mp_holistic = mp.solutions.holistic
    mp_drawing = mp.solutions.drawing_utils
    cap = cv2.VideoCapture(CAMERA_INDEX)

    frame_window = deque(maxlen=MAX_LEN)
    prediction_window = deque(maxlen=PREDICTION_SMOOTHING)
    neutral_gaps = deque(maxlen=NEUTRAL_FACE_WINDOW)
    current_action = "none"
    current_confidence = 0.0
    current_margin = 0.0
    current_sentence_type = "평서문"
    current_eyebrow_delta = 0.0
    current_neutral_gap = None
    last_logged_action = None
    locked_action = None
    both_hands_missing_since = None
    last_pose = None
    last_face = None
    inference_count = 0

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
            both_hands_missing = are_both_hands_missing(results)
            eyebrow_gap = get_normalized_eyebrow_gap(results)

            if not hand_visible:
                now = time.monotonic()
                if both_hands_missing_since is None:
                    both_hands_missing_since = now
                if now - both_hands_missing_since >= UNLOCK_NO_HANDS_SECONDS:
                    locked_action = None

                if eyebrow_gap is not None:
                    neutral_gaps.append(eyebrow_gap)

                frame_window.clear()
                prediction_window.clear()
                current_action = "none"
                current_confidence = 0.0
                current_margin = 0.0
                current_sentence_type = "평서문"
                current_eyebrow_delta = 0.0
                current_neutral_gap = float(np.median(neutral_gaps)) if neutral_gaps else None
                last_logged_action = None
                last_pose = None
                last_face = None
            else:
                both_hands_missing_since = None
                current_sentence_type, current_eyebrow_delta, current_neutral_gap = classify_sentence_type(
                    eyebrow_gap,
                    neutral_gaps,
                )
                keypoints, last_pose, last_face = extract_keypoints(results, last_pose, last_face)
                frame_window.append(keypoints)

            if hand_visible and len(frame_window) >= MIN_FRAMES_FOR_PREDICTION:
                input_array, _ = pad_sequence(frame_window)
                probs_np = classifier.predict(input_array)
                top_indices = np.argsort(probs_np)[::-1]
                idx = int(top_indices[0])
                predicted_label = label_map[idx]
                top2_idx = int(top_indices[1]) if len(top_indices) > 1 else idx
                current_confidence = float(probs_np[idx])
                current_margin = current_confidence - float(probs_np[top2_idx])
                inference_count += 1

                if inference_count % DEBUG_TOPK_EVERY_N_FRAMES == 0:
                    print(
                        f"Top-{min(3, len(label_map))}: {format_topk(probs_np, label_map)} | "
                        f"margin={current_margin:.3f}"
                    )

                if LOCK_ACTION_UNTIL_NO_HANDS and locked_action is not None and predicted_label != locked_action:
                    current_action = locked_action
                else:
                    if (
                        current_confidence >= CONFIDENCE_THRESHOLD
                        and current_margin >= AMBIGUITY_MARGIN_THRESHOLD
                    ):
                        prediction_window.append(predicted_label)
                    else:
                        prediction_window.clear()
                        prediction_window.append("none")

                if prediction_window:
                    candidate, count = Counter(prediction_window).most_common(1)[0]
                    if candidate == "none" or count >= STABLE_MIN_COUNT:
                        current_action = "none" if candidate == "none" else candidate

                        if current_action != "none" and current_action != last_logged_action:
                            print(
                                f"Detected: {current_action} | "
                                f"confidence={current_confidence:.3f} | "
                                f"margin={current_margin:.3f} | "
                                f"eyebrow_delta={current_eyebrow_delta:.3f}"
                            )
                            locked_action = current_action
                            last_logged_action = current_action
                    else:
                        current_action = "none"
                        last_logged_action = None

            mp_drawing.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)

            display_frame = cv2.flip(frame, 1)
            neutral_text = "none" if current_neutral_gap is None else f"{current_neutral_gap:.3f}"
            display_frame = draw_text(
                display_frame,
                [
                    f"TFLite: {args.kind}",
                    f"Prediction: {current_action}",
                    f"Confidence: {current_confidence:.3f}",
                    f"Margin: {current_margin:.3f}",
                    f"Sentence: {current_sentence_type}",
                    f"Eyebrow delta: {current_eyebrow_delta:.3f}",
                    f"Neutral gap: {neutral_text} ({len(neutral_gaps)}/{MIN_NEUTRAL_FACE_SAMPLES})",
                    f"Frames: {len(frame_window)}/{MAX_LEN}",
                    "Quit: q",
                ],
            )

            cv2.imshow("KSL Transformer V5 TFLite Real-time", display_frame)

            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
