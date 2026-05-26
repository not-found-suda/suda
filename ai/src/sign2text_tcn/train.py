"""
V100 training example (v7 - 117 classes):

V6_DATA_DIR=./processed_npy_v7 \
V6_MODEL_DIR_NAME=v7_117words_tcn_balanced80 \
V6_MODEL_TYPE=tcn \
V6_EPOCHS=120 \
V6_BATCH_SIZE=16 \
V6_LR=0.001 \
V6_WEIGHT_DECAY=0.0 \
V6_MAX_FILES_PER_CLASS=80 \
V6_MAX_FILES_EXEMPT_CLASSES=병원,엄마,none \
V6_BALANCED_SAMPLER=1 \
V6_USE_AUGMENTATION=1 \
V6_NORMALIZE_MODE=none \
V6_TARGET_SAMPLES_PER_CLASS=120 \
V6_EARLY_STOPPING=1 \
V6_EARLY_STOPPING_PATIENCE=25 \
V6_NUM_WORKERS=2 \
V6_PIN_MEMORY=1 \
python train.py
"""

import copy
import inspect
import json
import os
import sys
from collections import Counter

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from sklearn.metrics import confusion_matrix, f1_score
from torch.optim.lr_scheduler import ReduceLROnPlateau

from dataset import create_dataloaders_v6
from model import build_ksl_model_v6
from target_words_v7 import TARGET_WORDS


class TeeLogger:
    """stdout/stderr를 터미널과 로그 파일에 동시에 출력."""

    def __init__(self, stream, log_file):
        self._stream = stream
        self._log = log_file

    def write(self, data):
        self._stream.write(data)
        self._stream.flush()
        self._log.write(data)
        self._log.flush()

    def flush(self):
        self._stream.flush()
        self._log.flush()

    def fileno(self):
        return self._stream.fileno()


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.environ.get("V6_DATA_DIR", os.path.join(SCRIPT_DIR, "processed_npy_v7"))
MODELS_ROOT = os.path.join(SCRIPT_DIR, "models")
MODEL_DIR_NAME = os.environ.get("V6_MODEL_DIR_NAME", "v7_117words_tcn_balanced80")
MODEL_TYPE = os.environ.get("V6_MODEL_TYPE", "tcn").strip().lower()
MODEL_FILE_BASENAME = "best_sign_model_v7"
LABEL_MAP_BASENAME = "label_map_v7"
CONFIG_BASENAME = "train_config_v7"
KEY_CLASSES = TARGET_WORDS


def env_value(name, default, legacy_name=None):
    if name in os.environ:
        return os.environ[name]
    if legacy_name and legacy_name in os.environ:
        return os.environ[legacy_name]
    return default


MIN_SAMPLES_PER_CLASS = int(env_value("V6_MIN_SAMPLES_PER_CLASS", "20", "V5_MIN_SAMPLES_PER_CLASS"))
EPOCHS = int(env_value("V6_EPOCHS", "120", "V5_EPOCHS"))
BATCH_SIZE = int(env_value("V6_BATCH_SIZE", "16", "V5_BATCH_SIZE"))
LEARNING_RATE = float(env_value("V6_LR", "0.001", "V5_LR"))
WEIGHT_DECAY = float(env_value("V6_WEIGHT_DECAY", "0.0", "V5_WEIGHT_DECAY"))
USE_CLASS_WEIGHTS = env_value("V6_USE_CLASS_WEIGHTS", "0", "V5_USE_CLASS_WEIGHTS") == "1"
USE_SCHEDULER = env_value("V6_USE_SCHEDULER", "0", "V5_USE_SCHEDULER") == "1"
SCHEDULER_FACTOR = float(env_value("V6_SCHEDULER_FACTOR", "0.5", "V5_SCHEDULER_FACTOR"))
SCHEDULER_PATIENCE = int(env_value("V6_SCHEDULER_PATIENCE", "12", "V5_SCHEDULER_PATIENCE"))
USE_BALANCED_SAMPLER = env_value("V6_BALANCED_SAMPLER", "1", "V5_BALANCED_SAMPLER") == "1"
SINGLE_BATCH_CHECK_STEPS = int(env_value("V6_SINGLE_BATCH_STEPS", "200", "V5_SINGLE_BATCH_STEPS"))
USE_EARLY_STOPPING = env_value("V6_EARLY_STOPPING", "1", "V5_EARLY_STOPPING") == "1"
EARLY_STOPPING_PATIENCE = int(env_value("V6_EARLY_STOPPING_PATIENCE", "25", "V5_EARLY_STOPPING_PATIENCE"))
EARLY_STOPPING_MIN_DELTA = float(env_value("V6_EARLY_STOPPING_MIN_DELTA", "0.001", "V5_EARLY_STOPPING_MIN_DELTA"))
INPUT_DIM = 332
MAX_LEN = 30
D_MODEL = int(env_value("V6_D_MODEL", "128", "V5_D_MODEL"))
NUM_HEADS = int(env_value("V6_NUM_HEADS", "8", "V5_NUM_HEADS"))
NUM_LAYERS = int(env_value("V6_NUM_LAYERS", "3", "V5_NUM_LAYERS"))
NUM_WORKERS = int(env_value("V6_NUM_WORKERS", "2", "V5_NUM_WORKERS"))
PIN_MEMORY = env_value("V6_PIN_MEMORY", "1", "V5_PIN_MEMORY") == "1"
PERSISTENT_WORKERS = env_value("V6_PERSISTENT_WORKERS", "1", "V5_PERSISTENT_WORKERS") == "1"


