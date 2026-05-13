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


PAD_LABEL_ID = -100


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train a small frame-level sequence labeling baseline.")
    parser.add_argument("--dataset-manifest", type=Path, required=True)
    parser.add_argument("--vocab", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "artifacts" / "sequence_labeler.pt",
    )
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--hidden-size", type=int, default=128)
    parser.add_argument("--layers", type=int, default=2)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--augment-speed",
        action="store_true",
        help="Randomly resample training sequences between 0.9x and 1.1x speed.",
    )
    parser.add_argument(
        "--augment-noise-std",
        type=float,
        default=0.0,
        help="Stddev for weak Gaussian noise on nonzero training features.",
    )
    parser.add_argument(
        "--augment-frame-drop",
        type=float,
        default=0.0,
        help="Probability of dropping each training frame. Keep low, e.g. 0.02.",
    )
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


class AugmentConfig(NamedTuple):
    speed: bool = False
    noise_std: float = 0.0
    frame_drop: float = 0.0


def resample_sequence(
    x: np.ndarray,
    y: np.ndarray,
    speed: float,
) -> tuple[np.ndarray, np.ndarray]:
    if x.shape[0] <= 1:
        return x, y

    old_len = x.shape[0]
    new_len = max(1, int(round(old_len / speed)))
    positions = np.linspace(0, old_len - 1, new_len, dtype=np.float32)
    base = np.arange(old_len, dtype=np.float32)

    out_x = np.empty((new_len, x.shape[1]), dtype=np.float32)
    for dim in range(x.shape[1]):
        out_x[:, dim] = np.interp(positions, base, x[:, dim]).astype(np.float32)

    nearest = np.clip(np.rint(positions).astype(np.int64), 0, old_len - 1)
    return out_x, y[nearest]


def augment_sequence(
    x: np.ndarray,
    y: np.ndarray,
    config: AugmentConfig,
    rng: np.random.Generator,
) -> tuple[np.ndarray, np.ndarray]:
    if config.speed:
        x, y = resample_sequence(x, y, float(rng.uniform(0.9, 1.1)))

    if 0.0 < config.frame_drop < 1.0 and x.shape[0] > 1:
        keep = rng.random(x.shape[0]) >= config.frame_drop
        if keep.any():
            x = x[keep]
            y = y[keep]

    if config.noise_std > 0.0:
        noise = rng.normal(0.0, config.noise_std, size=x.shape).astype(np.float32)
        x = x + noise * (x != 0.0)

    return x.astype(np.float32, copy=False), y


class SequenceLabelDataset(Dataset):
    def __init__(
        self,
        rows: list[dict],
        augment: AugmentConfig | None = None,
        seed: int = 42,
    ) -> None:
        self.rows = rows
        self.augment = augment or AugmentConfig()
        self.rng = np.random.default_rng(seed)

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor, int]:
        row = self.rows[index]
        x = np.load(row["feature_path"]).astype(np.float32)
        y = np.load(row["label_path"]).astype(np.int64)
        if x.shape[0] != y.shape[0]:
            raise ValueError(f"Length mismatch: {row['video_name']} x={x.shape} y={y.shape}")
        x, y = augment_sequence(x, y, self.augment, self.rng)
        return torch.from_numpy(x), torch.from_numpy(y), int(x.shape[0])


def collate_batch(batch: list[tuple[torch.Tensor, torch.Tensor, int]]) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    lengths = torch.tensor([item[2] for item in batch], dtype=torch.long)
    max_len = int(lengths.max().item())
    feature_dim = int(batch[0][0].shape[1])
    x = torch.zeros((len(batch), max_len, feature_dim), dtype=torch.float32)
    y = torch.full((len(batch), max_len), PAD_LABEL_ID, dtype=torch.long)
    for index, (features, labels, length) in enumerate(batch):
        x[index, :length] = features
        y[index, :length] = labels
    return x, y, lengths


