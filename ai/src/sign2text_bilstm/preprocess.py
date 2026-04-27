import os
import json
import numpy as np
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split

# ── 설정 ─────────────────────────────────────────────────
RAW_DATA_DIR = os.path.join("data", "raw")
OUT_DIR   = os.path.join("data", "processed")
SEQ_LEN   = 60
N_FEAT    = 141
TAKES     = 5          # 세션당 테이크 수 (1-1 ~ 1-5)
AUG_PER_SAMPLE = 3     # 원본 1개당 생성할 증강 샘플 수 (총 4배: 원본1 + 증강3)

def get_dynamic_labels(raw_dir):
    label_dirs = {}
    if not os.path.isdir(raw_dir):
        return label_dirs
    # ACTIONS, NONE, EMOTIONS 등의 상위 카테고리 폴더 순회
    for category in os.listdir(raw_dir):
        cat_path = os.path.join(raw_dir, category)
        if not os.path.isdir(cat_path):
            continue
        # 카테고리 내부의 단어 폴더 순회
        for label in sorted(os.listdir(cat_path)):
            label_path = os.path.join(cat_path, label)
            if os.path.isdir(label_path):
                label_dirs[label] = label_path
    return label_dirs

LABEL_DIRS = get_dynamic_labels(RAW_DATA_DIR)
LABELS = list(LABEL_DIRS.keys())


# ── 세션 탐색 ─────────────────────────────────────────────
def find_sessions(label_dir: str):
    """
    label_dir 폴더에서 세션을 탐색합니다.
    반환: (complete_sessions, incomplete_sessions)
      - complete_sessions  : [session_num, ...] 1-1~1-5 전부 존재
      - incomplete_sessions: [file_path, ...]   일부 테이크만 존재
    """
    npy_files = [f for f in os.listdir(label_dir) if f.endswith(".npy")]
    session_map: dict[int, list[int]] = {}

    for name in npy_files:
        stem = os.path.splitext(name)[0]
        parts = stem.split("-")
        if len(parts) != 2 or not parts[0].isdigit() or not parts[1].isdigit():
            continue
        s_num, t_num = int(parts[0]), int(parts[1])
        session_map.setdefault(s_num, []).append(t_num)

    complete, incomplete_paths = [], []
    for s_num, takes in sorted(session_map.items()):
        expected = set(range(1, TAKES + 1))
        if expected.issubset(set(takes)):
            complete.append(s_num)
        else:
            for t_num in takes:
                path = os.path.join(label_dir, f"{s_num}-{t_num}.npy")
                incomplete_paths.append(path)

    return complete, incomplete_paths


def load_npy(path: str):
    arr = np.load(path)
    if arr.shape != (SEQ_LEN, N_FEAT):
        print(f"  [SKIP] shape mismatch {arr.shape}: {path}")
        return None
    return arr.astype(np.float32)