def build_model(num_classes):
    return build_ksl_model_v6(
        model_type=MODEL_TYPE,
        input_dim=INPUT_DIM,
        num_classes=num_classes,
        seq_len=MAX_LEN,
        d_model=D_MODEL,
        num_heads=NUM_HEADS,
        num_layers=NUM_LAYERS,
    )


def make_unique_model_dir(models_root, base_name):
    os.makedirs(models_root, exist_ok=True)
    existing_indices = []
    prefix = f"{base_name}_"
    for entry in os.scandir(models_root):
        if entry.is_dir() and entry.name.startswith(prefix):
            suffix = entry.name[len(prefix):]
            if suffix.isdigit():
                existing_indices.append(int(suffix))
    run_idx = max(existing_indices) + 1 if existing_indices else 1
    model_dir = os.path.join(models_root, f"{base_name}_{run_idx}")
    os.makedirs(model_dir, exist_ok=True)
    return model_dir, run_idx


def make_run_paths(model_dir):
    model_path = os.path.join(model_dir, f"{MODEL_FILE_BASENAME}.pt")
    label_map_path = os.path.join(model_dir, f"{LABEL_MAP_BASENAME}.json")
    config_path = os.path.join(model_dir, f"{CONFIG_BASENAME}.json")
    return model_path, label_map_path, config_path


def format_counter(counter, classes):
    return {classes[idx]: int(counter.get(idx, 0)) for idx in range(len(classes))}


def print_dataset_debug_info(classes, stats, class_weights):
    print("\n[Dataset]")
    print(f"- classes: {classes}")
    print(f"- total/train/val: {stats['total_size']}/{stats['train_size']}/{stats['val_size']}")
    print(
        f"- original total/train/val: "
        f"{stats['original_total_size']}/{stats['original_train_size']}/{stats['val_size']}"
    )
    print(f"- stratified split: {stats['stratified_split']}")
    print(f"- balanced sampler: {stats['balanced_sampling']}")
    print(f"- class counts: {stats['class_counts']}")
    print(f"- variant counts: {stats['variant_counts']}")
    print(f"- priority file keywords: {stats['priority_file_keywords']}")
    print(f"- priority file counts: {stats['priority_file_counts']}")
    print(f"- train class counts: {stats['train_class_counts']}")
    print(f"- val class counts: {stats['val_class_counts']}")
    print(f"- target variants: {stats['target_variants']}")
    print(f"- max files per class: {stats['max_files_per_class']}")
    print(f"- max files exempt classes: {stats['max_files_exempt_classes']}")
    print(f"- file sample seed: {stats['file_sample_seed']}")
    print(f"- use augmentation: {stats['use_augmentation']}")
    print(f"- normalize mode: {stats['normalize_mode']}")
    print(f"- target samples per class: {stats['target_samples_per_class']}")
    print(f"- augmentation counts: {stats['augmentation_counts']}")
    print(f"- augmentation params: {stats['augmentation']}")
    print(f"- dataloader: {stats['dataloader']}")
    print(f"- class weights: {[round(float(w), 4) for w in class_weights.tolist()]}")
    print(f"- skipped invalid npy files: {len(stats['skipped_files'])}")
    for file_path, reason in stats["skipped_files"][:20]:
        print(f"  skip: {file_path} ({reason})")

    low_sample_classes = {
        class_name: count
        for class_name, count in stats["class_counts"].items()
        if count < MIN_SAMPLES_PER_CLASS
    }
    if low_sample_classes:
        print(
            f"WARNING: classes below min sample count ({MIN_SAMPLES_PER_CLASS}): "
            f"{low_sample_classes}"
        )


