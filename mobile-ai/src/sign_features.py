from __future__ import annotations

from dataclasses import dataclass
from math import sqrt
from typing import Iterable

import numpy as np


INPUT_DIM = 332
HAND_LANDMARK_COUNT = 21
SELECTED_FACE_LANDMARK_COUNT = 45
SELECTED_POSE_LANDMARK_COUNT = 7
COORDINATE_SIZE = 3
LANDMARK_DIM = (
    (HAND_LANDMARK_COUNT * 2 + SELECTED_FACE_LANDMARK_COUNT + SELECTED_POSE_LANDMARK_COUNT)
    * COORDINATE_SIZE
)
HAND_FACE_DISTANCE_COUNT = 50

BASE_FACE_LANDMARK_INDICES = [
    70,
    63,
    105,
    66,
    336,
    296,
    334,
    293,
    33,
    160,
    158,
    133,
    153,
    144,
    362,
    385,
    387,
    263,
    373,
    380,
    61,
    146,
    91,
    181,
    84,
    17,
    314,
    405,
    321,
    375,
]
FACEPLUS_LANDMARK_INDICES = [
    1,
    4,
    152,
    234,
    454,
    172,
    397,
    148,
    377,
    13,
    14,
    78,
    308,
    82,
    312,
]
FACE_LANDMARK_INDICES = BASE_FACE_LANDMARK_INDICES + FACEPLUS_LANDMARK_INDICES
POSE_LANDMARK_INDICES = [0, 7, 8, 11, 12, 13, 14]
FACE_ANCHOR_INDICES = [1, 13, 152, 234, 454]
HAND_TIP_INDICES = [4, 8, 12, 16, 20]
LEFT_SHOULDER_LANDMARK_INDEX = 90
RIGHT_SHOULDER_LANDMARK_INDEX = 91
MIN_SHOULDER_WIDTH = 1e-6


@dataclass(frozen=True)
class Point:
    x: float
    y: float
    z: float

    def distance_to(self, other: "Point") -> float:
        dx = self.x - other.x
        dy = self.y - other.y
        dz = self.z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)


ZERO_POINT = Point(0.0, 0.0, 0.0)


