import os
import random
from glob import glob

import numpy as np
import torch
from sklearn.model_selection import train_test_split
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler


TARGET_CLASSES = [
    "기차",
    "장난감",
    "놀다",
    "가다",
    "일어나다",
    "병원",
    "조심",
]
TARGET_VARIANTS = {
    "가다": ["가다4"],
    "일어나다": ["일어나다2"],
}
MAX_FILES_PER_CLASS = {
    "기차": 60,
    "장난감": 60,
    "놀다": 60,
    "병원": 60,
    "조심": 60,
}
TARGET_SAMPLES_PER_CLASS = 80
USE_AUGMENTATION = True
AUGMENT_SEED = 2026
NOISE_STD = 0.01
TIME_SCALE_RANGE = (0.9, 1.1)
TIME_SHIFT_RANGE = (-2, 2)
FILE_SAMPLE_SEED = 42
PRIORITY_FILE_KEYWORDS = tuple(
    keyword.strip()
    for keyword in os.environ.get("V5_PRIORITY_FILE_KEYWORDS", "_FRONT,_LEFT,_RIGHT").split(",")
    if keyword.strip()
)
INPUT_DIM = 332
LANDMARK_DIM = 282
DISTANCE_DIM = 50
LEFT_SHOULDER_IDX = 90
RIGHT_SHOULDER_IDX = 91
MIN_NONZERO_RATIO = 0.01