def save_train_config(config_path, classes, stats, run_idx):
    config = {
        "run_idx": run_idx,
        "data_dir": DATA_DIR,
        "model_dir_name": MODEL_DIR_NAME,
        "model_type": MODEL_TYPE,
        "epochs": EPOCHS,
        "batch_size": BATCH_SIZE,
        "learning_rate": LEARNING_RATE,
        "weight_decay": WEIGHT_DECAY,
        "input_dim": INPUT_DIM,
        "max_len": MAX_LEN,
        "d_model": D_MODEL,
        "num_heads": NUM_HEADS,
        "num_layers": NUM_LAYERS,
        "num_workers": NUM_WORKERS,
        "pin_memory": PIN_MEMORY,
        "persistent_workers": PERSISTENT_WORKERS,
        "use_class_weights": USE_CLASS_WEIGHTS,
        "use_scheduler": USE_SCHEDULER,
        "scheduler_factor": SCHEDULER_FACTOR,
        "scheduler_patience": SCHEDULER_PATIENCE,
        "use_balanced_sampler": USE_BALANCED_SAMPLER,
        "single_batch_check_steps": SINGLE_BATCH_CHECK_STEPS,
        "use_early_stopping": USE_EARLY_STOPPING,
        "early_stopping_patience": EARLY_STOPPING_PATIENCE,
        "early_stopping_min_delta": EARLY_STOPPING_MIN_DELTA,
        "classes": classes,
        "stats": {
            "class_counts": stats["class_counts"],
            "variant_counts": stats["variant_counts"],
            "priority_file_keywords": stats["priority_file_keywords"],
            "priority_file_counts": stats["priority_file_counts"],
            "train_class_counts": stats["train_class_counts"],
            "val_class_counts": stats["val_class_counts"],
            "target_variants": stats["target_variants"],
            "max_files_per_class": stats["max_files_per_class"],
            "max_files_exempt_classes": stats["max_files_exempt_classes"],
            "file_sample_seed": stats["file_sample_seed"],
            "total_size": stats["total_size"],
            "original_total_size": stats["original_total_size"],
            "train_size": stats["train_size"],
            "original_train_size": stats["original_train_size"],
            "val_size": stats["val_size"],
            "stratified_split": stats["stratified_split"],
            "balanced_sampling": stats["balanced_sampling"],
            "use_augmentation": stats["use_augmentation"],
            "normalize_mode": stats["normalize_mode"],
            "target_samples_per_class": stats["target_samples_per_class"],
            "augmentation_counts": stats["augmentation_counts"],
            "augmentation": stats["augmentation"],
            "dataloader": stats["dataloader"],
            "skipped_file_count": len(stats["skipped_files"]),
        },
    }
    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(config, f, ensure_ascii=False, indent=4)
    print(f"Train config saved: {config_path}")


def class_recall(cm, class_idx):
    row_sum = int(cm[class_idx].sum())
    if row_sum == 0:
        return 0.0
    return float(cm[class_idx][class_idx] / row_sum)


def print_key_metrics(classes, cm):
    print("  key_metrics:")
    for class_name in KEY_CLASSES:
        if class_name not in classes:
            continue
        idx = classes.index(class_name)
        print(f"    recall[{class_name}]: {class_recall(cm, idx):.4f}")

    focus_pairs = [("가다", "걷다"), ("좋다", "싫다"), ("먹", "밥")]
    for left_class, right_class in focus_pairs:
        if left_class not in classes or right_class not in classes:
            continue
        left_idx = classes.index(left_class)
        right_idx = classes.index(right_class)
        print(f"    confusion {left_class}->{right_class}: {int(cm[left_idx][right_idx])}")
        print(f"    confusion {right_class}->{left_class}: {int(cm[right_idx][left_idx])}")


