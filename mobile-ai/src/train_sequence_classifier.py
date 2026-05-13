from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import NamedTuple

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train a sequence-level WORD/intent classifier.")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--hidden-size", type=int, default=128)
    parser.add_argument("--layers", type=int, default=2)
    parser.add_argument("--dropout", type=float, default=0.2)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="auto", choices=["auto", "cuda", "cpu"])
    parser.add_argument("--num-workers", type=int, default=2)
    return parser.parse_args()


def resolve_device(requested: str) -> torch.device:
    if requested == "auto":
        requested = "cuda" if torch.cuda.is_available() else "cpu"
    if requested == "cuda" and not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA was requested, but torch.cuda.is_available() is false. "
            "Check that this conda env has a CUDA-enabled PyTorch build."
        )
    return torch.device(requested)


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def build_label_vocab(rows: list[dict]) -> tuple[dict[str, int], dict[int, str]]:
    labels = sorted({row["label"] for row in rows if row.get("label")})
    label_to_id = {label: index for index, label in enumerate(labels)}
    id_to_label = {index: label for label, index in label_to_id.items()}
    return label_to_id, id_to_label


class Batch(NamedTuple):
    features: torch.Tensor
    lengths: torch.Tensor
    labels: torch.Tensor


class SequenceClassificationDataset(Dataset):
    def __init__(self, rows: list[dict], label_to_id: dict[str, int]) -> None:
        self.rows = [row for row in rows if row.get("label") in label_to_id]
        self.label_to_id = label_to_id

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, int, int]:
        row = self.rows[index]
        features = np.load(row["feature_path"]).astype(np.float32)
        label_id = self.label_to_id[row["label"]]
        return torch.from_numpy(features), int(features.shape[0]), label_id


def collate_batch(batch: list[tuple[torch.Tensor, int, int]]) -> Batch:
    lengths = torch.tensor([item[1] for item in batch], dtype=torch.long)
    labels = torch.tensor([item[2] for item in batch], dtype=torch.long)
    max_len = int(lengths.max().item())
    feature_dim = int(batch[0][0].shape[1])
    features = torch.zeros((len(batch), max_len, feature_dim), dtype=torch.float32)
    for index, (x, length, _) in enumerate(batch):
        features[index, :length] = x
    return Batch(features, lengths, labels)


class SequenceClassifier(nn.Module):
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

    def forward(self, x: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        packed = nn.utils.rnn.pack_padded_sequence(
            x,
            lengths.cpu(),
            batch_first=True,
            enforce_sorted=False,
        )
        encoded, _ = self.encoder(packed)
        padded, _ = nn.utils.rnn.pad_packed_sequence(encoded, batch_first=True)

        max_len = padded.shape[1]
        mask = torch.arange(max_len, device=lengths.device).unsqueeze(0) < lengths.unsqueeze(1)
        scores = self.attention(padded).squeeze(-1)
        scores = scores.masked_fill(~mask, -1.0e9)
        weights = torch.softmax(scores, dim=1).unsqueeze(-1)
        pooled = torch.sum(padded * weights, dim=1)
        return self.classifier(self.dropout(pooled))


def evaluate(
    model: SequenceClassifier,
    loader: DataLoader,
    loss_fn: nn.Module,
    device: torch.device,
) -> tuple[float, float]:
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0
    batches = 0
    with torch.no_grad():
        for batch in loader:
            non_blocking = device.type == "cuda"
            features = batch.features.to(device, non_blocking=non_blocking)
            lengths = batch.lengths.to(device, non_blocking=non_blocking)
            labels = batch.labels.to(device, non_blocking=non_blocking)
            logits = model(features, lengths)
            loss = loss_fn(logits, labels)
            total_loss += float(loss.item())
            correct += int((logits.argmax(dim=-1) == labels).sum().item())
            total += int(labels.numel())
            batches += 1
    return total_loss / max(batches, 1), correct / max(total, 1)


def main() -> None:
    args = parse_args()
    device = resolve_device(args.device)
    if device.type == "cuda":
        torch.backends.cudnn.benchmark = True
        print(f"device=cuda name={torch.cuda.get_device_name(0)} cuda={torch.version.cuda}")
    else:
        print("device=cpu")

    torch.manual_seed(args.seed)
    if device.type == "cuda":
        torch.cuda.manual_seed_all(args.seed)
    np.random.seed(args.seed)

    rows = read_rows(args.manifest)
    train_rows = [row for row in rows if row.get("split") == "train"]
    val_rows = [row for row in rows if row.get("split") == "val"]
    label_to_id, id_to_label = build_label_vocab(train_rows)

    train_dataset = SequenceClassificationDataset(train_rows, label_to_id)
    val_dataset = SequenceClassificationDataset(val_rows, label_to_id)
    if not train_dataset:
        raise ValueError("No training rows found.")
    if not val_dataset:
        raise ValueError("No validation rows found.")

    pin_memory = device.type == "cuda"
    train_loader = DataLoader(
        train_dataset,
        batch_size=args.batch_size,
        shuffle=True,
        collate_fn=collate_batch,
        num_workers=args.num_workers,
        pin_memory=pin_memory,
        persistent_workers=args.num_workers > 0,
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=args.batch_size,
        shuffle=False,
        collate_fn=collate_batch,
        num_workers=args.num_workers,
        pin_memory=pin_memory,
        persistent_workers=args.num_workers > 0,
    )

    first_features = np.load(train_dataset.rows[0]["feature_path"], mmap_mode="r")
    model = SequenceClassifier(
        input_dim=int(first_features.shape[1]),
        class_count=len(label_to_id),
        hidden_size=args.hidden_size,
        layers=args.layers,
        dropout=args.dropout,
    ).to(device)

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
            non_blocking = device.type == "cuda"
            features = batch.features.to(device, non_blocking=non_blocking)
            lengths = batch.lengths.to(device, non_blocking=non_blocking)
            labels = batch.labels.to(device, non_blocking=non_blocking)

            optimizer.zero_grad(set_to_none=True)
            logits = model(features, lengths)
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
        val_loss, val_acc = evaluate(model, val_loader, loss_fn, device)
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
            "label_to_id": label_to_id,
            "id_to_label": {str(index): label for index, label in id_to_label.items()},
            "best_val_acc": best_val_acc,
        },
        args.output,
    )
    print(f"saved={args.output}")


if __name__ == "__main__":
    main()
