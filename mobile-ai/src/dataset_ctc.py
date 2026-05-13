from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np


BLANK_TOKEN = "<blank>"


@dataclass(frozen=True)
class ContinuousSample:
    npy_path: Path
    labels: list[str]
    frames: int


def load_samples(split_dir: Path) -> list[ContinuousSample]:
    samples: list[ContinuousSample] = []
    for json_path in sorted(split_dir.glob("*.json")):
        npy_path = json_path.with_suffix(".npy")
        if not npy_path.exists():
            continue
        metadata = json.loads(json_path.read_text(encoding="utf-8"))
        samples.append(
            ContinuousSample(
                npy_path=npy_path,
                labels=list(metadata["labels"]),
                frames=int(metadata["frames"]),
            )
        )
    return samples


def build_vocab(samples: list[ContinuousSample]) -> dict[str, int]:
    labels = sorted({label for sample in samples for label in sample.labels})
    return {BLANK_TOKEN: 0, **{label: idx + 1 for idx, label in enumerate(labels)}}


def load_features(sample: ContinuousSample) -> np.ndarray:
    return np.load(sample.npy_path).astype(np.float32)
