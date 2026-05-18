import os
import time
import cv2
import numpy as np
import mediapipe as mp
import tkinter as tk
from tkinter import messagebox

mp_holistic = mp.solutions.holistic
mp_drawing = mp.solutions.drawing_utils

DATA_DIR = "data/raw"
SEQ_LEN = 60
ORIENTATION_PLAN = ["FRONT", "FRONT", "FRONT", "LEFT", "RIGHT"]
TARGET_SAMPLES = 5
WINDOW_NAME = "Collect Data"


def extract_keypoints(results):
    # TODO(Review-p5): 화면 이탈 시 0.0 으로 채워진 결측치는 모델에서 좌측 상단으로 오인할 여지가 있습니다.
    # 향후 모델 성능에 문제가 생길 경우 preprocess.py에서 보간(Interpolation) 처리를 고려하겠습니다.
    features = []

    if results.left_hand_landmarks:
        for lm in results.left_hand_landmarks.landmark:
            features.extend([lm.x, lm.y, lm.z])
    else:
        features.extend([0.0] * 21 * 3)

    if results.right_hand_landmarks:
        for lm in results.right_hand_landmarks.landmark:
            features.extend([lm.x, lm.y, lm.z])
    else:
        features.extend([0.0] * 21 * 3)

    pose_idxs = [0, 11, 12, 13, 14]
    if results.pose_landmarks:
        pose_landmarks = results.pose_landmarks.landmark
        for idx in pose_idxs:
            lm = pose_landmarks[idx]
            features.extend([lm.x, lm.y, lm.z])
    else:
        features.extend([0.0] * len(pose_idxs) * 3)

    return np.array(features, dtype=np.float32)


def discover_categories(data_dir):
    categories = []
    if not os.path.isdir(data_dir):
        return categories

    for name in sorted(os.listdir(data_dir)):
        cat_path = os.path.join(data_dir, name)
        if not os.path.isdir(cat_path):
            continue
        subdirs = [d for d in os.listdir(cat_path) if os.path.isdir(os.path.join(cat_path, d))]
        if subdirs:
            categories.append(name)
    return categories


def choose_from_list(title, options):
    if not options:
        raise ValueError(f"No options for {title}")

    print(f"\n[{title}]")
    for i, opt in enumerate(options, start=1):
        print(f"{i}. {opt}")

    while True:
        raw = input(f"Select {title} (1-{len(options)}, q=quit): ").strip()
        if raw.lower() == "q":
            raise SystemExit
        if raw.isdigit():
            idx = int(raw)
            if 1 <= idx <= len(options):
                return options[idx - 1]
        print("Invalid input. Try again.")


def count_npy_files(label_dir):
    if not os.path.isdir(label_dir):
        return 0
    return sum(1 for n in os.listdir(label_dir) if n.endswith(".npy"))


def read_key(delay=20):
    return cv2.waitKey(delay) & 0xFF


def is_start_key(key):
    # Start keys: s/S, Enter, Space
    return key in (ord("s"), ord("S"), 13, 32)


def is_quit_key(key):
    # Quit keys: q/Q, Esc
    return key in (ord("q"), ord("Q"), 27)


