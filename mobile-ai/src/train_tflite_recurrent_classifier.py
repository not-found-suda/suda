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
    print(f"tensorflow_device=gpu gpus={len(gpus)} names={', '.join(gpu.name for gpu in gpus)}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train a fixed-length GRU/LSTM sequence classifier and export TFLite.",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="Output .tflite path.")
    parser.add_argument("--label-map", type=Path, required=True)
    parser.add_argument("--keras-output", type=Path, default=None)
    parser.add_argument("--load-keras-weights", type=Path, default=None)
    parser.add_argument("--skip-train", action="store_true")
    parser.add_argument("--sequence-length", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--arch", choices=["recurrent", "conv"], default="recurrent")
    parser.add_argument("--rnn", choices=["gru", "lstm"], default="gru")
    parser.add_argument("--units", type=int, default=128)
    parser.add_argument("--layers", type=int, default=1)
    parser.add_argument("--bidirectional", action="store_true")
    parser.add_argument("--dense-size", type=int, default=128)
    parser.add_argument("--pooling", choices=["avgmax", "attention"], default="avgmax")
    parser.add_argument("--conv-kernel-size", type=int, default=5)
    parser.add_argument("--conv-dilations", default="1,2,4")
    parser.add_argument("--dropout", type=float, default=0.25)
    parser.add_argument("--recurrent-dropout", type=float, default=0.0)
    parser.add_argument(
        "--no-unroll",
        action="store_true",
        help="Keep the recurrent layer symbolic. The default unrolls fixed 30-frame RNNs for TFLite conversion.",
    )
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--noise-std", type=float, default=0.0)
    parser.add_argument("--time-jitter", type=int, default=0)
    parser.add_argument("--hand-dropout", type=float, default=0.0)
    return parser.parse_args()


def parse_dilations(raw: str) -> list[int]:
    dilations = [int(item.strip()) for item in raw.split(",") if item.strip()]
    if not dilations:
        raise ValueError("--conv-dilations must contain at least one integer.")
    if any(dilation < 1 for dilation in dilations):
        raise ValueError("--conv-dilations must contain integers >= 1.")
    return dilations


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


def augment_batch(
    features: tf.Tensor,
    labels: tf.Tensor,
    noise_std: float,
    time_jitter: int,
    hand_dropout: float,
) -> tuple[tf.Tensor, tf.Tensor]:
    if noise_std > 0:
        features = features + tf.random.normal(tf.shape(features), stddev=noise_std, dtype=features.dtype)

    if time_jitter > 0:
        shift = tf.random.uniform([], minval=-time_jitter, maxval=time_jitter + 1, dtype=tf.int32)
        features = tf.roll(features, shift=shift, axis=1)

    if hand_dropout > 0:
        batch = tf.shape(features)[0]
        seq_len = tf.shape(features)[1]
        mask = tf.cast(tf.random.uniform([batch, seq_len, 1]) >= hand_dropout, features.dtype)
        hand_mask = tf.concat(
            [
                tf.tile(mask, [1, 1, 126]),
                tf.ones([batch, seq_len, tf.shape(features)[2] - 126], dtype=features.dtype),
            ],
            axis=-1,
        )
        features = features * hand_mask

    return features, labels


def build_model(
    sequence_length: int,
    input_dim: int,
    class_count: int,
    rnn: str,
    units: int,
    layers: int,
    bidirectional: bool,
    dense_size: int,
    pooling: str,
    dropout: float,
    recurrent_dropout: float,
    unroll: bool,
    arch: str,
    conv_kernel_size: int,
    conv_dilations: list[int],
) -> tf.keras.Model:
    inputs = tf.keras.Input(shape=(sequence_length, input_dim), name="input")
    x = tf.keras.layers.LayerNormalization(name="norm")(inputs)
    if arch == "conv":
        for layer_index in range(layers):
            dilation = conv_dilations[layer_index % len(conv_dilations)]
            residual = x
            x = tf.keras.layers.Conv1D(
                filters=units,
                kernel_size=conv_kernel_size,
                padding="same",
                dilation_rate=dilation,
                name=f"conv_{layer_index + 1}",
            )(x)
            x = tf.keras.layers.Activation("gelu", name=f"conv_{layer_index + 1}_gelu")(x)
            x = tf.keras.layers.Dropout(dropout, name=f"conv_{layer_index + 1}_dropout")(x)
            if int(residual.shape[-1]) != units:
                residual = tf.keras.layers.Conv1D(
                    filters=units,
                    kernel_size=1,
                    padding="same",
                    name=f"conv_{layer_index + 1}_residual",
                )(residual)
            x = tf.keras.layers.Add(name=f"conv_{layer_index + 1}_add")([x, residual])
            x = tf.keras.layers.LayerNormalization(name=f"conv_{layer_index + 1}_norm")(x)
    else:
        for layer_index in range(layers):
            layer_name = f"{rnn}_{layer_index + 1}"
            if rnn == "gru":
                rnn_layer = tf.keras.layers.GRU(
                    units,
                    dropout=dropout,
                    recurrent_dropout=recurrent_dropout,
                    return_sequences=True,
                    unroll=unroll,
                    name=layer_name,
                )
            else:
                rnn_layer = tf.keras.layers.LSTM(
                    units,
                    dropout=dropout,
                    recurrent_dropout=recurrent_dropout,
                    return_sequences=True,
                    unroll=unroll,
                    name=layer_name,
                )
            if bidirectional:
                rnn_layer = tf.keras.layers.Bidirectional(rnn_layer, name=f"bidir_{layer_name}")
            x = rnn_layer(x)

    if pooling == "attention":
        attention_scores = tf.keras.layers.Dense(1, name="attention_score")(x)
        attention_weights = tf.keras.layers.Softmax(axis=1, name="attention_weights")(attention_scores)
        weighted = tf.keras.layers.Multiply(name="attention_apply")([x, attention_weights])
        x = tf.keras.layers.Lambda(lambda values: tf.reduce_sum(values, axis=1), name="attention_pool")(weighted)
    else:
        avg_pool = tf.keras.layers.GlobalAveragePooling1D(name="avg_pool")(x)
        max_pool = tf.keras.layers.GlobalMaxPooling1D(name="max_pool")(x)
        x = tf.keras.layers.Concatenate(name="pool_concat")([avg_pool, max_pool])

    if dense_size > 0:
        x = tf.keras.layers.Dense(dense_size, activation="relu", name="dense")(x)
    x = tf.keras.layers.Dropout(dropout, name="dropout")(x)
    logits = tf.keras.layers.Dense(class_count, name="logits")(x)
    direction_name = "bidir" if bidirectional else "single"
    model_name = f"fixed{sequence_length}_{arch}_{direction_name}_{rnn}_classifier"
    return tf.keras.Model(inputs=inputs, outputs=logits, name=model_name)


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
    converter.experimental_enable_resource_variables = True
    tflite_model = converter.convert()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)