class KSLDatasetV5(Dataset):
    def __init__(self, data_list, labels, max_len=30, input_dim=INPUT_DIM):
        self.data_list = data_list
        self.labels = labels
        self.max_len = max_len
        self.input_dim = input_dim

    def __len__(self):
        return len(self.data_list)

    def __getitem__(self, idx):
        item = self.data_list[idx]
        label = self.labels[idx]
        if isinstance(item, dict):
            file_path = item["path"]
            augment = item.get("augment", False)
            augment_seed = item.get("seed", idx)
        else:
            file_path = item
            augment = False
            augment_seed = idx

        data = np.load(file_path)
        if augment:
            data = self.augment_sequence(data, augment_seed)
        data = self.normalize_features(data)
        padded_data, valid_length = self.adjust_sequence_length(data)

        padded_data = np.array(padded_data, dtype=np.float32).copy()
        return (
            torch.from_numpy(padded_data).float(),
            torch.tensor(label, dtype=torch.long),
            torch.tensor(valid_length, dtype=torch.long),
        )

    def augment_sequence(self, data, seed):
        if data.ndim != 2 or data.shape[0] < 2 or data.shape[1] != self.input_dim:
            return data

        rng = np.random.default_rng(seed)
        augmented = np.array(data, dtype=np.float32).copy()
        augmented = self.temporal_resample(augmented, float(rng.uniform(*TIME_SCALE_RANGE)))

        shift = int(rng.integers(TIME_SHIFT_RANGE[0], TIME_SHIFT_RANGE[1] + 1))
        if shift > 0:
            augmented = np.vstack([np.repeat(augmented[:1], shift, axis=0), augmented[:-shift]])
        elif shift < 0:
            shift_abs = abs(shift)
            augmented = np.vstack([augmented[shift_abs:], np.repeat(augmented[-1:], shift_abs, axis=0)])

        noise = rng.normal(0.0, NOISE_STD, size=augmented.shape).astype(np.float32)
        return (augmented + noise).astype(np.float32)

    def temporal_resample(self, data, scale):
        frames = data.shape[0]
        scaled_frames = max(2, int(round(frames * scale)))
        src_positions = np.linspace(0, frames - 1, scaled_frames)
        scaled = np.zeros((scaled_frames, self.input_dim), dtype=np.float32)

        for i, pos in enumerate(src_positions):
            low = int(np.floor(pos))
            high = int(np.ceil(pos))
            weight = pos - low
            if low == high:
                scaled[i] = data[low]
            else:
                scaled[i] = data[low] * (1 - weight) + data[high] * weight

        target_positions = np.linspace(0, scaled_frames - 1, frames)
        restored = np.zeros((frames, self.input_dim), dtype=np.float32)
        for i, pos in enumerate(target_positions):
            low = int(np.floor(pos))
            high = int(np.ceil(pos))
            weight = pos - low
            if low == high:
                restored[i] = scaled[low]
            else:
                restored[i] = scaled[low] * (1 - weight) + scaled[high] * weight

        return restored

    def normalize_features(self, data):
        """
        Feature layout:
        - first 282 dims: 94 landmark points x (x, y, z)
        - last 50 dims: hand-tip to face-anchor distances
        """
        if len(data) == 0:
            return data

        if data.ndim != 2 or data.shape[1] != self.input_dim:
            return np.zeros((0, self.input_dim), dtype=np.float32)

        normalized_data = np.zeros_like(data, dtype=np.float32)

        for i in range(data.shape[0]):
            frame = data[i]
            landmarks = frame[:LANDMARK_DIM].reshape(-1, 3)
            distances = frame[LANDMARK_DIM:]

            l_shoulder = landmarks[LEFT_SHOULDER_IDX]
            r_shoulder = landmarks[RIGHT_SHOULDER_IDX]

            shoulder_center = (l_shoulder + r_shoulder) / 2.0
            shoulder_width = np.linalg.norm(l_shoulder - r_shoulder)
            if shoulder_width < 1e-6:
                shoulder_width = 1.0

            normalized_landmarks = (landmarks - shoulder_center) / shoulder_width
            normalized_distances = distances / shoulder_width
            normalized_data[i] = np.concatenate(
                [normalized_landmarks.flatten(), normalized_distances],
            )

        return normalized_data

    def adjust_sequence_length(self, data):
        if len(data.shape) < 2 or data.shape[1] != self.input_dim:
            return np.zeros((self.max_len, self.input_dim), dtype=np.float32), 0

        frames = data.shape[0]
        if frames == 0:
            return np.zeros((self.max_len, self.input_dim), dtype=np.float32), 0

        if frames == self.max_len:
            return np.array(data, dtype=np.float32), self.max_len

        if frames > self.max_len:
            indices = np.linspace(0, frames - 1, self.max_len, dtype=int)
            sampled = data[indices]
            return np.array(sampled, dtype=np.float32), self.max_len

        if frames < 15:
            indices = np.linspace(0, frames - 1, self.max_len)
            upsampled = np.zeros((self.max_len, self.input_dim), dtype=np.float32)

            for i, float_idx in enumerate(indices):
                low = int(np.floor(float_idx))
                high = int(np.ceil(float_idx))
                weight = float_idx - low

                if low == high:
                    upsampled[i] = data[low]
                else:
                    upsampled[i] = data[low] * (1 - weight) + data[high] * weight

            return upsampled, self.max_len

        pad_len = self.max_len - frames
        last_frame = data[-1:]
        padding = np.repeat(last_frame, pad_len, axis=0)
        padded = np.vstack((data, padding))
        return np.array(padded, dtype=np.float32), self.max_len


def _find_class_files(class_dir, allowed_variants=None):
    # Variants are stored under the representative class.
    if allowed_variants:
        files = []
        for variant_name in allowed_variants:
            variant_dir = os.path.join(class_dir, variant_name)
            files.extend(glob(os.path.join(variant_dir, "**", "*.npy"), recursive=True))
        return sorted(files)

    return sorted(glob(os.path.join(class_dir, "**", "*.npy"), recursive=True))


def _is_usable_npy(file_path, input_dim=INPUT_DIM):
    try:
        data = np.load(file_path, mmap_mode="r")
        if data.ndim != 2 or data.shape[1] != input_dim or data.shape[0] == 0:
            return False, f"bad shape={tuple(data.shape)}"

        sample = np.asarray(data[: min(len(data), 30)])
        nonzero_ratio = float(np.count_nonzero(sample) / max(sample.size, 1))
        if nonzero_ratio < MIN_NONZERO_RATIO:
            return False, f"near-zero nonzero_ratio={nonzero_ratio:.4f}, shape={tuple(data.shape)}"

        return True, None
    except Exception as e:
        return False, f"load error={e}"


