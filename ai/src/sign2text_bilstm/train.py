import os
import random

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
from sklearn.metrics import f1_score

from model import SignBiLSTM

# ── 설정 ─────────────────────────────────────────────────
SEED        = 42
BATCH_SIZE  = 16
EPOCHS      = 200
LR          = 1e-3
PATIENCE    = 20        # EarlyStopping patience
MODEL_DIR   = "models"
DATA_DIR    = "data/processed"


def get_next_model_path() -> str:
    """models/ 폴더에서 sign_lstm_N.pt 중 가장 높은 N을 찾아 N+1 경로를 반환합니다."""
    os.makedirs(MODEL_DIR, exist_ok=True)
    existing = [
        f for f in os.listdir(MODEL_DIR)
        if f.startswith("sign_lstm_") and f.endswith(".pt")
    ]
    nums = []
    for name in existing:
        stem = name[len("sign_lstm_"):-len(".pt")]
        if stem.isdigit():
            nums.append(int(stem))
    next_num = max(nums) + 1 if nums else 1
    return os.path.join(MODEL_DIR, f"sign_lstm_{next_num}.pt")


def set_seed(seed: int):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


class EarlyStopping:
    def __init__(self, patience: int = 20, delta: float = 1e-4):
        self.patience  = patience
        self.delta     = delta
        self.counter   = 0
        self.best_loss = float("inf")
        self.triggered = False

    def step(self, val_loss: float) -> bool:
        if val_loss < self.best_loss - self.delta:
            self.best_loss = val_loss
            self.counter   = 0
        else:
            self.counter += 1
            if self.counter >= self.patience:
                self.triggered = True
        return self.triggered


def main():
    set_seed(SEED)

    # ── 데이터 로드 ───────────────────────────────────────
    X_train = np.load(os.path.join(DATA_DIR, "X_train.npy"))
    y_train = np.load(os.path.join(DATA_DIR, "y_train.npy"))
    X_val   = np.load(os.path.join(DATA_DIR, "X_val.npy"))
    y_val   = np.load(os.path.join(DATA_DIR, "y_val.npy"))

    has_val = len(X_val) > 0

    MODEL_PATH = get_next_model_path()   # 자동 증가 번호 결정
    print(f"\n저장 경로: {MODEL_PATH}")

    print(f"X_train={X_train.shape}  y_train={y_train.shape}")
    print(f"X_val  ={X_val.shape}    y_val  ={y_val.shape}")

    # ── DataLoader ────────────────────────────────────────
    train_ds = TensorDataset(
        torch.tensor(X_train, dtype=torch.float32),
        torch.tensor(y_train, dtype=torch.long)
    )
    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True)

    if has_val:
        val_ds = TensorDataset(
            torch.tensor(X_val, dtype=torch.float32),
            torch.tensor(y_val, dtype=torch.long)
        )
        val_loader = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False)

    # ── 모델 & 옵티마이저 ─────────────────────────────────
    device     = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    num_classes = int(y_train.max()) + 1

    model = SignBiLSTM(
        input_size=X_train.shape[2],
        hidden_size=128,
        num_layers=2,
        num_classes=num_classes,
        dropout=0.3,
    ).to(device)

    # 클래스 가중치 (클래스 불균형 보정)
    class_counts  = np.bincount(y_train, minlength=num_classes).astype(np.float32)
    class_weights = 1.0 / np.clip(class_counts, 1, None)
    class_weights = class_weights / class_weights.sum() * num_classes
    criterion = nn.CrossEntropyLoss(
        weight=torch.tensor(class_weights, dtype=torch.float32).to(device)
    )

    optimizer = torch.optim.Adam(model.parameters(), lr=LR)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, mode="min", factor=0.5, patience=10
    )
    early_stop = EarlyStopping(patience=PATIENCE)

    print(f"\ndevice={device}  num_classes={num_classes}  train={len(train_ds)}", end="")
    print(f"  val={len(val_ds)}" if has_val else "  val=없음")
    print("-" * 60)

    # ── 학습 루프 ─────────────────────────────────────────
    best_val_loss = float("inf")
    os.makedirs(MODEL_DIR, exist_ok=True)

    for epoch in range(1, EPOCHS + 1):
        # Train
        model.train()
        total_loss, correct, total = 0.0, 0, 0
        for xb, yb in train_loader:
            xb, yb = xb.to(device), yb.to(device)
            optimizer.zero_grad()
            logits = model(xb)
            loss   = criterion(logits, yb)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            total_loss += loss.item() * len(yb)
            correct    += (logits.argmax(1) == yb).sum().item()
            total      += len(yb)

        train_loss = total_loss / total
        train_acc  = correct / total

        # Validation
        if has_val:
            model.eval()
            val_loss_sum, val_correct, val_total = 0.0, 0, 0
            all_preds, all_labels = [], []
            with torch.no_grad():
                for xb, yb in val_loader:
                    xb, yb = xb.to(device), yb.to(device)
                    logits = model(xb)
                    val_loss_sum += criterion(logits, yb).item() * len(yb)
                    preds = logits.argmax(1)
                    val_correct  += (preds == yb).sum().item()
                    val_total    += len(yb)
                    all_preds.extend(preds.cpu().numpy())
                    all_labels.extend(yb.cpu().numpy())
            val_loss = val_loss_sum / val_total
            val_acc  = val_correct / val_total
            val_f1   = f1_score(all_labels, all_preds, average='macro', zero_division=0)
            scheduler.step(val_loss)

            print(f"Epoch {epoch:03d} | "
                  f"train_loss={train_loss:.4f}  train_acc={train_acc:.4f} | "
                  f"val_loss={val_loss:.4f}  val_acc={val_acc:.4f}  val_f1={val_f1:.4f}")

            # 최고 모델 저장
            if val_loss < best_val_loss:
                best_val_loss = val_loss
                torch.save(model.state_dict(), MODEL_PATH)
                print(f"           ✓ best model saved (val_loss={val_loss:.4f})")

            if early_stop.step(val_loss):
                print(f"\nEarlyStopping triggered at epoch {epoch}")
                break
        else:
            # Val 없으면 train loss 기준으로 저장
            scheduler.step(train_loss)
            print(f"Epoch {epoch:03d} | train_loss={train_loss:.4f}  train_acc={train_acc:.4f}")
            if train_loss < best_val_loss:
                best_val_loss = train_loss
                torch.save(model.state_dict(), MODEL_PATH)

    print(f"\n학습 완료. 최고 모델 저장 위치: {MODEL_PATH}")


if __name__ == "__main__":
    main()
