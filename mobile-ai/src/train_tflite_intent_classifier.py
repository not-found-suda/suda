from __future__ import annotations

import argparse
import csv
import json
import os
import random
from collections import Counter
from pathlib import Path

import numpy as np

os.environ.setdefault("TF_USE_LEGACY_KERAS", "1")

import tensorflow as tf


def configure_tensorflow_gpu() -> None:
    gpus = tf.config.list_physical_devices("GPU")
    if not gpus:
        print("tensorflow_device=cpu gpus=0")
        return

    for gpu in gpus:
        try:
            tf.config.experimental.set_memory_growth(gpu, True)
        except RuntimeError:
            pass
    gpu_names = ", ".join(gpu.name for gpu in gpus)
    print(f"tensorflow_device=gpu gpus={len(gpus)} names={gpu_names}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train a mobile-friendly fixed-length intent classifier and export TFLite."
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="Output .tflite path.")
    parser.add_argument("--label-map", type=Path, required=True)
    parser.add_argument("--keras-output", type=Path, default=None, help="Optional .keras checkpoint path.")
    parser.add_argument("--sequence-length", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--filters", type=int, default=128)
    parser.add_argument("--dense-size", type=int, default=128)
    parser.add_argument("--dropout", type=float, default=0.25)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--top-k", type=int, default=3)
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


def load_split(rows: list[dict], label_to_id: dict[str, int], sequence_length: int) -> tuple[np.ndarray, np.ndarray]:
    xs: list[np.ndarray] = []
    ys: list[int] = []
    for row in rows:
        label = row.get("label", "")
        if label not in label_to_id:
            continue
        features = np.load(row["feature_path"]).astype(np.float32)
        xs.append(resample_features(features, sequence_length))
        ys.append(label_to_id[label])

    if not xs:
        raise ValueError("No rows available after filtering labels.")
    return np.stack(xs, axis=0).astype(np.float32), np.asarray(ys, dtype=np.int64)


def build_model(
    sequence_length: int,
    input_dim: int,
    class_count: int,
    filters: int,
    dense_size: int,
    dropout: float,
) -> tf.keras.Model:
    inputs = tf.keras.Input(shape=(sequence_length, input_dim), name="input")
    x = tf.keras.layers.LayerNormalization(name="norm")(inputs)
    x = tf.keras.layers.Conv1D(filters, 5, padding="same", activation="relu", name="conv1")(x)
    x = tf.keras.layers.Dropout(dropout, name="dropout1")(x)
    x = tf.keras.layers.Conv1D(filters, 3, padding="same", activation="relu", name="conv2")(x)
    x = tf.keras.layers.Dropout(dropout, name="dropout2")(x)
    x = tf.keras.layers.Conv1D(filters, 3, padding="same", activation="relu", name="conv3")(x)

    avg_pool = tf.keras.layers.GlobalAveragePooling1D(name="avg_pool")(x)
    max_pool = tf.keras.layers.GlobalMaxPooling1D(name="max_pool")(x)
    x = tf.keras.layers.Concatenate(name="pool_concat")([avg_pool, max_pool])
    x = tf.keras.layers.Dense(dense_size, activation="relu", name="dense")(x)
    x = tf.keras.layers.Dropout(dropout, name="dropout3")(x)
    logits = tf.keras.layers.Dense(class_count, name="logits")(x)
    return tf.keras.Model(inputs=inputs, outputs=logits, name="intent_conv1d_classifier")


def print_eval_report(
    model: tf.keras.Model,
    x_val: np.ndarray,
    y_val: np.ndarray,
    id_to_label: dict[int, str],
    top_k: int,
) -> None:
    logits = model.predict(x_val, verbose=0)
    probs = tf.nn.softmax(logits, axis=-1).numpy()
    top_indices = np.argsort(probs, axis=-1)[:, ::-1][:, :top_k]

    top1_correct = 0
    topk_correct = 0
    support: Counter[str] = Counter()
    predicted: Counter[str] = Counter()
    correct: Counter[str] = Counter()
    errors: Counter[tuple[str, str]] = Counter()

    for index, ref_id in enumerate(y_val):
        hyp_id = int(top_indices[index, 0])
        ref = id_to_label[int(ref_id)]
        hyp = id_to_label[hyp_id]
        top1_correct += int(hyp_id == int(ref_id))
        topk_correct += int(int(ref_id) in top_indices[index])
        support[ref] += 1
        predicted[hyp] += 1
        if hyp == ref:
            correct[ref] += 1
        else:
            errors[(ref, hyp)] += 1

    total = max(len(y_val), 1)
    print(f"summary files={len(y_val)} top1_acc={top1_correct / total:.4f} top{top_k}_acc={topk_correct / total:.4f}")
    print("per_label")
    print("label\tsupport\tpredicted\tcorrect\tprecision\trecall")
    for label in sorted(support):
        precision = correct[label] / predicted[label] if predicted[label] else 0.0
        recall = correct[label] / support[label] if support[label] else 0.0
        print(f"{label}\t{support[label]}\t{predicted[label]}\t{correct[label]}\t{precision:.4f}\t{recall:.4f}")

    if errors:
        print("top_errors")
        print("ref\tpred\tcount")
        for (ref, hyp), count in errors.most_common(20):
            print(f"{ref}\t{hyp}\t{count}")


def export_tflite(model: tf.keras.Model, output_path: Path) -> None:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)


def main() -> None:
    args = parse_args()
    configure_tensorflow_gpu()
    random.seed(args.seed)
    np.random.seed(args.seed)
    tf.keras.utils.set_random_seed(args.seed)

    rows = read_rows(args.manifest)
    train_rows = [row for row in rows if row.get("split") == "train"]
    val_rows = [row for row in rows if row.get("split") == "val"]
    label_to_id, id_to_label = build_label_vocab(train_rows)
    if not train_rows:
        raise ValueError("No train rows found.")
    if not val_rows:
        raise ValueError("No val rows found.")

    x_train, y_train = load_split(train_rows, label_to_id, args.sequence_length)
    x_val, y_val = load_split(val_rows, label_to_id, args.sequence_length)
    input_dim = int(x_train.shape[-1])
    class_count = len(label_to_id)

    model = build_model(
        sequence_length=args.sequence_length,
        input_dim=input_dim,
        class_count=class_count,
        filters=args.filters,
        dense_size=args.dense_size,
        dropout=args.dropout,
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=args.lr),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
        metrics=["accuracy"],
    )

    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_accuracy",
            patience=15,
            mode="max",
            restore_best_weights=True,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=6,
            min_lr=1e-5,
        ),
    ]

    model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch_size,
        callbacks=callbacks,
        verbose=2,
    )

    print_eval_report(model, x_val, y_val, id_to_label, args.top_k)

    if args.keras_output:
        args.keras_output.parent.mkdir(parents=True, exist_ok=True)
        model.save(args.keras_output)
        print(f"keras={args.keras_output}")

    export_tflite(model, args.output)

    labels = [id_to_label[index] for index in sorted(id_to_label)]
    args.label_map.parent.mkdir(parents=True, exist_ok=True)
    args.label_map.write_text(
        json.dumps(
            {
                "labels": labels,
                "input_shape": [1, args.sequence_length, input_dim],
                "output_shape": [1, len(labels)],
                "output_activation": "logits",
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"tflite={args.output}")
    print(f"label_map={args.label_map}")
    print(f"labels={len(labels)}")
    print(f"input_shape={[1, args.sequence_length, input_dim]}")
    print(f"output_shape={[1, len(labels)]}")


if __name__ == "__main__":
    main()