class SequenceLabeler(nn.Module):
    def __init__(self, input_dim: int, class_count: int, hidden_size: int, layers: int) -> None:
        super().__init__()
        self.encoder = nn.GRU(
            input_size=input_dim,
            hidden_size=hidden_size,
            num_layers=layers,
            batch_first=True,
            bidirectional=True,
            dropout=0.1 if layers > 1 else 0.0,
        )
        self.classifier = nn.Sequential(
            nn.LayerNorm(hidden_size * 2),
            nn.Linear(hidden_size * 2, class_count),
        )

    def forward(self, x: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        packed = nn.utils.rnn.pack_padded_sequence(
            x,
            lengths.cpu(),
            batch_first=True,
            enforce_sorted=False,
        )
        encoded, _ = self.encoder(packed)
        padded, _ = nn.utils.rnn.pad_packed_sequence(encoded, batch_first=True, total_length=x.shape[1])
        return self.classifier(padded)


def split_rows(rows: list[dict]) -> tuple[list[dict], list[dict]]:
    train = [row for row in rows if row.get("split") != "val"]
    val = [row for row in rows if row.get("split") == "val"]
    if not val and len(train) > 5:
        val_count = max(1, int(len(train) * 0.15))
        val = train[-val_count:]
        train = train[:-val_count]
    return train, val


def run_epoch(
    model: nn.Module,
    loader: DataLoader,
    criterion: nn.Module,
    device: torch.device,
    optimizer: torch.optim.Optimizer | None = None,
) -> tuple[float, float]:
    is_train = optimizer is not None
    model.train(is_train)
    total_loss = 0.0
    total_tokens = 0
    correct = 0

    for x, y, lengths in loader:
        x = x.to(device)
        y = y.to(device)
        lengths = lengths.to(device)
        if optimizer:
            optimizer.zero_grad(set_to_none=True)

        with torch.set_grad_enabled(is_train):
            logits = model(x, lengths)
            loss = criterion(logits.reshape(-1, logits.shape[-1]), y.reshape(-1))
            if optimizer:
                loss.backward()
                nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                optimizer.step()

        mask = y != PAD_LABEL_ID
        predictions = logits.argmax(dim=-1)
        correct += int((predictions[mask] == y[mask]).sum().item())
        token_count = int(mask.sum().item())
        total_tokens += token_count
        total_loss += float(loss.item()) * token_count

    avg_loss = total_loss / max(total_tokens, 1)
    accuracy = correct / max(total_tokens, 1)
    return avg_loss, accuracy


def main() -> None:
    args = parse_args()
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)
    vocab = json.loads(args.vocab.read_text(encoding="utf-8"))
    class_count = len(vocab["label_to_id"])
    rows = read_rows(args.dataset_manifest)
    train_rows, val_rows = split_rows(rows)
    if not train_rows:
        raise ValueError("No training rows found. Did you run prepare_sequence_labels.py after extracting videos?")

    train_augment = AugmentConfig(
        speed=args.augment_speed,
        noise_std=args.augment_noise_std,
        frame_drop=args.augment_frame_drop,
    )
    train_loader = DataLoader(
        SequenceLabelDataset(train_rows, augment=train_augment, seed=args.seed),
        batch_size=args.batch_size,
        shuffle=True,
        collate_fn=collate_batch,
    )
    val_loader = DataLoader(
        SequenceLabelDataset(val_rows),
        batch_size=args.batch_size,
        shuffle=False,
        collate_fn=collate_batch,
    ) if val_rows else None

    device = torch.device(args.device)
    model = SequenceLabeler(input_dim=332, class_count=class_count, hidden_size=args.hidden_size, layers=args.layers).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    criterion = nn.CrossEntropyLoss(ignore_index=PAD_LABEL_ID)

    best_val = float("inf")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    for epoch in range(1, args.epochs + 1):
        train_loss, train_acc = run_epoch(model, train_loader, criterion, device, optimizer)
        message = f"epoch={epoch} train_loss={train_loss:.4f} train_acc={train_acc:.4f}"
        metric_for_save = train_loss
        if val_loader:
            val_loss, val_acc = run_epoch(model, val_loader, criterion, device)
            message += f" val_loss={val_loss:.4f} val_acc={val_acc:.4f}"
            metric_for_save = val_loss
        print(message)

        if metric_for_save < best_val:
            best_val = metric_for_save
            torch.save(
                {
                    "model_state": model.state_dict(),
                    "vocab": vocab,
                    "input_dim": 332,
                    "class_count": class_count,
                    "hidden_size": args.hidden_size,
                    "layers": args.layers,
                    "augment": train_augment._asdict(),
                },
                args.output,
            )
    print(f"saved={args.output}")


if __name__ == "__main__":
    main()