class ContinuousFeatureExtractor:
    def __init__(self, hand_forward_fill: bool = False):
        self.hand_forward_fill = hand_forward_fill
        self.previous_left_hand: list[Point] | None = None
        self.previous_right_hand: list[Point] | None = None
        self.previous_face: list[Point] | None = None
        self.previous_pose: list[Point] | None = None

    def reset(self) -> None:
        self.previous_left_hand = None
        self.previous_right_hand = None
        self.previous_face = None
        self.previous_pose = None

    def encode(self, results) -> tuple[np.ndarray, bool]:
        left_hand_landmarks = _get_landmarks(results, "left_hand_landmarks")
        right_hand_landmarks = _get_landmarks(results, "right_hand_landmarks")
        face_landmarks = _get_landmarks(results, "face_landmarks")
        pose_landmarks = _get_landmarks(results, "pose_landmarks")

        left_hand = self._select_hand(left_hand_landmarks, "left")
        right_hand = self._select_hand(right_hand_landmarks, "right")
        face = self._select_indexed_landmarks(
            face_landmarks,
            FACE_LANDMARK_INDICES,
            previous_attr="previous_face",
        )
        pose = self._select_indexed_landmarks(
            pose_landmarks,
            POSE_LANDMARK_INDICES,
            previous_attr="previous_pose",
        )

        raw_landmarks = left_hand + right_hand + face + pose
        distances = self._hand_face_distances(
            face_landmarks=face_landmarks,
            left_hand=left_hand,
            right_hand=right_hand,
        )
        values = self._normalize(raw_landmarks, distances)
        has_hands = left_hand_landmarks is not None or right_hand_landmarks is not None
        return values, has_hands

    def _select_hand(self, hand_landmarks, side: str) -> list[Point]:
        previous_attr = f"previous_{side}_hand"
        if hand_landmarks is None:
            previous = getattr(self, previous_attr)
            if self.hand_forward_fill and previous is not None:
                return previous
            return [ZERO_POINT] * HAND_LANDMARK_COUNT

        points = _to_points(hand_landmarks)
        selected = [points[index] if index < len(points) else ZERO_POINT for index in range(HAND_LANDMARK_COUNT)]
        setattr(self, previous_attr, selected)
        return selected

    def _select_indexed_landmarks(
        self,
        landmarks,
        indices: list[int],
        previous_attr: str,
    ) -> list[Point]:
        if landmarks is not None:
            points = _to_points(landmarks)
            if all(index < len(points) for index in indices):
                selected = [points[index] for index in indices]
                setattr(self, previous_attr, selected)
                return selected

        previous = getattr(self, previous_attr)
        if previous is not None:
            return previous
        return [ZERO_POINT] * len(indices)

    def _hand_face_distances(
        self,
        face_landmarks,
        left_hand: list[Point],
        right_hand: list[Point],
    ) -> np.ndarray:
        if face_landmarks is None:
            return np.zeros(HAND_FACE_DISTANCE_COUNT, dtype=np.float32)

        face_points = _to_points(face_landmarks)
        if not all(index < len(face_points) for index in FACE_ANCHOR_INDICES):
            return np.zeros(HAND_FACE_DISTANCE_COUNT, dtype=np.float32)

        anchors = [face_points[index] for index in FACE_ANCHOR_INDICES]
        values: list[float] = []
        for hand in (left_hand, right_hand):
            if len(hand) <= max(HAND_TIP_INDICES) or _is_zero_hand(hand):
                values.extend([0.0] * (len(HAND_TIP_INDICES) * len(anchors)))
                continue
            for tip_index in HAND_TIP_INDICES:
                tip = hand[tip_index]
                values.extend(tip.distance_to(anchor) for anchor in anchors)

        return np.asarray(values, dtype=np.float32)

    def _normalize(self, raw_landmarks: list[Point], distances: np.ndarray) -> np.ndarray:
        left_shoulder = raw_landmarks[LEFT_SHOULDER_LANDMARK_INDEX]
        right_shoulder = raw_landmarks[RIGHT_SHOULDER_LANDMARK_INDEX]
        center = Point(
            x=(left_shoulder.x + right_shoulder.x) / 2.0,
            y=(left_shoulder.y + right_shoulder.y) / 2.0,
            z=(left_shoulder.z + right_shoulder.z) / 2.0,
        )
        shoulder_width = left_shoulder.distance_to(right_shoulder)
        scale = shoulder_width if shoulder_width >= MIN_SHOULDER_WIDTH else 1.0

        output = np.zeros(INPUT_DIM, dtype=np.float32)
        offset = 0
        for point in raw_landmarks:
            output[offset] = (point.x - center.x) / scale
            output[offset + 1] = (point.y - center.y) / scale
            output[offset + 2] = (point.z - center.z) / scale
            offset += COORDINATE_SIZE

        output[offset : offset + HAND_FACE_DISTANCE_COUNT] = distances / scale
        if offset + HAND_FACE_DISTANCE_COUNT != INPUT_DIM:
            raise ValueError(f"Unexpected feature length: {offset + HAND_FACE_DISTANCE_COUNT}")
        return output


def _get_landmarks(results, attr_name: str):
    value = getattr(results, attr_name, None)
    if value is None:
        return None
    if hasattr(value, "landmark"):
        value = value.landmark
    if not value:
        return None
    return value


def _to_points(landmarks: Iterable) -> list[Point]:
    return [Point(float(lm.x), float(lm.y), float(lm.z)) for lm in landmarks]


def _is_zero_hand(hand: list[Point]) -> bool:
    return all(point == ZERO_POINT for point in hand)
