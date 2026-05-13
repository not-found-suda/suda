from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch
from torch import nn

from train_ctc_labeler import CtcLabeler


class CtcOnnxWrapper(nn.Module):
    def __init__(self, model: CtcLabeler) -> None:
        super().__init__()
        self.model = model

    def forward(self, features: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        # Android runs one completed segment at a time, so export a batch=1 path
        # without PackedSequence. This keeps ONNX Runtime mobile compatibility much
        # better than exporting pack_padded_sequence.
        if self.model.encoder_type == "gru":
            encoded, _ = self.model.encoder(features)
            logits = self.model.classifier(encoded)
            return logits + lengths.to(logits.dtype).reshape(1, 1, 1) * 0.0
        return self.model(features, lengths)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export a trained CTC labeler checkpoint to ONNX for Android.")
    parser.add_argument("--model", type=Path, required=True, help="Input PyTorch checkpoint.")
    parser.add_argument("--vocab", type=Path, required=True, help="Training vocab.json.")
    parser.add_argument("--output", type=Path, required=True, help="Output ONNX path.")
    parser.add_argument("--checkpoint-key", default="model_state", choices=["model_state", "final_model_state"])
    parser.add_argument("--opset", type=int, default=17)
    parser.add_argument("--dummy-frames", type=int, default=80)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    checkpoint = torch.load(args.model, map_location="cpu")
    vocab = json.loads(args.vocab.read_text(encoding="utf-8"))
    class_count = len(vocab["label_to_id"])

    model = CtcLabeler(
        input_dim=int(checkpoint["input_dim"]),
        class_count=class_count,
        encoder=checkpoint.get("encoder", "gru"),
        hidden_size=int(checkpoint.get("hidden_size", 128)),
        layers=int(checkpoint.get("layers", 2)),
        heads=int(checkpoint.get("heads", 4)),
        ff_size=int(checkpoint.get("ff_size", 512)),
        dropout=float(checkpoint.get("dropout", 0.0)),
    )
    model.load_state_dict(checkpoint[args.checkpoint_key])
    model.eval()

    input_dim = int(checkpoint["input_dim"])
    features = torch.zeros((1, args.dummy_frames, input_dim), dtype=torch.float32)
    lengths = torch.tensor([args.dummy_frames], dtype=torch.long)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    export_model = CtcOnnxWrapper(model).eval()
    torch.onnx.export(
        export_model,
        (features, lengths),
        args.output,
        input_names=["features", "lengths"],
        output_names=["logits"],
        dynamic_axes={
            "features": {1: "time"},
            "lengths": {0: "batch"},
            "logits": {1: "time"},
        },
        opset_version=args.opset,
        dynamo=False,
    )
    print(f"onnx={args.output}")
    print(f"input_dim={input_dim}")
    print(f"class_count={class_count}")


if __name__ == "__main__":
    main()