# ── 데이터 증강 ───────────────────────────────────────────
def augment(seq: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """seq: (60, 141) 형태의 원본 시퀀스 → 증강 1가지 조합 적용"""
    aug = seq.copy()

    # ① 좌표 이동 (Translation) — X/Y 축 ±0.05
    shift = rng.uniform(-0.05, 0.05, size=(1, N_FEAT))
    aug = aug + shift

    # ② 크기 조절 (Scale) — 0.85 ~ 1.15
    scale = rng.uniform(0.85, 1.15)
    aug = aug * scale

    # ③ 소각도 회전 (Rotation) — ±5도, 화면 중심점(0.5, 0.5) 기준 X/Y 좌표쌍 회전
    angle = rng.uniform(-5, 5) * np.pi / 180
    cos_a, sin_a = np.cos(angle), np.sin(angle)
    cx, cy = 0.5, 0.5
    # 특징 벡터는 [lh(63), rh(63), pose(15)] 순서이므로 x/y 쌍만 회전
    for start in range(0, N_FEAT - 2, 3):   # (x, y, z) 트리플렛
        x = aug[:, start].copy() - cx
        y = aug[:, start + 1].copy() - cy
        aug[:, start]     = (cos_a * x - sin_a * y) + cx
        aug[:, start + 1] = (sin_a * x + cos_a * y) + cy

    # ④ 가우시안 노이즈 (Noise) — σ=0.005
    aug = aug + rng.normal(0, 0.005, size=aug.shape).astype(np.float32)

    # ⑤ 시간 스트레칭 (Time Warp) — 0.8x ~ 1.2x 속도 변형
    speed = rng.uniform(0.8, 1.2)
    src_len = int(SEQ_LEN * speed)
    src_len = max(10, min(src_len, SEQ_LEN * 2))
    src_indices = np.linspace(0, SEQ_LEN - 1, src_len)
    dst_indices = np.linspace(0, src_len - 1, SEQ_LEN)
    aug_warped = np.zeros_like(aug)
    for f in range(N_FEAT):
        aug_warped[:, f] = np.interp(dst_indices, np.arange(src_len),
                                     np.interp(src_indices, np.arange(SEQ_LEN), aug[:, f]))
    return aug_warped.astype(np.float32)


# ── 메인 ─────────────────────────────────────────────────
def main():
    # 기존 전처리 데이터 삭제 후 새로 생성 (stale 데이터 방지)
    if os.path.isdir(OUT_DIR):
        import shutil
        print(f"[INFO] 기존 전처리 데이터를 삭제합니다: {OUT_DIR}")
        shutil.rmtree(OUT_DIR)
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"[INFO] 새 전처리 데이터를 생성합니다: {OUT_DIR}\n")
    rng = np.random.default_rng(42)

    label_map = {label: idx for idx, label in enumerate(LABELS)}

    X_all_raw, y_all_raw = [], []

    for label in LABELS:
        label_dir = LABEL_DIRS[label]
        if not os.path.isdir(label_dir):
            print(f"[WARN] 폴더 없음, 건너뜀: {label_dir}")
            continue

        complete_sessions, incomplete_paths = find_sessions(label_dir)
        cls_idx = label_map[label]

        print(f"\n[{label}]  완전 세션={complete_sessions}  불완전 파일={len(incomplete_paths)}개")

        # ── 데이터 전부 읽어들이기 ──────────────────
        for s in complete_sessions:
            for t in range(1, TAKES + 1):
                path = os.path.join(label_dir, f"{s}-{t}.npy")
                arr = load_npy(path)
                if arr is not None:
                    X_all_raw.append(arr)
                    y_all_raw.append(cls_idx)

        for path in incomplete_paths:
            arr = load_npy(path)
            if arr is not None:
                X_all_raw.append(arr)
                y_all_raw.append(cls_idx)

    if not X_all_raw:
        raise ValueError("학습 데이터가 없습니다. 데이터를 먼저 수집하세요.")

    X_all = np.array(X_all_raw, dtype=np.float32)
    y_all = np.array(y_all_raw, dtype=np.int64)

    # ── Train / Val 무작위 분할 (8:2) ──────────────────
    X_train, X_val, y_train, y_val = train_test_split(
        X_all, y_all, test_size=0.2, random_state=42, stratify=y_all
    )

    # ── 데이터 증강 (Train 전용, 정규화 이전 수행!) ─────────────────────────
    X_aug, y_aug = [], []
    for i in range(len(X_train)):
        for _ in range(AUG_PER_SAMPLE):
            X_aug.append(augment(X_train[i], rng))
            y_aug.append(y_train[i])

    X_train = np.concatenate([X_train, np.array(X_aug, dtype=np.float32)], axis=0)
    y_train = np.concatenate([y_train, np.array(y_aug, dtype=np.int64)],   axis=0)

    # 셔플
    perm = rng.permutation(len(X_train))
    X_train, y_train = X_train[perm], y_train[perm]

    # ── Z-score 정규화 (Train 전체 기준 피팅) ──────────────────────
    scaler = StandardScaler()
    n_train, t, f = X_train.shape
    X_train_2d = X_train.reshape(-1, f)
    X_train_2d = scaler.fit_transform(X_train_2d)
    X_train = X_train_2d.reshape(n_train, t, f)

    if len(X_val) > 0:
        n_val = X_val.shape[0]
        X_val_2d = X_val.reshape(-1, f)
        X_val_2d = scaler.transform(X_val_2d)
        X_val = X_val_2d.reshape(n_val, t, f)

    # scaler 통계 저장 (추론 시 사용)
    np.save(os.path.join(OUT_DIR, "scaler_mean.npy"), scaler.mean_.astype(np.float32))
    np.save(os.path.join(OUT_DIR, "scaler_scale.npy"), scaler.scale_.astype(np.float32))

    # ── 저장 ─────────────────────────────────────────────
    np.save(os.path.join(OUT_DIR, "X_train.npy"), X_train)
    np.save(os.path.join(OUT_DIR, "y_train.npy"), y_train)
    np.save(os.path.join(OUT_DIR, "X_val.npy"),   X_val)
    np.save(os.path.join(OUT_DIR, "y_val.npy"),   y_val)

    with open(os.path.join(OUT_DIR, "label_map.json"), "w", encoding="utf-8") as f:
        json.dump(label_map, f, ensure_ascii=False, indent=2)

    print("\n" + "="*50)
    print(f"X_train : {X_train.shape}   y_train : {y_train.shape}")
    print(f"X_val   : {X_val.shape}     y_val   : {y_val.shape}")
    print(f"label_map: {label_map}")
    print(f"저장 위치: {OUT_DIR}")
    print("="*50)


if __name__ == "__main__":
    main()
