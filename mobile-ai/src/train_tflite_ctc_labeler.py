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
# Some server TensorFlow builds rewrite LayerNormalization to oneDNN/MKL
# kernels that are CPU-only, which breaks GPU validation. Keep the graph on
# standard TensorFlow ops unless the caller explicitly opts back in.
os.environ.setdefault("TF_ENABLE_ONEDNN_OPTS", "0")

import tensorflow as tf


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train a TFLite-friendly fixed-length Conv1D/TCN CTC labeler.",
    )
    parser.add_argument("--dataset-manifest", type=Path, required=True)
    parser.add_argument("--vocab", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="Output .tflite path.")
    parser.add_argument("--label-map", type=Path, required=True)
    parser.add_argument("--keras-output", type=Path, default=None)
    parser.add_argument("--sequence-length", type=int, default=192)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--units", type=int, default=192)
    parser.add_argument("--layers", type=int, default=6)
    parser.add_argument("--kernel-size", type=int, default=5)
    parser.add_argument("--dilations", default="1,2,4,8")
    parser.add_argument("--dropout", type=float, default=0.15)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--noise-std", type=float, default=0.0)
    parser.add_argument("--time-jitter", type=int, default=0)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--top-errors", type=int, default=20)
    return parser.parse_args()


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


def parse_dilations(raw: str) -> list[int]:
    values = [int(item.strip()) for item in raw.split(",") if item.strip()]
    if not values:
        raise ValueError("--dilations must contain at least one integer.")
    if any(value < 1 for value in values):
        raise ValueError("--dilations values must be >= 1.")
    return values


