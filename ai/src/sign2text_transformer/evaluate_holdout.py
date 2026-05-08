import json
import os
from collections import Counter
from pathlib import Path

import numpy as np
import torch

from dataset import INPUT_DIM, KSLDatasetV5
from model import KSLTransformerV5


SCRIPT_DIR = Path(__file__).resolve().parent
MODELS_ROOT = SCRIPT_DIR / "models"
HOLDOUT_DIR = Path(os.environ.get("V5_HOLDOUT_DIR", SCRIPT_DIR / "holdout_test_v5"))
MODEL_DIR_NAME = os.environ.get("V5_MODEL_DIR", "")
MODEL_PATH = os.environ.get("V5_MODEL_PATH", "")
LABEL_MAP_PATH = os.environ.get("V5_LABEL_MAP_PATH", "")

DEFAULT_MODEL_DIRS = [
    "overfit_model_v5_7words_demo_stable",
    "7words_demo_stable",
    "overfit_model_v5_8words_no_oda_jada",
    "8words_new_1",
]
MAX_LEN = int(os.environ.get("V5_MAX_LEN", "30"))
BATCH_SIZE = int(os.environ.get("V5_HOLDOUT_BATCH_SIZE", "16"))
TOP_K = int(os.environ.get("V5_TOP_K", "3"))


def resolve_model_path(path_or_name):
    path = Path(path_or_name)
    if path.is_absolute():
        return path
    if path.exists():
        return path.resolve()
    return MODELS_ROOT / path


def choose_model_dir():
    if MODEL_DIR_NAME:
        return resolve_model_path(MODEL_DIR_NAME)

    for dirname in DEFAULT_MODEL_DIRS:
        candidate = MODELS_ROOT / dirname
        if candidate.exists():
            return candidate

    return MODELS_ROOT / DEFAULT_MODEL_DIRS[0]


def find_latest_numbered_pair(model_dir):
    model_dir = resolve_model_path(model_dir)
    pairs = []

    legacy_model = model_dir / "best_sign_model_v5.pt"
    legacy_label_map = model_dir / "label_map_v5.json"
    if legacy_model.exists() and legacy_label_map.exists():
        pairs.append((0, legacy_model, legacy_label_map))

    for model_path in model_dir.glob("best_sign_model_v5_*.pt"):
        suffix = model_path.stem.replace("best_sign_model_v5_", "")
        if not suffix.isdigit():
            continue

        label_map_path = model_dir / f"label_map_v5_{suffix}.json"
        config_path = model_dir / f"train_config_v5_{suffix}.json"
        if label_map_path.exists():
            pairs.append((int(suffix), model_path, label_map_path))
        elif config_path.exists():
            pairs.append((int(suffix), model_path, config_path))

    if not pairs:
        return None, None

    _, model_path, label_map_path = sorted(pairs, key=lambda item: item[0])[-1]
    return model_path, label_map_path


def find_model_files():
    if MODEL_PATH and LABEL_MAP_PATH:
        return resolve_model_path(MODEL_PATH), resolve_model_path(LABEL_MAP_PATH)

    model_dir = choose_model_dir()
    return find_latest_numbered_pair(model_dir)


def load_label_map(label_map_path):
    with open(label_map_path, "r", encoding="utf-8") as f:
        raw = json.load(f)

    if isinstance(raw, dict) and "classes" in raw:
        raw = raw["classes"]

    if isinstance(raw, dict):
        return [raw[str(idx)] for idx in range(len(raw))]

    if isinstance(raw, list):
        return raw

    raise ValueError(f"Unsupported label map format: {label_map_path}")


def collect_holdout_files(holdout_dir, classes):
    holdout_dir = Path(holdout_dir)
    class_to_idx = {class_name: idx for idx, class_name in enumerate(classes)}
    samples = []
    missing_classes = []
    unknown_dirs = []

    for class_name in classes:
        class_dir = holdout_dir / class_name
        if not class_dir.exists():
            missing_classes.append(class_name)
            continue

        files = sorted(class_dir.rglob("*.npy"))
        if not files:
            missing_classes.append(class_name)
            continue

        for file_path in files:
            samples.append((file_path, class_to_idx[class_name], class_name))

    if holdout_dir.exists():
        for path in sorted(holdout_dir.iterdir()):
            if path.is_dir() and path.name not in class_to_idx:
                unknown_dirs.append(path.name)

    return samples, missing_classes, unknown_dirs


def load_state_dict(model_path, device):
    try:
        return torch.load(model_path, map_location=device, weights_only=True)
    except TypeError:
        return torch.load(model_path, map_location=device)


def preprocess_file(file_path, dataset_helper):
    data = np.load(file_path)
    data = dataset_helper.normalize_features(data)
    data, valid_length = dataset_helper.adjust_sequence_length(data)
    if valid_length <= 0:
        raise ValueError("valid_length is 0")
    return torch.from_numpy(np.array(data, dtype=np.float32)), int(valid_length)


def format_topk(probs, classes, top_k=TOP_K):
    k = min(top_k, len(classes))
    top_indices = np.argsort(probs)[::-1][:k]
    return ", ".join(
        f"{classes[int(idx)]}:{float(probs[idx]):.3f}"
        for idx in top_indices
    )


