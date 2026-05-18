import re
import time
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from PIL import Image, ImageDraw, ImageFont


SCRIPT_DIR = Path(__file__).resolve().parent
OUTPUT_ROOT = SCRIPT_DIR / "get_data" / "processed_npy_v6"
SEQ_LEN = 60
COUNTDOWN_SECONDS = (4, 3, 2, 1)
WINDOW_NAME = "Collect V6 Sign Data"
ORIENTATION_PLAN = ["FRONT", "FRONT", "FRONT", "LEFT", "RIGHT"]
DISPLAY_MIRROR_MODE = True

# Representative class -> variants to collect.
# Saved path: processed_npy_v6/{class_name}/{variant_name}/{file}.npy
COLLECTION_TARGETS = {
    "none": ["none1", "none2", "none3"],
}

# Feature layout:
# left hand 63 + right hand 63 + expanded face 135 + pose 21 + hand-face distances 50 = 332
INPUT_DIM = 332
BASE_FACE_IDXS = [
    70, 63, 105, 66,
    336, 296, 334, 293,
    33, 160, 158, 133, 153, 144,
    362, 385, 387, 263, 373, 380,
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
]
FACEPLUS_IDXS = [
    1, 4,          # nose
    152,           # chin
    234, 454,      # cheeks / face sides
    172, 397,      # jaw line
    148, 377,      # lower jaw line
    13, 14,        # upper/lower lip center
    78, 308,       # mouth corners
    82, 312,       # upper lip detail
]
FACE_IDXS = BASE_FACE_IDXS + FACEPLUS_IDXS
POSE_IDXS = [0, 7, 8, 11, 12, 13, 14]
HAND_TIP_IDXS = [4, 8, 12, 16, 20]
FACE_ANCHOR_IDXS = [1, 13, 152, 234, 454]  # nose, mouth, chin, left cheek, right cheek

mp_holistic = mp.solutions.holistic
mp_drawing = mp.solutions.drawing_utils


def slugify(value):
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9_-]+", "_", value)
    return value.strip("_") or "person"


def flatten_landmarks(landmarks, idxs):
    return np.array(
        [[landmarks[idx].x, landmarks[idx].y, landmarks[idx].z] for idx in idxs],
        dtype=np.float32,
    ).flatten()


def hand_array(hand_landmarks):
    if not hand_landmarks:
        return np.zeros((21, 3), dtype=np.float32), False

    points = np.array([[lm.x, lm.y, lm.z] for lm in hand_landmarks.landmark], dtype=np.float32)
    return points, True


def hand_face_distances(left_hand, left_present, right_hand, right_present, face_landmarks):
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
    left_hand, left_present = hand_array(results.left_hand_landmarks)
    right_hand, right_present = hand_array(results.right_hand_landmarks)
    lh = left_hand.flatten()
    rh = right_hand.flatten()

    if results.face_landmarks:
        face = flatten_landmarks(results.face_landmarks.landmark, FACE_IDXS)
        last_face = face
    else:
        face = last_face if last_face is not None else np.zeros(len(FACE_IDXS) * 3, dtype=np.float32)

    if results.pose_landmarks:
        pose = flatten_landmarks(results.pose_landmarks.landmark, POSE_IDXS)
        last_pose = pose
    else:
        pose = last_pose if last_pose is not None else np.zeros(len(POSE_IDXS) * 3, dtype=np.float32)

    distances = hand_face_distances(
        left_hand,
        left_present,
        right_hand,
        right_present,
        results.face_landmarks,
    )

    feature = np.concatenate([lh, rh, face, pose, distances]).astype(np.float32)
    if feature.shape[0] != INPUT_DIM:
        raise ValueError(f"Unexpected feature dim: {feature.shape[0]} != {INPUT_DIM}")

    return feature, last_pose, last_face


def read_key(delay=20):
    return cv2.waitKey(delay) & 0xFF


def is_start_key(key):
    return key in (ord("s"), ord("S"), 13, 32)


def is_quit_key(key):
    return key in (ord("q"), ord("Q"), 27)


def draw_landmark_list(frame, landmark_list, connections):
    if landmark_list:
        mp_drawing.draw_landmarks(frame, landmark_list, connections)


