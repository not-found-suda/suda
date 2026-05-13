from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import NamedTuple

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train a mobile-friendly fixed-length sequence classifier.")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sequence-length", type=int, default=30)
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--hidden-size", type=int, default=128)
    parser.add_argument("--layers", type=int, default=2)
    parser.add_argument("--dropout", type=float, default=0.2)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def build_label_vocab(rows: list[dict]) -> tuple[dict[str, int], dict[int, str]]:
    labels = sorted({row["label"] for row in rows if row.get("label")})
    label_to_id = {label: index for index, label in enumerate(labels)}
    id_to_label = {index: label for label, index in label_to_id.items()}
    return label_to_id, id_to_label


def resample_features(features: np.ndarray, target_len: int) -> np.ndarray:
    features = np.nan_to_num(features.astype(np.float32, copy=False))
    if features.shape[0] == target_len:
        return features
    if features.shape[0] <= 1:
        return np.repeat(features, target_len, axis=0)

    old_positions = np.arange(features.shape[0], dtype=np.float32)
    new_positions = np.linspace(0, features.shape[0] - 1, target_len, dtype=np.float32)
    out = np.empty((target_len, features.shape[1]), dtype=np.float32)
    for dim in range(features.shape[1]):
        out[:, dim] = np.interp(new_positions, old_positions, features[:, dim])
    return out


class Batch(NamedTuple):
    features: torch.Tensor
    labels: torch.Tensor


class FixedSequenceClassificationDataset(Dataset):
    def __init__(self, rows: list[dict], label_to_id: dict[str, int], sequence_length: int) -> None:
        self.rows = [row for row in rows if row.get("label") in label_to_id]
        self.label_to_id = label_to_id
        self.sequence_length = sequence_length

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, int]:
        row = self.rows[index]
        features = np.load(row["feature_path"]).astype(np.float32)
        features = resample_features(features, self.sequence_length)
        return torch.from_numpy(features), self.label_to_id[row["label"]]


def collate_batch(batch: list[tuple[torch.Tensor, int]]) -> Batch:
    features = torch.stack([item[0] for item in batch], dim=0)
    labels = torch.tensor([item[1] for item in batch], dtype=torch.long)
    return Batch(features, labels)


class FixedSequenceClassifier(nn.Module):
    def __init__(
        self,
        input_dim: int,
        class_count: int,
        hidden_size: int = 128,
        layers: int = 2,
        dropout: float = 0.2,
    ) -> None:
        super().__init__()
        self.encoder = nn.GRU(
            input_size=input_dim,
            hidden_size=hidden_size,
            num_layers=layers,
            batch_first=True,
            bidirectional=True,
            dropout=dropout if layers > 1 else 0.0,
        )
        self.attention = nn.Linear(hidden_size * 2, 1)
        self.dropout = nn.Dropout(dropout)
        self.classifier = nn.Linear(hidden_size * 2, class_count)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        encoded, _ = self.encoder(x)
        scores = self.attention(encoded).squeeze(-1)
        weights = torch.softmax(scores, dim=1).unsqueeze(-1)
        pooled = torch.sum(encoded * weights, dim=1)
        return self.classifier(self.dropout(pooled))


def evaluate(
    model: FixedSequenceClassifier,
    loader: DataLoader,
    loss_fn: nn.Module,
    device: str,
) -> tuple[float, float]:
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0
    batches = 0
    with torch.no_grad():
        for batch in loader:
            features = batch.features.to(device)
            labels = batch.labels.to(device)
            logits = model(features)
            loss = loss_fn(logits, labels)
            total_loss += float(loss.item())
            correct += int((logits.argmax(dim=-1) == labels).sum().item())
            total += int(labels.numel())
            batches += 1
    return total_loss / max(batches, 1), correct / max(total, 1)


def main() -> None:
    args = parse_args()
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)

    rows = read_rows(args.manifest)
    train_rows = [row for row in rows if row.get("split") == "train"]
    val_rows = [row for row in rows if row.get("split") == "val"]
    label_to_id, id_to_label = build_label_vocab(train_rows)

    train_dataset = FixedSequenceClassificationDataset(train_rows, label_to_id, args.sequence_length)
    val_dataset = FixedSequenceClassificationDataset(val_rows, label_to_id, args.sequence_length)
    if not train_dataset:
        raise ValueError("No training rows found.")
    if not val_dataset:
        raise ValueError("No validation rows found.")

    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True, collate_fn=collate_batch)
    val_loader = DataLoader(val_dataset, batch_size=args.batch_size, shuffle=False, collate_fn=collate_batch)

    first_features = np.load(train_dataset.rows[0]["feature_path"], mmap_mode="r")
    model = FixedSequenceClassifier(
        input_dim=int(first_features.shape[1]),
        class_count=len(label_to_id),
        hidden_size=args.hidden_size,
        layers=args.layers,
        dropout=args.dropout,
    ).to(args.device)

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)
    loss_fn = nn.CrossEntropyLoss()
    best_val_acc = -1.0
    best_state = None

    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        correct = 0
        total = 0
        batches = 0
        for batch in train_loader:
            features = batch.features.to(args.device)
            labels = batch.labels.to(args.device)

            optimizer.zero_grad(set_to_none=True)
            logits = model(features)
            loss = loss_fn(logits, labels)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            optimizer.step()

            total_loss += float(loss.item())
            correct += int((logits.argmax(dim=-1) == labels).sum().item())
            total += int(labels.numel())
            batches += 1

        train_loss = total_loss / max(batches, 1)
        train_acc = correct / max(total, 1)
        val_loss, val_acc = evaluate(model, val_loader, loss_fn, args.device)
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            best_state = {key: value.detach().cpu() for key, value in model.state_dict().items()}

        print(
            f"epoch={epoch} train_loss={train_loss:.4f} train_acc={train_acc:.4f} "
            f"val_loss={val_loss:.4f} val_acc={val_acc:.4f}"
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "model_state": best_state or model.state_dict(),
            "final_model_state": {key: value.detach().cpu() for key, value in model.state_dict().items()},
            "input_dim": int(first_features.shape[1]),
            "class_count": len(label_to_id),
            "hidden_size": args.hidden_size,
            "layers": args.layers,
            "dropout": args.dropout,
            "sequence_length": args.sequence_length,
            "label_to_id": label_to_id,
            "id_to_label": {str(index): label for index, label in id_to_label.items()},
            "best_val_acc": best_val_acc,
        },
        args.output,
    )
    print(f"saved={args.output}")


if __name__ == "__main__":
    main()