def is_better_model(val_f1, val_acc, best_f1, best_val_acc):
    if val_f1 > best_f1 + EARLY_STOPPING_MIN_DELTA:
        return True
    if abs(val_f1 - best_f1) <= EARLY_STOPPING_MIN_DELTA and val_acc > best_val_acc:
        return True
    return False


def inspect_first_batch(train_loader, classes):
    data, labels, lengths = next(iter(train_loader))
    zero_frame_mask = data.abs().sum(dim=2) == 0
    zero_frame_ratio = zero_frame_mask.float().mean().item() * 100.0

    print("\n[First Batch Inspection]")
    print(f"- batch shape: {tuple(data.shape)}")
    print(f"- labels: {[classes[int(x)] for x in labels.tolist()]}")
    print(f"- lengths: {lengths.tolist()}")
    print(
        f"- value stats: mean={data.mean().item():.6f}, std={data.std().item():.6f}, "
        f"min={data.min().item():.6f}, max={data.max().item():.6f}"
    )
    print(f"- zero frame ratio: {zero_frame_ratio:.2f}%")


def inspect_loader_signal(loader, classes, max_batches=10):
    label_counter = Counter()
    feature_sum = 0.0
    feature_sq_sum = 0.0
    feature_count = 0
    zero_frame_sum = 0.0
    batch_count = 0

    for data, labels, _ in loader:
        batch_count += 1
        label_counter.update(labels.tolist())
        feature_sum += float(data.sum().item())
        feature_sq_sum += float((data * data).sum().item())
        feature_count += int(data.numel())
        zero_frame_sum += float((data.abs().sum(dim=2) == 0).float().mean().item())

        if batch_count >= max_batches:
            break

    mean = feature_sum / max(feature_count, 1)
    variance = feature_sq_sum / max(feature_count, 1) - mean * mean
    std = max(variance, 0.0) ** 0.5

    print("\n[Train Loader Signal]")
    print(f"- sampled labels: {format_counter(label_counter, classes)}")
    print(f"- sampled mean/std: {mean:.6f}/{std:.6f}")
    print(f"- sampled zero frame ratio: {100.0 * zero_frame_sum / max(batch_count, 1):.2f}%")


def create_v6_dataloaders(device):
    kwargs = {
        "data_dir": DATA_DIR,
        "batch_size": BATCH_SIZE,
        "max_len": MAX_LEN,
        "input_dim": INPUT_DIM,
        "target_classes": TARGET_WORDS,
        "balanced_sampling": USE_BALANCED_SAMPLER,
        "target_variants": {},
    }

    signature = inspect.signature(create_dataloaders_v6)
    if "num_workers" in signature.parameters:
        kwargs.update(
            {
                "num_workers": NUM_WORKERS,
                "pin_memory": PIN_MEMORY and device.type == "cuda",
                "persistent_workers": PERSISTENT_WORKERS,
            }
        )
    else:
        print(
            "WARNING: dataset.py does not support dataloader worker options. "
            "Upload the updated dataset.py to enable V6_NUM_WORKERS/V6_PIN_MEMORY."
        )

    return create_dataloaders_v6(**kwargs)


def run_single_batch_overfit_check(model, criterion, optimizer, train_loader, device, steps=SINGLE_BATCH_CHECK_STEPS):
    data, labels, lengths = next(iter(train_loader))
    data = data.to(device)
    labels = labels.to(device)
    lengths = lengths.to(device)

    print("\n[Single Batch Overfit Check]")
    for step in range(steps):
        model.train()
        optimizer.zero_grad()
        outputs = model(data, lengths=lengths)
        loss = criterion(outputs, labels)
        loss.backward()
        optimizer.step()

        preds = outputs.argmax(dim=1)
        acc = (preds == labels).float().mean().item() * 100.0

        if (step + 1) % 20 == 0 or step == 0:
            print(f"- step {step + 1:>2}: loss={loss.item():.4f}, acc={acc:.2f}%")

    if acc < 80.0:
        print(
            "WARNING: single-batch overfit stayed below 80%. "
            "This usually means the input data is near-zero/invalid, labels are misaligned, "
            "or the model is not receiving useful signal."
        )