def select_target_gui(categories):
    result = {"category": None, "label": None}
    root = tk.Tk()
    root.title("Select Data Target")
    root.geometry("700x500")
    root.resizable(False, False)

    tk.Label(root, text="Category", font=("Arial", 12, "bold")).place(x=20, y=10)
    tk.Label(root, text="Label (existing samples)", font=("Arial", 12, "bold")).place(x=240, y=10)

    category_list = tk.Listbox(root, width=26, height=18, exportselection=False)
    category_list.place(x=20, y=40)
    label_list = tk.Listbox(root, width=55, height=18, exportselection=False)
    label_list.place(x=240, y=40)

    info_var = tk.StringVar(value="Choose category and label.")
    tk.Label(root, textvariable=info_var, justify="left", fg="#0b5", font=("Arial", 11)).place(x=20, y=360)

    labels_cache = []

    for c in categories:
        category_list.insert(tk.END, c)

    def refresh_labels(_evt=None):
        label_list.delete(0, tk.END)
        labels_cache.clear()

        sel = category_list.curselection()
        if not sel:
            info_var.set("Choose category and label.")
            return

        category = category_list.get(sel[0])
        label_root = os.path.join(DATA_DIR, category)
        labels = sorted([d for d in os.listdir(label_root) if os.path.isdir(os.path.join(label_root, d))])
        for label in labels:
            cnt = count_npy_files(os.path.join(label_root, label))
            labels_cache.append(label)
            label_list.insert(tk.END, f"{label:<24} ({cnt}/{TARGET_SAMPLES})")
        info_var.set(f"Category: {category} | labels: {len(labels)}")

    def on_label_click(_evt=None):
        csel = category_list.curselection()
        lsel = label_list.curselection()
        if not csel or not lsel:
            return
        category = category_list.get(csel[0])
        label = labels_cache[lsel[0]]
        cnt = count_npy_files(os.path.join(DATA_DIR, category, label))
        info_var.set(
            f"Selected: {category}/{label} | existing={cnt} | "
            f"target={TARGET_SAMPLES} | remaining={max(0, TARGET_SAMPLES - cnt)} | "
            f"next session={get_next_session(os.path.join(DATA_DIR, category, label))}"
        )

    def on_confirm():
        csel = category_list.curselection()
        lsel = label_list.curselection()
        if not csel:
            messagebox.showwarning("Select category", "Please select a category.")
            return
        if not lsel:
            messagebox.showwarning("Select label", "Please select a label.")
            return
        result["category"] = category_list.get(csel[0])
        result["label"] = labels_cache[lsel[0]]
        root.destroy()

    def on_cancel():
        root.destroy()

    category_list.bind("<<ListboxSelect>>", refresh_labels)
    label_list.bind("<<ListboxSelect>>", on_label_click)

    tk.Button(root, text="Start Collection", width=20, command=on_confirm).place(x=240, y=430)
    tk.Button(root, text="Cancel", width=12, command=on_cancel).place(x=420, y=430)

    if categories:
        category_list.selection_set(0)
        refresh_labels()

    root.mainloop()
    return result["category"], result["label"]


def get_next_session(label_dir):
    os.makedirs(label_dir, exist_ok=True)
    sessions = []
    for name in os.listdir(label_dir):
        if not name.endswith(".npy"):
            continue
        stem = os.path.splitext(name)[0]
        parts = stem.split('-')
        if len(parts) >= 1 and parts[0].isdigit():
            sessions.append(int(parts[0]))
    return max(sessions) + 1 if sessions else 1


def draw_guide_layer(frame, results):
    h, w = frame.shape[:2]

    color_glow = (200, 200, 0)
    color_core = (255, 255, 0)

    cx = w // 2

    # ── 머리: 타원(원형) ───────────────────────────────────
    head_cx   = cx
    head_cy   = int(h * 0.34)          # 머리 중심 Y (화면 34%)
    head_rx   = int(w * 0.09)          # 가로 반경
    head_ry   = int(h * 0.09)          # 세로 반경  → 0.25 ~ 0.43h 범위
    cv2.ellipse(frame, (head_cx, head_cy), (head_rx, head_ry),
                0, 0, 360, color_glow, 7)
    cv2.ellipse(frame, (head_cx, head_cy), (head_rx, head_ry),
                0, 0, 360, color_core, 3)

    # ── 몸통 + 팔: 폴리라인 (목부터 아래) ─────────────────
    body_left = [
        (cx - int(w * 0.04), int(h * 0.46)),   # Neck top
        (cx - int(w * 0.04), int(h * 0.50)),   # Neck bottom
        (cx - int(w * 0.09), int(h * 0.52)),   # Inner shoulder
        (cx - int(w * 0.16), int(h * 0.55)),   # Outer shoulder
        (cx - int(w * 0.21), int(h * 0.60)),   # Upper arm
        (cx - int(w * 0.24), int(h * 0.71)),   # Mid arm
        (cx - int(w * 0.26), int(h * 0.93)),   # Wrist
    ]
    body_right = [(cx + (cx - x), y) for x, y in body_left]

    # 왼쪽 손목 → 목 → 오른쪽 손목 순서로 한 줄 연결
    body_pts = list(reversed(body_left)) + body_right[1:]
    body_pts = np.array(body_pts, np.int32).reshape((-1, 1, 2))
    cv2.polylines(frame, [body_pts], isClosed=False, color=color_glow, thickness=7)
    cv2.polylines(frame, [body_pts], isClosed=False, color=color_core, thickness=3)

    if not results.pose_landmarks:
        return "NO POSE", (0, 0, 255)

    lm = results.pose_landmarks.landmark
    nose   = lm[0]
    left_sh  = lm[11]
    right_sh = lm[12]

    nose_ok      = 0.38 <= nose.x <= 0.62 and 0.15 <= nose.y <= 0.41
    shoulder_y_ok = 0.33 <= left_sh.y <= 0.65 and 0.33 <= right_sh.y <= 0.65
    shoulder_w   = abs(left_sh.x - right_sh.x)
    shoulder_w_ok = 0.12 <= shoulder_w <= 0.55

    if nose_ok and shoulder_y_ok and shoulder_w_ok:
        return "ALIGN OK", (0, 255, 0)
    return "ADJUST POSITION", (0, 165, 255)