def read_rows(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


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


def load_split(
    rows: list[dict],
    label_to_id: dict[str, int],
    sequence_length: int,
    max_target_length: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, list[dict]]:
    features_list: list[np.ndarray] = []
    labels_list: list[np.ndarray] = []
    input_lengths: list[int] = []
    label_lengths: list[int] = []
    used_rows: list[dict] = []
    expected_dim: int | None = None

    for row in rows:
        tokens = (row.get("target_labels") or "").split()
        if not tokens:
            continue
        missing = [token for token in tokens if token not in label_to_id]
        if missing:
            raise ValueError(f"Unknown labels in {row.get('video_name')}: {missing}")

        features = np.load(row["feature_path"]).astype(np.float32)
        if expected_dim is None:
            expected_dim = int(features.shape[1])
        elif int(features.shape[1]) != expected_dim:
            raise ValueError(
                f"Feature dim mismatch in {row.get('video_name')}: "
                f"{features.shape[1]} != {expected_dim}"
            )

        encoded = np.asarray([label_to_id[token] for token in tokens], dtype=np.int32)
        if encoded.shape[0] > sequence_length:
            raise ValueError(f"Target longer than input sequence length in {row.get('video_name')}")

        padded_labels = np.zeros((max_target_length,), dtype=np.int32)
        padded_labels[: encoded.shape[0]] = encoded

        features_list.append(resample_features(features, sequence_length))
        labels_list.append(padded_labels)
        input_lengths.append(sequence_length)
        label_lengths.append(int(encoded.shape[0]))
        used_rows.append(row)

    if not features_list:
        raise ValueError("No rows available after filtering target_labels.")

    return (
        np.stack(features_list, axis=0).astype(np.float32),
        np.stack(labels_list, axis=0).astype(np.int32),
        np.asarray(input_lengths, dtype=np.int32),
        np.asarray(label_lengths, dtype=np.int32),
        used_rows,
    )


def augment_batch(
    features: tf.Tensor,
    labels: tf.Tensor,
    input_lengths: tf.Tensor,
    label_lengths: tf.Tensor,
    noise_std: float,
    time_jitter: int,
) -> tuple[tuple[tf.Tensor, tf.Tensor, tf.Tensor], tf.Tensor]:
    if noise_std > 0:
        features = features + tf.random.normal(tf.shape(features), stddev=noise_std, dtype=features.dtype)

    if time_jitter > 0:
        shift = tf.random.uniform([], minval=-time_jitter, maxval=time_jitter + 1, dtype=tf.int32)
        features = tf.roll(features, shift=shift, axis=1)

    return (features, labels, input_lengths), label_lengths


def make_dataset(
    features: np.ndarray,
    labels: np.ndarray,
    input_lengths: np.ndarray,
    label_lengths: np.ndarray,
    batch_size: int,
    shuffle: bool,
    seed: int,
    noise_std: float,
    time_jitter: int,
) -> tf.data.Dataset:
    dataset = tf.data.Dataset.from_tensor_slices((features, labels, input_lengths, label_lengths))
    if shuffle:
        dataset = dataset.shuffle(min(len(features), 10000), seed=seed, reshuffle_each_iteration=True)
    dataset = dataset.batch(batch_size)
    if noise_std > 0 or time_jitter > 0:
        dataset = dataset.map(
            lambda x, y, il, yl: augment_batch(x, y, il, yl, noise_std, time_jitter),
            num_parallel_calls=tf.data.AUTOTUNE,
        )
    else:
        dataset = dataset.map(
            lambda x, y, il, yl: ((x, y, il), yl),
            num_parallel_calls=tf.data.AUTOTUNE,
        )
    return dataset.prefetch(tf.data.AUTOTUNE)


def build_encoder_model(
    sequence_length: int,
    input_dim: int,
    class_count: int,
    units: int,
    layers: int,
    kernel_size: int,
    dilations: list[int],
    dropout: float,
) -> tf.keras.Model:
    inputs = tf.keras.Input(shape=(sequence_length, input_dim), name="features")
    x = tf.keras.layers.LayerNormalization(name="input_norm")(inputs)
    if input_dim != units:
        x = tf.keras.layers.Conv1D(units, 1, padding="same", name="input_projection")(x)

    for index in range(layers):
        dilation = dilations[index % len(dilations)]
        residual = x
        x = tf.keras.layers.Conv1D(
            units,
            kernel_size,
            padding="same",
            dilation_rate=dilation,
            name=f"tcn_{index + 1}_conv",
        )(x)
        x = tf.keras.layers.Activation("relu", name=f"tcn_{index + 1}_relu")(x)
        x = tf.keras.layers.Dropout(dropout, name=f"tcn_{index + 1}_dropout")(x)
        x = tf.keras.layers.Add(name=f"tcn_{index + 1}_add")([x, residual])
        x = tf.keras.layers.LayerNormalization(name=f"tcn_{index + 1}_norm")(x)

    logits = tf.keras.layers.Dense(class_count, name="logits")(x)
    return tf.keras.Model(inputs=inputs, outputs=logits, name="fixed_tcn_ctc_labeler")


class CtcTrainingModel(tf.keras.Model):
    def __init__(self, encoder: tf.keras.Model, blank_id: int = 0) -> None:
        super().__init__(name="ctc_training_model")
        self.encoder = encoder
        self.blank_id = blank_id
        self.loss_tracker = tf.keras.metrics.Mean(name="loss")

    @property
    def metrics(self) -> list[tf.keras.metrics.Metric]:
        return [self.loss_tracker]

    def call(self, features: tf.Tensor, training: bool = False) -> tf.Tensor:
        return self.encoder(features, training=training)

    def train_step(self, data: tuple[tuple[tf.Tensor, tf.Tensor, tf.Tensor], tf.Tensor]) -> dict[str, tf.Tensor]:
        (features, labels, input_lengths), label_lengths = data
        with tf.GradientTape() as tape:
            logits = self(features, training=True)
            loss = tf.nn.ctc_loss(
                labels=labels,
                logits=logits,
                label_length=label_lengths,
                logit_length=input_lengths,
                logits_time_major=False,
                blank_index=self.blank_id,
            )
            loss = tf.reduce_mean(loss)

        gradients = tape.gradient(loss, self.trainable_variables)
        self.optimizer.apply_gradients(zip(gradients, self.trainable_variables))
        self.loss_tracker.update_state(loss)
        return {"loss": self.loss_tracker.result()}

    def test_step(self, data: tuple[tuple[tf.Tensor, tf.Tensor, tf.Tensor], tf.Tensor]) -> dict[str, tf.Tensor]:
        (features, labels, input_lengths), label_lengths = data
        logits = self(features, training=False)
        loss = tf.nn.ctc_loss(
            labels=labels,
            logits=logits,
            label_length=label_lengths,
            logit_length=input_lengths,
            logits_time_major=False,
            blank_index=self.blank_id,
        )
        self.loss_tracker.update_state(tf.reduce_mean(loss))
        return {"loss": self.loss_tracker.result()}


def greedy_decode_ids(ids: np.ndarray, id_to_label: dict[int, str], blank_id: int = 0) -> list[str]:
    words: list[str] = []
    previous = blank_id
    for value in ids.tolist():
        label_id = int(value)
        if label_id != blank_id and label_id != previous:
            words.append(id_to_label[label_id])
        previous = label_id
    return words


def edit_distance(reference: list[str], hypothesis: list[str]) -> int:
    previous = list(range(len(hypothesis) + 1))
    for i, ref_token in enumerate(reference, start=1):
        current = [i]
        for j, hyp_token in enumerate(hypothesis, start=1):
            cost = 0 if ref_token == hyp_token else 1
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost))
        previous = current
    return previous[-1]