def draw_mirrored_landmarks(frame, landmark_list, connections):
    if not landmark_list:
        return

    mirrored = type(landmark_list)()
    mirrored.landmark.extend(landmark_list.landmark)
    for lm in mirrored.landmark:
        lm.x = 1.0 - lm.x

    mp_drawing.draw_landmarks(frame, mirrored, connections)


def draw_landmarks(frame, results, mirror=False):
    draw_fn = draw_mirrored_landmarks if mirror else draw_landmark_list
    draw_fn(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
    draw_fn(frame, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
    draw_fn(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)


def make_display_frame(frame, results):
    if not DISPLAY_MIRROR_MODE:
        display = frame.copy()
        draw_landmarks(display, results, mirror=False)
        return display

    display = cv2.flip(frame, 1)
    draw_landmarks(display, results, mirror=True)
    return display


def get_korean_font(size=28):
    for font_name in ("malgun.ttf", "malgunbd.ttf", "/usr/share/fonts/truetype/nanum/NanumGothic.ttf"):
        try:
            return ImageFont.truetype(font_name, size)
        except Exception:
            continue
    return ImageFont.load_default()


def draw_text(frame, lines):
    image = Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    draw = ImageDraw.Draw(image)
    font = get_korean_font(28)

    y = 24
    for line in lines:
        draw.text((20, y), line, font=font, fill=(0, 255, 0))
        y += 34

    frame[:] = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)


def countdown(cap, holistic, title_lines, seconds=COUNTDOWN_SECONDS):
    for sec in seconds:
        end_time = time.time() + 1.0
        while time.time() < end_time:
            ok, frame = cap.read()
            if not ok:
                continue

            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            display = make_display_frame(frame, results)
            draw_text(display, title_lines)
            cv2.putText(
                display,
                str(sec),
                (display.shape[1] // 2 - 30, display.shape[0] // 2),
                cv2.FONT_HERSHEY_SIMPLEX,
                3.0,
                (0, 255, 255),
                5,
            )
            cv2.imshow(WINDOW_NAME, display)
            if is_quit_key(read_key(20)):
                raise SystemExit


def wait_for_initial_start(cap, holistic, participant_id, selected_targets):
    title_lines = [
        "V5 수어 데이터 수집",
        f"참가자: {participant_id}",
        f"수집 variant 수: {len(selected_targets)} | variant당 촬영: {len(ORIENTATION_PLAN)}",
        "상반신 전체, 얼굴, 양손이 화면에 들어오게 맞춰주세요.",
        "S / Enter / Space: 자동 진행, Q: 종료",
    ]
    while True:
        ok, frame = cap.read()
        if not ok:
            continue

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)
        display = make_display_frame(frame, results)
        draw_text(display, title_lines)
        cv2.imshow(WINDOW_NAME, display)

        key = read_key(20)
        if is_start_key(key):
            return
        if is_quit_key(key):
            raise SystemExit


def wait_for_target_start(cap, holistic, participant_id, class_name, variant_name, target_idx, total_targets):
    title_lines = [
        "다음 수집 대상 준비",
        f"참가자: {participant_id}",
        f"대상: {target_idx}/{total_targets} - {class_name} / {variant_name}",
        f"촬영 방향: {ORIENTATION_PLAN}",
        "자세를 맞춘 뒤 S / Enter / Space를 누르면 이 대상 촬영을 시작합니다.",
        "Q: 종료",
    ]
    while True:
        ok, frame = cap.read()
        if not ok:
            continue

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)
        display = make_display_frame(frame, results)
        draw_text(display, title_lines)
        cv2.imshow(WINDOW_NAME, display)

        key = read_key(20)
        if is_start_key(key):
            return
        if is_quit_key(key):
            raise SystemExit