def train():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Start V7 117-class TCN training. device={device}")

    if not os.path.exists(DATA_DIR):
        print(f"Data directory not found: {DATA_DIR}")
        return

    model_dir, run_idx = make_unique_model_dir(MODELS_ROOT, MODEL_DIR_NAME)
    model_save_path, label_map_path, config_path = make_run_paths(model_dir)

    # ── 로그 파일 설정: 터미널 + 모델 폴더 내 train.log 동시 출력 ──
    log_path = os.path.join(model_dir, "train.log")
    _log_file = open(log_path, "w", encoding="utf-8", buffering=1)
    sys.stdout = TeeLogger(sys.stdout, _log_file)
    sys.stderr = TeeLogger(sys.stderr, _log_file)

    print(f"Model output directory: {model_dir}")
    print(f"Run directory suffix: _{run_idx}")
    print(f"Training log: {log_path}")

    train_loader, val_loader, classes, class_weights, stats = create_v6_dataloaders(device)

    num_classes = len(classes)

    with open(label_map_path, "w", encoding="utf-8") as f:
        json.dump({str(idx): label for idx, label in enumerate(classes)}, f, ensure_ascii=False, indent=4)
    print(f"Label map saved: {label_map_path} -> {classes}")

    print_dataset_debug_info(classes, stats, class_weights)
    save_train_config(config_path, classes, stats, run_idx)
    inspect_first_batch(train_loader, classes)
    inspect_loader_signal(train_loader, classes)

    model = build_model(num_classes).to(device)
    print(
        f"Model: type={MODEL_TYPE}, input_dim={INPUT_DIM}, max_len={MAX_LEN}, classes={num_classes}, "
        f"d_model={D_MODEL}, heads={NUM_HEADS}, layers={NUM_LAYERS}"
    )

    if USE_CLASS_WEIGHTS:
        criterion = nn.CrossEntropyLoss(weight=class_weights.to(device))
        print("Loss: CrossEntropyLoss with class weights")
    else:
        criterion = nn.CrossEntropyLoss()
        print("Loss: CrossEntropyLoss without class weights")

    optimizer = optim.AdamW(model.parameters(), lr=LEARNING_RATE, weight_decay=WEIGHT_DECAY)
    scheduler = (
        ReduceLROnPlateau(optimizer, mode="max", factor=SCHEDULER_FACTOR, patience=SCHEDULER_PATIENCE)
        if USE_SCHEDULER
        else None
    )
    print(f"Scheduler: {'ReduceLROnPlateau' if scheduler else 'disabled'}")
    print(
        f"Early stopping: {'enabled' if USE_EARLY_STOPPING else 'disabled'} "
        f"(patience={EARLY_STOPPING_PATIENCE}, min_delta={EARLY_STOPPING_MIN_DELTA})"
    )


    best_f1 = 0.0
    best_train_acc = 0.0
    best_val_acc_at_best_f1 = 0.0
    last_best_update = None
    epochs_without_improvement = 0

    for epoch in range(EPOCHS):
        model.train()
        train_loss_sum = 0.0
        train_correct = 0
        train_total = 0

        for data, labels, lengths in train_loader:
            data = data.to(device)
            labels = labels.to(device)
            lengths = lengths.to(device)

            optimizer.zero_grad()
            outputs = model(data, lengths=lengths)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()

            train_loss_sum += loss.item() * data.size(0)
            preds = outputs.argmax(dim=1)
            train_total += labels.size(0)
            train_correct += (preds == labels).sum().item()

        train_loss = train_loss_sum / max(train_total, 1)
        train_acc = 100.0 * train_correct / max(train_total, 1)

        model.eval()
        val_loss_sum = 0.0
        val_correct = 0
        val_total = 0
        all_preds = []
        all_labels = []

        with torch.no_grad():
            for data, labels, lengths in val_loader:
                data = data.to(device)
                labels = labels.to(device)
                lengths = lengths.to(device)

                outputs = model(data, lengths=lengths)
                loss = criterion(outputs, labels)

                val_loss_sum += loss.item() * data.size(0)
                preds = outputs.argmax(dim=1)
                val_total += labels.size(0)
                val_correct += (preds == labels).sum().item()

                all_preds.extend(preds.cpu().tolist())
                all_labels.extend(labels.cpu().tolist())

        val_loss = val_loss_sum / max(val_total, 1)
        val_acc = 100.0 * val_correct / max(val_total, 1)
        val_f1 = f1_score(all_labels, all_preds, average="macro", zero_division=0)

        pred_hist = format_counter(Counter(all_preds), classes)
        label_hist = format_counter(Counter(all_labels), classes)
        cm = confusion_matrix(all_labels, all_preds, labels=list(range(num_classes)))

        if scheduler is not None:
            scheduler.step(val_f1)

        print(
            f"Epoch [{epoch + 1}/{EPOCHS}] "
            f"Train Loss: {train_loss:.4f} | Train Acc: {train_acc:.2f}% | "
            f"Val Loss: {val_loss:.4f} | Val Acc: {val_acc:.2f}% | "
            f"F1: {val_f1:.4f} | Gap: {train_acc - val_acc:.2f}% | "
            f"LR: {optimizer.param_groups[0]['lr']}"
        )
        print(f"  labels: {label_hist}")
        print(f"  preds : {pred_hist}")
        print("  confusion_matrix:")
        for row_idx, row in enumerate(cm.tolist()):
            print(f"    {classes[row_idx]} -> {row}")
        print_key_metrics(classes, cm)

        should_save = is_better_model(val_f1, val_acc, best_f1, best_val_acc_at_best_f1)
        if should_save:
            best_f1 = val_f1
            best_val_acc_at_best_f1 = val_acc
            epochs_without_improvement = 0
        else:
            epochs_without_improvement += 1

        best_train_acc = max(best_train_acc, train_acc)

        if should_save:
            torch.save(model.state_dict(), model_save_path)
            last_best_update = {
                "epoch": epoch + 1,
                "train_loss": train_loss,
                "train_acc": train_acc,
                "val_loss": val_loss,
                "val_acc": val_acc,
                "val_f1": val_f1,
                "lr": optimizer.param_groups[0]["lr"],
                "label_hist": label_hist,
                "pred_hist": pred_hist,
                "confusion_matrix": cm.tolist(),
                "best_f1": best_f1,
                "best_train_acc": best_train_acc,
                "best_val_acc_at_best_f1": best_val_acc_at_best_f1,
            }
            print(
                f"  best model updated: {model_save_path} "
                f"(Best F1: {best_f1:.4f}, Best Train Acc: {best_train_acc:.2f}%, "
                f"Val Acc@BestF1: {best_val_acc_at_best_f1:.2f}%)"
            )
        elif USE_EARLY_STOPPING:
            print(
                f"  no val_f1 improvement: {epochs_without_improvement}/"
                f"{EARLY_STOPPING_PATIENCE}"
            )

        if USE_EARLY_STOPPING and epochs_without_improvement >= EARLY_STOPPING_PATIENCE:
            print(
                f"Early stopping at epoch {epoch + 1}. "
                f"Best F1: {best_f1:.4f}, Val Acc@BestF1: {best_val_acc_at_best_f1:.2f}%"
            )
            break

    if last_best_update is not None:
        print("\n[Last Best Model Update]")
        print(
            f"Epoch [{last_best_update['epoch']}/{EPOCHS}] "
            f"Train Loss: {last_best_update['train_loss']:.4f} | "
            f"Train Acc: {last_best_update['train_acc']:.2f}% | "
            f"Val Loss: {last_best_update['val_loss']:.4f} | "
            f"Val Acc: {last_best_update['val_acc']:.2f}% | "
            f"F1: {last_best_update['val_f1']:.4f} | "
            f"LR: {last_best_update['lr']}"
        )
        print(f"  labels: {last_best_update['label_hist']}")
        print(f"  preds : {last_best_update['pred_hist']}")
        print("  confusion_matrix:")
        for row_idx, row in enumerate(last_best_update["confusion_matrix"]):
            print(f"    {classes[row_idx]} -> {row}")
        print_key_metrics(classes, np.array(last_best_update["confusion_matrix"]))
        print(
            f"  saved model: {model_save_path} "
            f"(Best F1: {last_best_update['best_f1']:.4f}, "
            f"Best Train Acc: {last_best_update['best_train_acc']:.2f}%, "
            f"Val Acc@BestF1: {last_best_update['best_val_acc_at_best_f1']:.2f}%)"
        )

    print(
        f"\nV7 training complete! Best F1: {best_f1:.4f} | "
        f"Val Acc@BestF1: {best_val_acc_at_best_f1:.2f}% | "
        f"Best Train Acc: {best_train_acc:.2f}%"
    )
    print(f"Best model path: {model_save_path}")
    print(f"Label map path: {label_map_path}")
    print(f"Train config path: {config_path}")


if __name__ == "__main__":
    train()
