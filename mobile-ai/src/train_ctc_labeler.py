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
    parser = argparse.ArgumentParser(description="Train a BiGRU CTC recognizer from feature sequences to word tokens.")
    parser.add_argument("--dataset-manifest", type=Path, required=True)
    parser.add_argument("--vocab", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "artifacts" / "ctc_labeler.pt",
    )
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--encoder", choices=["gru", "transformer"], default="gru")
    parser.add_argument("--hidden-size", type=int, default=128)
    parser.add_argument("--layers", type=int, default=2)
    parser.add_argument("--heads", type=int, default=4)
    parser.add_argument("--ff-size", type=int, default=512)
    parser.add_argument("--dropout", type=float, default=0.1)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    parser.add_argument(
        "--frame-ce-weight",
        type=float,
        default=0.0,
        help="Optional weight for frame-level cross entropy using label_path rows.",
    )
    parser.add_argument(
        "--init-checkpoint",
        type=Path,
        default=None,
        help="Optional checkpoint to initialize from before training.",
    )
    parser.add_argument(
        "--init-checkpoint-key",
        default="model_state",
        choices=["model_state", "final_model_state"],
        help="State dict key to load from --init-checkpoint.",
    )
    return parser.parse_args()


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


class Batch(NamedTuple):
    features: torch.Tensor
    input_lengths: torch.Tensor
    targets: torch.Tensor
    target_lengths: torch.Tensor
    frame_labels: torch.Tensor


class CtcDataset(Dataset):
    def __init__(self, rows: list[dict], label_to_id: dict[str, int]) -> None:
        self.rows = [row for row in rows if (row.get("target_labels") or "").split()]
        self.label_to_id = label_to_id

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor, int, int]:
        row = self.rows[index]
        x = np.load(row["feature_path"]).astype(np.float32)
        tokens = (row.get("target_labels") or "").split()
        y = np.asarray([self.label_to_id[token] for token in tokens], dtype=np.int64)
        label_path = row.get("label_path", "")
        if label_path and Path(label_path).exists():
            frame_labels = np.load(label_path).astype(np.int64)
            if int(frame_labels.shape[0]) != int(x.shape[0]):
                raise ValueError(f"label_path length mismatch: {row.get('video_name')}")
        else:
            frame_labels = np.full((x.shape[0],), -100, dtype=np.int64)
        return torch.from_numpy(x), torch.from_numpy(y), torch.from_numpy(frame_labels), int(x.shape[0]), int(y.shape[0])


def collate_batch(batch: list[tuple[torch.Tensor, torch.Tensor, torch.Tensor, int, int]]) -> Batch:
    input_lengths = torch.tensor([item[3] for item in batch], dtype=torch.long)
    target_lengths = torch.tensor([item[4] for item in batch], dtype=torch.long)
    max_len = int(input_lengths.max().item())
    feature_dim = int(batch[0][0].shape[1])

    features = torch.zeros((len(batch), max_len, feature_dim), dtype=torch.float32)
    frame_labels = torch.full((len(batch), max_len), -100, dtype=torch.long)
    targets = []
    for index, (x, y, labels, input_len, _) in enumerate(batch):
        features[index, :input_len] = x
        frame_labels[index, :input_len] = labels
        targets.append(y)

    return Batch(features, input_lengths, torch.cat(targets).long(), target_lengths, frame_labels)