def capture_sequence(cap, holistic, participant_id, class_name, variant_name, orient, sample_no, take_idx, total_takes):
    title_lines = [
        f"다음 동작: {variant_name}",
        f"저장 라벨: {class_name} / {variant_name}",
        f"참가자: {participant_id} | 촬영: {take_idx}/{total_takes} | 방향: {orient}",
        "4초 카운트다운 후 자동 시작. Q 종료.",
    ]

    countdown(cap, holistic, title_lines)

    sequence = []
    last_pose = None
    last_face = None

    while len(sequence) < SEQ_LEN:
        ok, frame = cap.read()
        if not ok:
            continue

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)
        keypoints, last_pose, last_face = extract_keypoints(results, last_pose, last_face)
        sequence.append(keypoints)

        display = make_display_frame(frame, results)
        draw_text(
            display,
            [
                f"촬영 중: {len(sequence)}/{SEQ_LEN}",
                f"{class_name} / {variant_name} / {orient}",
                "상반신 전체와 손 이동 방향이 잘 보이게 유지해주세요.",
            ],
        )
        cv2.imshow(WINDOW_NAME, display)

        if is_quit_key(read_key(1)):
            raise SystemExit

    data = np.array(sequence, dtype=np.float32)
    if data.ndim != 2 or data.shape[1] != INPUT_DIM:
        raise ValueError(f"Unexpected sequence shape: {data.shape}")
    return data


def next_index(output_dir, participant_id, class_name, variant_name):
    output_dir.mkdir(parents=True, exist_ok=True)
    prefix = f"{class_name}_{variant_name}_{participant_id}_"
    indices = []

    for path in output_dir.glob(f"{prefix}*.npy"):
        stem = path.stem
        parts = stem.split("_")
        if len(parts) >= 5 and parts[-2].isdigit():
            indices.append(int(parts[-2]))

    return max(indices) + 1 if indices else 1


def all_collection_targets():
    targets = []
    for class_name, variants in COLLECTION_TARGETS.items():
        for variant_name in variants:
            targets.append((class_name, variant_name))
    return targets


def choose_targets():
    targets = all_collection_targets()
    print("\nV5 collection targets")
    for idx, (class_name, variant_name) in enumerate(targets, start=1):
        print(f"{idx}. {class_name} / {variant_name}")
    print("a. All targets")

    while True:
        raw = input("Select target numbers separated by comma, or 'a' for all: ").strip().lower()
        if raw == "a":
            return targets

        selected = []
        try:
            for token in raw.split(","):
                idx = int(token.strip())
                if not 1 <= idx <= len(targets):
                    raise ValueError
                selected.append(targets[idx - 1])
        except ValueError:
            print("Invalid input. Example: 1,2 or a")
            continue

        if selected:
            return selected


def main():
    participant_id = slugify(input("Participant ID (example: p01): "))
    selected_targets = choose_targets()

    print("\nCollection plan")
    print(f"- output root: {OUTPUT_ROOT}")
    print(f"- participant: {participant_id}")
    print(f"- sequence length: {SEQ_LEN}")
    print(f"- feature dim: {INPUT_DIM}")
    print(f"- views per target: {ORIENTATION_PLAN}")
    print(f"- selected targets: {selected_targets}")
    print("\nTip: each participant can run all targets once or twice for stronger coverage.")

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        raise RuntimeError("Cannot open webcam.")

    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    try:
        with mp_holistic.Holistic(
            static_image_mode=False,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5,
        ) as holistic:
            wait_for_initial_start(cap, holistic, participant_id, selected_targets)

            total_takes = len(selected_targets) * len(ORIENTATION_PLAN)
            current_take = 0

            for target_idx, (class_name, variant_name) in enumerate(selected_targets, start=1):
                wait_for_target_start(
                    cap,
                    holistic,
                    participant_id,
                    class_name,
                    variant_name,
                    target_idx,
                    len(selected_targets),
                )

                output_dir = OUTPUT_ROOT / class_name / variant_name
                start_idx = next_index(output_dir, participant_id, class_name, variant_name)

                for offset, orient in enumerate(ORIENTATION_PLAN):
                    current_take += 1
                    sample_no = start_idx + offset
                    data = capture_sequence(
                        cap,
                        holistic,
                        participant_id,
                        class_name,
                        variant_name,
                        orient,
                        sample_no,
                        current_take,
                        total_takes,
                    )
                    save_path = (
                        output_dir
                        / f"{class_name}_{variant_name}_{participant_id}_{sample_no:03d}_{orient}.npy"
                    )
                    np.save(save_path, data)
                    print(f"saved: {save_path} shape={data.shape}")

    finally:
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
