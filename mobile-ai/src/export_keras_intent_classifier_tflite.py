from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

os.environ.setdefault("TF_USE_LEGACY_KERAS", "1")

import tensorflow as tf


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export a trained Keras intent classifier to TFLite.")
    parser.add_argument("--keras-model", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-label-map", type=Path, required=True)
    parser.add_argument("--label-map", type=Path, required=True)
    return parser.parse_args()


def convert_from_concrete_function(model: tf.keras.Model, input_shape: list[int]) -> bytes:
    serving = tf.function(model).get_concrete_function(
        tf.TensorSpec(input_shape, tf.float32, name="input")
    )
    converter = tf.lite.TFLiteConverter.from_concrete_functions([serving], model)
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    return converter.convert()


def main() -> None:
    args = parse_args()
    model = tf.keras.models.load_model(args.keras_model)
    source_label_map = json.loads(args.source_label_map.read_text(encoding="utf-8"))
    input_shape = source_label_map.get("input_shape", [1, 30, 332])

    tflite_model = convert_from_concrete_function(model, input_shape)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(tflite_model)

    args.label_map.parent.mkdir(parents=True, exist_ok=True)
    args.label_map.write_text(
        json.dumps(
            {
                "labels": source_label_map["labels"],
                "input_shape": input_shape,
                "output_shape": source_label_map.get("output_shape", [1, len(source_label_map["labels"])]),
                "output_activation": "logits",
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"tflite={args.output}")
    print(f"label_map={args.label_map}")
    print(f"input_shape={input_shape}")
    print(f"output_shape={[1, len(source_label_map['labels'])]}")


if __name__ == "__main__":
    main()