def _is_priority_file(file_path):
    return any(keyword in os.path.basename(file_path) for keyword in PRIORITY_FILE_KEYWORDS)


def _limit_files_for_class(class_name, files):
    max_files = MAX_FILES_PER_CLASS.get(class_name)
    if max_files is None or len(files) <= max_files:
        return files

    priority_files = [file_path for file_path in files if _is_priority_file(file_path)]
    priority_file_set = set(priority_files)
    remaining_files = [file_path for file_path in files if file_path not in priority_file_set]

    rng = random.Random(FILE_SAMPLE_SEED)
    rng.shuffle(priority_files)
    rng.shuffle(remaining_files)

    selected_files = priority_files[:max_files]
    if len(selected_files) < max_files:
        selected_files.extend(remaining_files[: max_files - len(selected_files)])

    return sorted(selected_files)


def _build_class_lists(
    data_dir,
    target_classes,
    input_dim=INPUT_DIM,
    validate_files=True,
    target_variants=None,
):
    classes = []
    all_files = []
    all_labels = []
    class_counts = {}
    variant_counts = {}
    skipped_files = []

    for class_name in target_classes:
        class_dir = os.path.join(data_dir, class_name)
        if not os.path.isdir(class_dir):
            continue

        allowed_variants = target_variants.get(class_name) if target_variants else None
        candidate_files = _find_class_files(class_dir, allowed_variants=allowed_variants)
        files = []
        for file_path in candidate_files:
            if not validate_files:
                files.append(file_path)
                continue

            is_usable, reason = _is_usable_npy(file_path, input_dim=input_dim)
            if is_usable:
                files.append(file_path)
            else:
                skipped_files.append((file_path, reason))

        if not files:
            continue

        files = _limit_files_for_class(class_name, files)

        label_idx = len(classes)
        classes.append(class_name)
        class_counts[class_name] = len(files)
        all_files.extend(files)
        all_labels.extend([label_idx] * len(files))

        for file_path in files:
            rel_path = os.path.relpath(file_path, class_dir)
            variant_name = rel_path.split(os.sep)[0] if os.sep in rel_path else class_name
            variant_counts[f"{class_name}/{variant_name}"] = variant_counts.get(f"{class_name}/{variant_name}", 0) + 1

    return classes, all_files, all_labels, class_counts, variant_counts, skipped_files


def _can_stratify(labels, num_classes):
    if len(labels) < num_classes * 2:
        return False
    label_counts = np.bincount(labels, minlength=num_classes)
    return bool(np.all(label_counts >= 2))


def _make_augmented_train_items(x_train, y_train, classes):
    if not USE_AUGMENTATION:
        return x_train, y_train, {}

    rng = random.Random(AUGMENT_SEED)
    train_items = list(x_train)
    train_labels = list(y_train)
    augmentation_counts = {}

    for class_idx, class_name in enumerate(classes):
        class_files = [path for path, label in zip(x_train, y_train) if label == class_idx]
        if not class_files:
            continue

        current_count = len(class_files)
        needed = max(0, TARGET_SAMPLES_PER_CLASS - current_count)
        augmentation_counts[class_name] = needed

        for aug_idx in range(needed):
            source_path = class_files[aug_idx % len(class_files)]
            train_items.append(
                {
                    "path": source_path,
                    "augment": True,
                    "seed": rng.randint(0, 2**31 - 1),
                }
            )
            train_labels.append(class_idx)

    return train_items, train_labels, augmentation_counts