def print_sequence_report(
    encoder: tf.keras.Model,
    features: np.ndarray,
    rows: list[dict],
    id_to_label: dict[int, str],
    top_errors: int,
) -> None:
    logits = encoder.predict(features, verbose=0)
    predicted_ids = np.argmax(logits, axis=-1)
    total_distance = 0
    total_reference_tokens = 0
    correct_sequences = 0
    label_support: Counter[str] = Counter()
    label_predictions: Counter[str] = Counter()
    errors: Counter[tuple[str, str]] = Counter()

    for index, row in enumerate(rows):
        reference_words = (row.get("target_labels") or "").split()
        predicted_words = greedy_decode_ids(predicted_ids[index], id_to_label)
        distance = edit_distance(reference_words, predicted_words)
        total_distance += distance
        total_reference_tokens += len(reference_words)
        correct_sequences += int(distance == 0)
        label_support.update(reference_words)
        label_predictions.update(predicted_words)
        if distance:
            errors[(" ".join(reference_words), " ".join(predicted_words) or "<empty>")] += 1

    print(
        f"summary files={len(rows)} "
        f"sequence_acc={correct_sequences / max(len(rows), 1):.4f} "
        f"wer={total_distance / max(total_reference_tokens, 1):.4f}"
    )
    print("label_support")
    for label in sorted(label_support):
        print(f"{label}\tref={label_support[label]}\tpred={label_predictions[label]}")
    if errors:
        print("top_sequence_errors")
        print("ref\tpred\tcount")
        for (ref, pred), count in errors.most_common(top_errors):
            print(f"{ref}\t{pred}\t{count}")


def export_tflite(model: tf.keras.Model, output_path: Path) -> None:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    tflite_model = converter.convert()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)


def main() -> None:
    args = parse_args()
    configure_tensorflow_gpu()
    random.seed(args.seed)
    np.random.seed(args.seed)
    tf.keras.utils.set_random_seed(args.seed)

    vocab = json.loads(args.vocab.read_text(encoding="utf-8"))
    label_to_id = {key: int(value) for key, value in vocab["label_to_id"].items()}
    id_to_label = {int(key): value for key, value in vocab["id_to_label"].items()}
    if label_to_id.get("<blank>") != 0:
        raise ValueError("This trainer expects '<blank>' id to be 0.")

    rows = read_rows(args.dataset_manifest)
    train_rows = [row for row in rows if row.get("split") == "train"]
    val_rows = [row for row in rows if row.get("split") == "val"]
    if not train_rows:
        raise ValueError("No training rows found.")
    if not val_rows:
        raise ValueError("No validation rows found.")

    max_target_length = max(
        len((row.get("target_labels") or "").split())
        for row in rows
        if (row.get("target_labels") or "").split()
    )
    x_train, y_train, input_train, len_train, used_train = load_split(
        train_rows,
        label_to_id,
        args.sequence_length,
        max_target_length,
    )
    x_val, y_val, input_val, len_val, used_val = load_split(
        val_rows,
        label_to_id,
        args.sequence_length,
        max_target_length,
    )
    input_dim = int(x_train.shape[-1])
    class_count = len(label_to_id)
    dilations = parse_dilations(args.dilations)

    encoder = build_encoder_model(
        sequence_length=args.sequence_length,
        input_dim=input_dim,
        class_count=class_count,
        units=args.units,
        layers=args.layers,
        kernel_size=args.kernel_size,
        dilations=dilations,
        dropout=args.dropout,
    )
    trainer = CtcTrainingModel(encoder, blank_id=0)
    trainer.compile(optimizer=tf.keras.optimizers.Adam(learning_rate=args.lr))

    train_dataset = make_dataset(
        x_train,
        y_train,
        input_train,
        len_train,
        args.batch_size,
        shuffle=True,
        seed=args.seed,
        noise_std=args.noise_std,
        time_jitter=args.time_jitter,
    )
    val_dataset = make_dataset(
        x_val,
        y_val,
        input_val,
        len_val,
        args.batch_size,
        shuffle=False,
        seed=args.seed,
        noise_std=0.0,
        time_jitter=0,
    )

    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=15,
            mode="min",
            restore_best_weights=True,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=6,
            min_lr=1e-5,
        ),
    ]
    trainer.fit(
        train_dataset,
        validation_data=val_dataset,
        epochs=args.epochs,
        callbacks=callbacks,
        verbose=2,
    )

    print_sequence_report(encoder, x_val, used_val, id_to_label, args.top_errors)

    if args.keras_output:
        args.keras_output.parent.mkdir(parents=True, exist_ok=True)
        encoder.save(args.keras_output)
        print(f"keras={args.keras_output}")

    export_tflite(encoder, args.output)
    labels = [id_to_label[index] for index in sorted(id_to_label)]
    args.label_map.parent.mkdir(parents=True, exist_ok=True)
    args.label_map.write_text(
        json.dumps(
            {
                "labels": labels,
                "blank_id": 0,
                "input_shape": [1, args.sequence_length, input_dim],
                "output_shape": [1, args.sequence_length, class_count],
                "output_activation": "logits",
                "architecture": "fixed_tcn_ctc",
                "sequence_length": args.sequence_length,
                "units": args.units,
                "layers": args.layers,
                "kernel_size": args.kernel_size,
                "dilations": dilations,
                "train_files": len(used_train),
                "val_files": len(used_val),
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
    print(f"output_shape={[1, args.sequence_length, class_count]}")


if __name__ == "__main__":
    main()