def main() -> None:
    args = parse_args()
    configure_tensorflow_gpu()
    random.seed(args.seed)
    np.random.seed(args.seed)
    tf.keras.utils.set_random_seed(args.seed)
    conv_dilations = parse_dilations(args.conv_dilations)

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
        rnn=args.rnn,
        units=args.units,
        layers=args.layers,
        bidirectional=args.bidirectional,
        dense_size=args.dense_size,
        pooling=args.pooling,
        dropout=args.dropout,
        recurrent_dropout=args.recurrent_dropout,
        unroll=not args.no_unroll,
        arch=args.arch,
        conv_kernel_size=args.conv_kernel_size,
        conv_dilations=conv_dilations,
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=args.lr),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
        metrics=["accuracy"],
    )

    if args.load_keras_weights:
        loaded_model = tf.keras.models.load_model(args.load_keras_weights, compile=False)
        model.set_weights(loaded_model.get_weights())
        print(f"loaded_keras_weights={args.load_keras_weights}")

    train_dataset = tf.data.Dataset.from_tensor_slices((x_train, y_train)).shuffle(
        min(len(x_train), 10000),
        seed=args.seed,
        reshuffle_each_iteration=True,
    )
    train_dataset = train_dataset.batch(args.batch_size)
    if args.noise_std > 0 or args.time_jitter > 0 or args.hand_dropout > 0:
        train_dataset = train_dataset.map(
            lambda features, labels: augment_batch(
                features,
                labels,
                args.noise_std,
                args.time_jitter,
                args.hand_dropout,
            ),
            num_parallel_calls=tf.data.AUTOTUNE,
        )
    train_dataset = train_dataset.prefetch(tf.data.AUTOTUNE)

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

    if not args.skip_train:
        model.fit(
            train_dataset,
            validation_data=(x_val, y_val),
            epochs=args.epochs,
            callbacks=callbacks,
            verbose=2,
        )
    else:
        print("skip_train=true")

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
                "architecture": args.arch,
                "rnn": args.rnn if args.arch == "recurrent" else None,
                "layers": args.layers,
                "bidirectional": args.bidirectional,
                "pooling": args.pooling,
                "units": args.units,
                "conv_kernel_size": args.conv_kernel_size if args.arch == "conv" else None,
                "conv_dilations": conv_dilations if args.arch == "conv" else None,
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