def create_dataloaders_v5(
    data_dir,
    batch_size=16,
    max_len=30,
    input_dim=INPUT_DIM,
    target_classes=None,
    val_ratio=0.2,
    validate_files=True,
    balanced_sampling=True,
    target_variants=None,
):
    target_classes = target_classes or TARGET_CLASSES
    target_variants = TARGET_VARIANTS if target_variants is None else target_variants
    classes, all_files, all_labels, class_counts, variant_counts, skipped_files = _build_class_lists(
        data_dir,
        target_classes,
        input_dim=input_dim,
        validate_files=validate_files,
        target_variants=target_variants,
    )

    if not classes:
        raise ValueError(f"No valid class directories found under: {data_dir}")

    if len(classes) < 2:
        raise ValueError("At least two classes are required for training.")

    if len(all_files) != len(all_labels):
        raise ValueError("File list and label list are misaligned.")

    label_counts = np.bincount(all_labels, minlength=len(classes))
    class_weights = len(all_labels) / (len(classes) * (label_counts + 1e-6))
    class_weights_tensor = torch.tensor(class_weights, dtype=torch.float32)

    stratify = all_labels if _can_stratify(all_labels, len(classes)) else None
    X_train, X_val, y_train, y_val = train_test_split(
        all_files,
        all_labels,
        test_size=val_ratio,
        random_state=42,
        stratify=stratify,
    )

    X_train_items, y_train_items, augmentation_counts = _make_augmented_train_items(X_train, y_train, classes)

    train_dataset = KSLDatasetV5(X_train_items, y_train_items, max_len=max_len, input_dim=input_dim)
    val_dataset = KSLDatasetV5(X_val, y_val, max_len=max_len, input_dim=input_dim)

    train_sampler = None
    train_shuffle = True
    if balanced_sampling:
        train_label_counts = np.bincount(y_train_items, minlength=len(classes))
        sample_weights = [1.0 / max(train_label_counts[label], 1) for label in y_train_items]
        train_sampler = WeightedRandomSampler(
            weights=torch.tensor(sample_weights, dtype=torch.double),
            num_samples=len(y_train_items),
            replacement=True,
        )
        train_shuffle = False

    train_loader = DataLoader(
        train_dataset,
        batch_size=batch_size,
        shuffle=train_shuffle,
        sampler=train_sampler,
    )
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    train_label_counts = np.bincount(y_train_items, minlength=len(classes))
    val_label_counts = np.bincount(y_val, minlength=len(classes))
    priority_file_counts = {
        class_name: sum(1 for file_path, label in zip(all_files, all_labels) if label == idx and _is_priority_file(file_path))
        for idx, class_name in enumerate(classes)
    }

    stats = {
        "class_counts": class_counts,
        "variant_counts": variant_counts,
        "priority_file_keywords": PRIORITY_FILE_KEYWORDS,
        "priority_file_counts": priority_file_counts,
        "train_class_counts": {classes[idx]: int(train_label_counts[idx]) for idx in range(len(classes))},
        "val_class_counts": {classes[idx]: int(val_label_counts[idx]) for idx in range(len(classes))},
        "train_size": len(X_train_items),
        "original_train_size": len(X_train),
        "val_size": len(X_val),
        "total_size": len(X_train_items) + len(X_val),
        "original_total_size": len(all_files),
        "stratified_split": stratify is not None,
        "skipped_files": skipped_files,
        "balanced_sampling": balanced_sampling,
        "target_variants": target_variants,
        "max_files_per_class": MAX_FILES_PER_CLASS,
        "file_sample_seed": FILE_SAMPLE_SEED,
        "use_augmentation": USE_AUGMENTATION,
        "target_samples_per_class": TARGET_SAMPLES_PER_CLASS,
        "augmentation_counts": augmentation_counts,
        "augmentation": {
            "seed": AUGMENT_SEED,
            "noise_std": NOISE_STD,
            "time_scale_range": TIME_SCALE_RANGE,
            "time_shift_range": TIME_SHIFT_RANGE,
        },
    }

    return train_loader, val_loader, classes, class_weights_tensor, stats