def draw_common_ui(frame, category, label, sample_idx, orient, msg, status_text, status_color, existing_count):
    cv2.putText(frame, f"{category}/{label}", (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
    cv2.putText(frame, f"sample {sample_idx} | {orient}", (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 220, 0), 2)
    cv2.putText(frame, msg, (20, 105), cv2.FONT_HERSHEY_SIMPLEX, 0.75, (0, 255, 0), 2)
    cv2.putText(frame, f"guide: {status_text}", (20, 140), cv2.FONT_HERSHEY_SIMPLEX, 0.75, status_color, 2)
    cv2.putText(frame, f"existing: {existing_count}", (20, 175), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
    cv2.putText(frame, "S/Enter/Space:start", (20, 205), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 200, 255), 2)
    cv2.putText(frame, "Q/Esc:quit", (20, 232), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 200, 255), 2)


def countdown(cap, holistic, category, label, sample_idx, orient, existing_count, seconds_list):
    for sec in seconds_list:
        t_end = time.time() + 1.0
        while time.time() < t_end:
            ret, frame = cap.read()
            if not ret:
                continue
            frame = cv2.flip(frame, 1)
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)
            status_text, status_color = draw_guide_layer(frame, results)
            
            msg = "Auto Starting..." if len(seconds_list) > 3 else "Get ready..."
            draw_common_ui(
                frame, category, label, sample_idx, orient,
                msg, status_text, status_color, existing_count
            )
            cv2.putText(
                frame,
                str(sec),
                (frame.shape[1] // 2 - 20, frame.shape[0] // 2),
                cv2.FONT_HERSHEY_SIMPLEX,
                3.5,
                (0, 255, 0),
                5
            )
            cv2.imshow(WINDOW_NAME, frame)
            if is_quit_key(read_key(20)):
                raise SystemExit


def capture_one_sequence(cap, holistic, category, label, sample_idx, orient, existing_count, auto_start=False):
    sequence_data = []
    orient_msg = {
        "FRONT": "Look FRONT",
        "LEFT": "Turn slightly LEFT",
        "RIGHT": "Turn slightly RIGHT",
    }[orient]
    
    if auto_start:
        countdown(cap, holistic, category, label, sample_idx, orient, existing_count, [4, 3, 2, 1])
    else:
        while True:
            ret, frame = cap.read()
            if not ret:
                continue

            frame = cv2.flip(frame, 1)
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(rgb)

            status_text, status_color = draw_guide_layer(frame, results)
            mp_drawing.draw_landmarks(frame, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
            mp_drawing.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
            draw_common_ui(
                frame, category, label, sample_idx, orient,
                f"{orient_msg} and press S", status_text, status_color, existing_count
            )

            cv2.imshow(WINDOW_NAME, frame)
            key = read_key(20)
            if is_start_key(key):
                countdown(cap, holistic, category, label, sample_idx, orient, existing_count, [3, 2, 1])
                break
            if is_quit_key(key):
                raise SystemExit

    # TODO(Review-p4): 현재 PC 성능에 따라 60프레임 수집의 실제 경과 시간(Delta Time)이 다를 수 있습니다.
    # 추후 LSTM 학습 시 시퀀스 속도 불일치 노이즈가 심하다면, 프레임 간 타임스탬프를 함께 기록하거나 비동기 처리 도입을 고려하겠습니다.
    frame_idx = 0
    while len(sequence_data) < SEQ_LEN:
        ret, frame = cap.read()
        if not ret:
            continue

        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic.process(rgb)
        sequence_data.append(extract_keypoints(results))
        frame_idx += 1

        status_text, status_color = draw_guide_layer(frame, results)
        mp_drawing.draw_landmarks(frame, results.left_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
        mp_drawing.draw_landmarks(frame, results.right_hand_landmarks, mp_holistic.HAND_CONNECTIONS)
        mp_drawing.draw_landmarks(frame, results.pose_landmarks, mp_holistic.POSE_CONNECTIONS)
        draw_common_ui(
            frame, category, label, sample_idx, orient,
            f"Recording {len(sequence_data)}/{SEQ_LEN}",
            status_text,
            status_color,
            existing_count
        )

        # 녹화 초반 15 프레임 동안 중립 자세 안내 표시
        if frame_idx <= 15:
            h, w = frame.shape[:2]
            cv2.putText(
                frame,
                "Start from NEUTRAL position!",
                (w // 2 - 210, h // 2 + 60),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.85,
                (0, 120, 255),
                2,
            )

        cv2.imshow(WINDOW_NAME, frame)
        if is_quit_key(read_key(20)):
            raise SystemExit

    return np.array(sequence_data, dtype=np.float32)


def main():
    categories = discover_categories(DATA_DIR)
    if not categories:
        raise FileNotFoundError("No category folders found under data/raw")

    category = None
    label = None
    try:
        category, label = select_target_gui(categories)
    except Exception:
        # Fallback to CLI if GUI is not available in environment.
        category = choose_from_list("Category", categories)
        label_root = os.path.join(DATA_DIR, category)
        labels = sorted([d for d in os.listdir(label_root) if os.path.isdir(os.path.join(label_root, d))])
        label = choose_from_list("Label", labels)

    if not category or not label:
        print("Cancelled.")
        return

    label_root = os.path.join(DATA_DIR, category)
    target_dir = os.path.join(label_root, label)
    os.makedirs(target_dir, exist_ok=True)
    start_session = get_next_session(target_dir)
    existing_count = count_npy_files(target_dir)
    if existing_count >= TARGET_SAMPLES:
        try:
            proceed = messagebox.askyesno(
                "Target reached",
                f"{category}/{label} already has {existing_count} samples "
                f"(target: {TARGET_SAMPLES}). Add more anyway?"
            )
            if not proceed:
                print("Cancelled (target already reached).")
                return
        except Exception:
            print(
                f"Warning: {category}/{label} already has {existing_count} samples "
                f"(target: {TARGET_SAMPLES})."
            )

    print(f"\nTarget: {category}/{label}")
    print(f"Plan: {ORIENTATION_PLAN} (total {len(ORIENTATION_PLAN)} samples)")
    print(f"Existing files: {existing_count}/{TARGET_SAMPLES}")
    print(f"Remaining to target: {max(0, TARGET_SAMPLES - existing_count)}")
    print(f"Next session starts at: {start_session}-1")
    print("Press S in window to start each take. Press Q to quit.\n")

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        raise RuntimeError("Cannot open webcam.")
        
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    try:
        with mp_holistic.Holistic(
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        ) as holistic:
            for i, orient in enumerate(ORIENTATION_PLAN):
                take_num = i + 1
                seq_label = f"{start_session}-{take_num}"
                is_auto = (i > 0)
                data = capture_one_sequence(cap, holistic, category, label, seq_label, orient, existing_count, auto_start=is_auto)
                save_path = os.path.join(target_dir, f"{seq_label}.npy")
                np.save(save_path, data)
                print(f"saved: {save_path}, shape={data.shape}, orient={orient}")
                existing_count += 1
    finally:
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