def print_confusion_matrix(classes, labels, preds, present_indices):
    print("\n[Confusion Matrix]")
    header = "true \\ pred".ljust(14) + " ".join(classes[idx][:6].rjust(8) for idx in present_indices)
    print(header)
    for true_idx in present_indices:
        row = []
        for pred_idx in present_indices:
            row.append(sum(1 for y, p in zip(labels, preds) if y == true_idx and p == pred_idx))
        print(classes[true_idx].ljust(14) + " ".join(str(value).rjust(8) for value in row))

    outside = Counter()
    for y, p in zip(labels, preds):
        if p not in present_indices:
            outside[(classes[y], classes[p])] += 1

    if outside:
        print("\n[Predicted Outside Holdout Classes]")
        for (true_name, pred_name), count in outside.most_common():
            print(f"- {true_name} -> {pred_name}: {count}")


def evaluate():
    holdout_dir = Path(HOLDOUT_DIR)
    model_path, label_map_path = find_model_files()

    if model_path is None or not model_path.exists():
        print("V5 model file was not found.")
        print(f"Configured model dir: {choose_model_dir()}")
        return

    if label_map_path is None or not label_map_path.exists():
        print("V5 label map file was not found.")
        print(f"Configured model dir: {choose_model_dir()}")
        return

    if not holdout_dir.exists():
        print("Holdout directory was not found.")
        print(f"Configured holdout dir: {holdout_dir}")
        return

    classes = load_label_map(label_map_path)
    samples, missing_classes, unknown_dirs = collect_holdout_files(holdout_dir, classes)
    if not samples:
        print("No holdout npy files found for classes in label map.")
        print(f"Holdout dir: {holdout_dir}")
        print(f"Classes: {classes}")
        return

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = KSLTransformerV5(
        input_dim=INPUT_DIM,
        num_classes=len(classes),
        d_model=128,
        num_heads=8,
        num_layers=3,
    ).to(device)
    model.load_state_dict(load_state_dict(model_path, device))
    model.eval()

    print("[Holdout Evaluation]")
    print(f"- device: {device}")
    print(f"- model: {model_path}")
    print(f"- label map: {label_map_path}")
    print(f"- holdout dir: {holdout_dir}")
    print(f"- classes: {classes}")
    print(f"- files: {len(samples)}")
    print(f"- missing classes in holdout: {missing_classes}")
    if unknown_dirs:
        print(f"- ignored unknown holdout dirs: {unknown_dirs}")

    dataset_helper = KSLDatasetV5([], [], max_len=MAX_LEN, input_dim=INPUT_DIM)
    labels = []
    preds = []
    bad_files = []
    detailed_rows = []

    with torch.no_grad():
        for start in range(0, len(samples), BATCH_SIZE):
            batch_samples = samples[start : start + BATCH_SIZE]
            batch_data = []
            batch_lengths = []
            batch_labels = []
            batch_paths = []

            for file_path, label_idx, _ in batch_samples:
                try:
                    data, valid_length = preprocess_file(file_path, dataset_helper)
                    batch_data.append(data)
                    batch_lengths.append(valid_length)
                    batch_labels.append(label_idx)
                    batch_paths.append(file_path)
                except Exception as e:
                    bad_files.append((file_path, str(e)))

            if not batch_data:
                continue

            x = torch.stack(batch_data).to(device)
            lengths = torch.tensor(batch_lengths, dtype=torch.long, device=device)
            logits = model(x, lengths=lengths)
            probs = torch.softmax(logits, dim=1).cpu().numpy()
            batch_preds = probs.argmax(axis=1).tolist()

            for file_path, true_idx, pred_idx, prob_row in zip(
                batch_paths,
                batch_labels,
                batch_preds,
                probs,
            ):
                labels.append(true_idx)
                preds.append(pred_idx)
                detailed_rows.append((file_path, true_idx, pred_idx, prob_row))

    total = len(labels)
    correct = sum(1 for y, p in zip(labels, preds) if y == p)
    accuracy = 100.0 * correct / max(total, 1)

    present_indices = sorted(set(labels))
    print(f"\n[Summary]")
    print(f"- evaluated files: {total}")
    print(f"- skipped bad files: {len(bad_files)}")
    print(f"- accuracy: {accuracy:.2f}% ({correct}/{total})")

    print("\n[Per-Class Accuracy]")
    for idx in present_indices:
        class_total = sum(1 for y in labels if y == idx)
        class_correct = sum(1 for y, p in zip(labels, preds) if y == idx and p == idx)
        class_acc = 100.0 * class_correct / max(class_total, 1)
        print(f"- {classes[idx]}: {class_acc:.2f}% ({class_correct}/{class_total})")

    print_confusion_matrix(classes, labels, preds, present_indices)

    print("\n[Wrong Predictions]")
    wrong_count = 0
    for file_path, true_idx, pred_idx, prob_row in detailed_rows:
        if true_idx == pred_idx:
            continue
        wrong_count += 1
        rel_path = file_path.relative_to(holdout_dir)
        print(
            f"- {rel_path}: true={classes[true_idx]}, pred={classes[pred_idx]}, "
            f"top{min(TOP_K, len(classes))}=[{format_topk(prob_row, classes)}]"
        )

    if wrong_count == 0:
        print("- none")

    if bad_files:
        print("\n[Skipped Bad Files]")
        for file_path, reason in bad_files[:20]:
            print(f"- {file_path}: {reason}")


if __name__ == "__main__":
    evaluate()