class CtcLabeler(nn.Module):
    def __init__(
        self,
        input_dim: int,
        class_count: int,
        encoder: str = "gru",
        hidden_size: int = 128,
        layers: int = 2,
        heads: int = 4,
        ff_size: int = 512,
        dropout: float = 0.1,
        max_positions: int = 1024,
    ) -> None:
        super().__init__()
        self.encoder_type = encoder
        if encoder == "gru":
            self.encoder = nn.GRU(
                input_size=input_dim,
                hidden_size=hidden_size,
                num_layers=layers,
                batch_first=True,
                bidirectional=True,
                dropout=dropout if layers > 1 else 0.0,
            )
            output_dim = hidden_size * 2
        elif encoder == "transformer":
            if hidden_size % heads != 0:
                raise ValueError("--hidden-size must be divisible by --heads")
            self.input_projection = nn.Linear(input_dim, hidden_size)
            self.position_embedding = nn.Parameter(torch.zeros(1, max_positions, hidden_size))
            encoder_layer = nn.TransformerEncoderLayer(
                d_model=hidden_size,
                nhead=heads,
                dim_feedforward=ff_size,
                dropout=dropout,
                batch_first=True,
                activation="gelu",
                norm_first=True,
            )
            self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=layers)
            output_dim = hidden_size
        else:
            raise ValueError(f"Unsupported encoder: {encoder}")
        self.classifier = nn.Linear(output_dim, class_count)

    def forward(self, x: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        if self.encoder_type == "gru":
            packed = nn.utils.rnn.pack_padded_sequence(
                x,
                lengths.cpu(),
                batch_first=True,
                enforce_sorted=False,
            )
            encoded, _ = self.encoder(packed)
            padded, _ = nn.utils.rnn.pad_packed_sequence(encoded, batch_first=True)
            return self.classifier(padded)

        time_steps = x.shape[1]
        if time_steps > self.position_embedding.shape[1]:
            raise ValueError(f"Sequence too long: {time_steps} > {self.position_embedding.shape[1]}")
        positions = self.position_embedding[:, :time_steps]
        encoded_input = self.input_projection(x) + positions
        mask = torch.arange(time_steps, device=x.device)[None, :] >= lengths[:, None]
        encoded = self.encoder(encoded_input, src_key_padding_mask=mask)
        return self.classifier(encoded)


def evaluate(
    model: CtcLabeler,
    loader: DataLoader,
    loss_fn: nn.CTCLoss,
    frame_loss_fn: nn.CrossEntropyLoss,
    frame_ce_weight: float,
    device: str,
) -> tuple[float, float]:
    model.eval()
    total_loss = 0.0
    total_tokens = 0
    batches = 0
    with torch.no_grad():
        for batch in loader:
            features = batch.features.to(device)
            input_lengths = batch.input_lengths.to(device)
            targets = batch.targets.to(device)
            target_lengths = batch.target_lengths.to(device)
            frame_labels = batch.frame_labels.to(device)

            logits = model(features, input_lengths)
            log_probs = torch.log_softmax(logits, dim=-1).transpose(0, 1)
            loss = loss_fn(log_probs, targets, input_lengths, target_lengths)
            if frame_ce_weight > 0.0 and torch.any(frame_labels != -100):
                frame_loss = frame_loss_fn(logits.transpose(1, 2), frame_labels)
                loss = loss + frame_ce_weight * frame_loss

            total_loss += float(loss.item())
            total_tokens += int(target_lengths.sum().item())
            batches += 1

    return total_loss / max(batches, 1), total_tokens / max(len(loader.dataset), 1)


def main() -> None:
    args = parse_args()
    torch.manual_seed(args.seed)
    np.random.seed(args.seed)

    vocab = json.loads(args.vocab.read_text(encoding="utf-8"))
    label_to_id = {key: int(value) for key, value in vocab["label_to_id"].items()}
    rows = read_rows(args.dataset_manifest)
    train_rows = [row for row in rows if row.get("split") == "train"]
    val_rows = [row for row in rows if row.get("split") == "val"]

    train_dataset = CtcDataset(train_rows, label_to_id)
    val_dataset = CtcDataset(val_rows, label_to_id)
    if not train_dataset:
        raise ValueError("No training rows found.")
    if not val_dataset:
        raise ValueError("No validation rows found.")

    train_loader = DataLoader(
        train_dataset,
        batch_size=args.batch_size,
        shuffle=True,
        collate_fn=collate_batch,
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=args.batch_size,
        shuffle=False,
        collate_fn=collate_batch,
    )

    first_features = np.load(train_dataset.rows[0]["feature_path"], mmap_mode="r")
    model = CtcLabeler(
        input_dim=int(first_features.shape[1]),
        class_count=len(label_to_id),
        encoder=args.encoder,
        hidden_size=args.hidden_size,
        layers=args.layers,
        heads=args.heads,
        ff_size=args.ff_size,
        dropout=args.dropout,
    ).to(args.device)
    if args.init_checkpoint:
        checkpoint = torch.load(args.init_checkpoint, map_location=args.device)
        model.load_state_dict(checkpoint[args.init_checkpoint_key])
        print(f"initialized={args.init_checkpoint} key={args.init_checkpoint_key}")

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)
    loss_fn = nn.CTCLoss(blank=0, zero_infinity=True)
    frame_loss_fn = nn.CrossEntropyLoss(ignore_index=-100)

    best_val_loss = float("inf")
    best_state = None
    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        batches = 0
        for batch in train_loader:
            features = batch.features.to(args.device)
            input_lengths = batch.input_lengths.to(args.device)
            targets = batch.targets.to(args.device)
            target_lengths = batch.target_lengths.to(args.device)
            frame_labels = batch.frame_labels.to(args.device)

            optimizer.zero_grad(set_to_none=True)
            logits = model(features, input_lengths)
            log_probs = torch.log_softmax(logits, dim=-1).transpose(0, 1)
            loss = loss_fn(log_probs, targets, input_lengths, target_lengths)
            if args.frame_ce_weight > 0.0 and torch.any(frame_labels != -100):
                frame_loss = frame_loss_fn(logits.transpose(1, 2), frame_labels)
                loss = loss + args.frame_ce_weight * frame_loss
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            optimizer.step()

            total_loss += float(loss.item())
            batches += 1

        train_loss = total_loss / max(batches, 1)
        val_loss, val_tokens_per_file = evaluate(
            model,
            val_loader,
            loss_fn,
            frame_loss_fn,
            args.frame_ce_weight,
            args.device,
        )
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state = {key: value.detach().cpu() for key, value in model.state_dict().items()}

        print(
            f"epoch={epoch} train_loss={train_loss:.4f} "
            f"val_loss={val_loss:.4f} val_tokens_per_file={val_tokens_per_file:.2f}"
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "model_state": best_state or model.state_dict(),
            "final_model_state": {key: value.detach().cpu() for key, value in model.state_dict().items()},
            "input_dim": int(first_features.shape[1]),
            "class_count": len(label_to_id),
            "encoder": args.encoder,
            "hidden_size": args.hidden_size,
            "layers": args.layers,
            "heads": args.heads,
            "ff_size": args.ff_size,
            "dropout": args.dropout,
            "frame_ce_weight": args.frame_ce_weight,
            "best_val_loss": best_val_loss,
        },
        args.output,
    )
    print(f"saved={args.output}")


if __name__ == "__main__":
    main()
