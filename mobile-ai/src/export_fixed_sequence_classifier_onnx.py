from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch

from train_fixed_sequence_classifier import FixedSequenceClassifier


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export a fixed-length sequence classifier to ONNX.")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--label-map", type=Path, required=True)
    parser.add_argument("--checkpoint-key", choices=["model_state", "final_model_state"], default="model_state")
    parser.add_argument("--opset", type=int, default=17)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    checkpoint = torch.load(args.model, map_location="cpu")
    id_to_label = {int(index): label for index, label in checkpoint["id_to_label"].items()}

    model = FixedSequenceClassifier(
        input_dim=int(checkpoint.get("input_dim", 332)),
        class_count=int(checkpoint.get("class_count", len(id_to_label))),
        hidden_size=int(checkpoint.get("hidden_size", 128)),
        layers=int(checkpoint.get("layers", 2)),
        dropout=float(checkpoint.get("dropout", 0.2)),
    )
    model.load_state_dict(checkpoint[args.checkpoint_key])
    model.eval()

    sequence_length = int(checkpoint.get("sequence_length", 30))
    input_dim = int(checkpoint.get("input_dim", 332))
    dummy_input = torch.zeros((1, sequence_length, input_dim), dtype=torch.float32)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        model,
        dummy_input,
        args.output,
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=args.opset,
    )

    labels = [id_to_label[index] for index in sorted(id_to_label)]
    args.label_map.parent.mkdir(parents=True, exist_ok=True)
    args.label_map.write_text(
        json.dumps(
            {
                "labels": labels,
                "input_shape": [1, sequence_length, input_dim],
                "output_shape": [1, len(labels)],
                "output_activation": "logits",
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"onnx={args.output}")
    print(f"label_map={args.label_map}")
    print(f"labels={len(labels)}")
    print(f"input_shape={[1, sequence_length, input_dim]}")
    print(f"output_shape={[1, len(labels)]}")


if __name__ == "__main__":
    main()
