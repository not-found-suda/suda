from __future__ import annotations

import numpy as np


def greedy_ctc_decode(probabilities: np.ndarray, index_to_label: dict[int, str], blank_index: int = 0) -> list[str]:
    if probabilities.ndim != 2:
        raise ValueError(f"Expected [T, C] probabilities, got {probabilities.shape}")

    best_indices = probabilities.argmax(axis=1).tolist()
    decoded: list[str] = []
    previous = blank_index
    for index in best_indices:
        if index != blank_index and index != previous:
            decoded.append(index_to_label[int(index)])
        previous = int(index)
    return decoded
