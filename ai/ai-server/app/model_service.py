import importlib.util
import json
import os
from dataclasses import dataclass
from pathlib import Path
from time import perf_counter
from typing import Any

from app.schemas import (
    DEFAULT_FEATURE_DIMENSION,
    DEFAULT_SEQUENCE_LENGTH,
    SignInferenceCandidate,
    SignInferenceRequest,
    SignInferenceResponse,
)

DEFAULT_CONFIDENCE_THRESHOLD = 0.80
DEFAULT_NONE_LABEL = "none"


class ModelNotLoadedError(RuntimeError):
    pass


@dataclass(frozen=True)
class ModelSettings:
    model_path: Path
    train_config_path: Path
    label_map_path: Path
    model_module_path: Path
    confidence_threshold: str
    none_label: str
    model_version: str | None


class SignModelService:
    def __init__(self) -> None:
        self.settings = load_model_settings()
        self.model: Any | None = None
        self.torch: Any | None = None
        self.device: Any | None = None
        self.label_map: dict[int, str] = {}
        self.model_version: str | None = None
        self.confidence_threshold = DEFAULT_CONFIDENCE_THRESHOLD
        self.load_error: str | None = None

    @property
    def is_loaded(self) -> bool:
        return self.model is not None

    def load(self) -> None:
        try:
            self._load()
            self.load_error = None
        except Exception as exc:
            self.model = None
            self.torch = None
            self.device = None
            self.label_map = {}
            self.model_version = None
            self.load_error = str(exc)

    def health(self) -> dict[str, Any]:
        return {
            "status": "ok" if self.is_loaded else "degraded",
            "modelLoaded": self.is_loaded,
            "device": str(self.device) if self.device is not None else None,
            "modelVersion": self.model_version,
            "confidenceThreshold": self.confidence_threshold,
            "error": self.load_error,
        }

    def predict(
        self,
        request: SignInferenceRequest,
        trace_id: str | None = None,
    ) -> SignInferenceResponse:
        if not self.is_loaded or self.torch is None or self.device is None:
            raise ModelNotLoadedError(self.load_error or "sign model is not loaded")

        started_at = perf_counter()
        input_tensor = (
            self.torch.tensor(request.features, dtype=self.torch.float32)
            .reshape(1, request.sequence_length, request.feature_dimension)
            .to(self.device)
        )

        with self.torch.no_grad():
            probs = self.model(input_tensor, return_probs=True)[0]

        top_k = min(request.top_k, len(self.label_map))
        top_probs, top_indices = self.torch.topk(probs, k=top_k)
        top_probs = top_probs.detach().cpu().tolist()
        top_indices = top_indices.detach().cpu().tolist()

        top_candidates = [
            SignInferenceCandidate(
                rank=rank,
                class_index=int(class_index),
                gloss=self.label_map.get(int(class_index), str(class_index)),
                confidence=float(confidence),
            )
            for rank, (class_index, confidence) in enumerate(
                zip(top_indices, top_probs, strict=True),
                start=1,
            )
        ]

        top_candidate = top_candidates[0]
        second_confidence = top_candidates[1].confidence if len(top_candidates) > 1 else 0.0
        margin = top_candidate.confidence - second_confidence
        raw_gloss = top_candidate.gloss
        accepted, rejection_reason, gloss = self._resolve_acceptance(
            raw_gloss,
            top_candidate.confidence,
        )
        inference_ms = int((perf_counter() - started_at) * 1000)

        return SignInferenceResponse(
            gloss=gloss,
            confidence=top_candidate.confidence,
            margin=margin,
            class_index=top_candidate.class_index,
            raw_gloss=raw_gloss,
            accepted=accepted,
            rejection_reason=rejection_reason,
            top_candidates=top_candidates,
            model_version=self.model_version,
            inference_ms=inference_ms,
            trace_id=trace_id,
        )

    def _load(self) -> None:
        confidence_threshold = parse_confidence_threshold(self.settings.confidence_threshold)
        if not self.settings.model_path.exists():
            raise FileNotFoundError(f"model file not found: {self.settings.model_path}")
        if not self.settings.train_config_path.exists():
            raise FileNotFoundError(
                f"train config file not found: {self.settings.train_config_path}"
            )
        if not self.settings.label_map_path.exists():
            raise FileNotFoundError(f"label map file not found: {self.settings.label_map_path}")
        if not self.settings.model_module_path.exists():
            raise FileNotFoundError(f"model module file not found: {self.settings.model_module_path}")

        import torch

        train_config = load_json(self.settings.train_config_path)
        label_map = load_label_map(self.settings.label_map_path)
        validate_model_contract(train_config, label_map)

        model_module = load_python_module(self.settings.model_module_path)
        model_type = train_config.get("model_type", "tcn")
        model = model_module.build_ksl_model_v6(
            model_type=model_type,
            input_dim=int(train_config.get("input_dim", DEFAULT_FEATURE_DIMENSION)),
            num_classes=len(label_map),
            seq_len=int(train_config.get("max_len", DEFAULT_SEQUENCE_LENGTH)),
            d_model=int(train_config.get("d_model", 128)),
            num_heads=int(train_config.get("num_heads", 8)),
            num_layers=int(train_config.get("num_layers", 3)),
        )

        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        state_dict = load_state_dict(torch, self.settings.model_path, device)
        if isinstance(state_dict, dict) and "model_state_dict" in state_dict:
            state_dict = state_dict["model_state_dict"]

        model.load_state_dict(state_dict)
        model.to(device)
        model.eval()

        self.torch = torch
        self.device = device
        self.model = model
        self.label_map = label_map
        self.confidence_threshold = confidence_threshold
        self.model_version = resolve_model_version(
            self.settings,
            train_config,
            self.settings.model_path,
        )

    def _resolve_acceptance(self, raw_gloss: str, confidence: float) -> tuple[bool, str | None, str]:
        if confidence < self.confidence_threshold:
            return False, "low_confidence", self.settings.none_label
        if raw_gloss == self.settings.none_label:
            return False, "none_class", self.settings.none_label
        return True, None, raw_gloss


def load_model_settings() -> ModelSettings:
    project_root = discover_project_root()
    default_model_path = project_root / "models" / "tcn_mobile" / "best_sign_model_v6.pt"
    default_train_config_path = project_root / "models" / "tcn_mobile" / "train_config_v6.json"
    default_label_map_path = project_root / "models" / "tcn_mobile" / "label_map_v6.json"
    default_model_module_path = project_root / "src" / "sign2text_tcn" / "model.py"

    return ModelSettings(
        model_path=resolve_path(os.getenv("SIGN_MODEL_PATH"), default_model_path),
        train_config_path=resolve_path(
            os.getenv("SIGN_TRAIN_CONFIG_PATH"),
            default_train_config_path,
        ),
        label_map_path=resolve_path(os.getenv("SIGN_LABEL_MAP_PATH"), default_label_map_path),
        model_module_path=resolve_path(
            os.getenv("SIGN_MODEL_MODULE_PATH"),
            default_model_module_path,
        ),
        confidence_threshold=os.getenv(
            "SIGN_CONFIDENCE_THRESHOLD",
            str(DEFAULT_CONFIDENCE_THRESHOLD),
        ),
        none_label=os.getenv("SIGN_NONE_LABEL", DEFAULT_NONE_LABEL),
        model_version=os.getenv("SIGN_MODEL_VERSION"),
    )


def discover_project_root() -> Path:
    marker = Path("src") / "sign2text_tcn" / "model.py"
    for parent in Path(__file__).resolve().parents:
        if (parent / marker).exists():
            return parent
    return Path.cwd()


def resolve_path(value: str | None, default: Path) -> Path:
    if not value:
        return default.resolve()
    path = Path(value)
    if path.is_absolute():
        return path
    return (Path.cwd() / path).resolve()


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_label_map(path: Path) -> dict[int, str]:
    raw_label_map = load_json(path)
    return {int(index): str(label) for index, label in raw_label_map.items()}


def validate_model_contract(train_config: dict[str, Any], label_map: dict[int, str]) -> None:
    input_dim = int(train_config.get("input_dim", DEFAULT_FEATURE_DIMENSION))
    max_len = int(train_config.get("max_len", DEFAULT_SEQUENCE_LENGTH))
    normalize_mode = train_config.get("stats", {}).get("normalize_mode", "none")

    if input_dim != DEFAULT_FEATURE_DIMENSION:
        raise ValueError(f"unsupported input_dim={input_dim}; expected {DEFAULT_FEATURE_DIMENSION}")
    if max_len != DEFAULT_SEQUENCE_LENGTH:
        raise ValueError(f"unsupported max_len={max_len}; expected {DEFAULT_SEQUENCE_LENGTH}")
    if normalize_mode not in ("none", "raw", "off"):
        raise ValueError(f"unsupported normalize_mode={normalize_mode}; expected none/raw/off")
    if sorted(label_map) != list(range(len(label_map))):
        raise ValueError("label_map indexes must be contiguous from 0")


def parse_confidence_threshold(value: str) -> float:
    threshold = float(value)
    if threshold < 0.0 or threshold > 1.0:
        raise ValueError("SIGN_CONFIDENCE_THRESHOLD must be between 0.0 and 1.0")
    return threshold


def load_state_dict(torch_module: Any, model_path: Path, device: Any) -> Any:
    try:
        return torch_module.load(model_path, map_location=device, weights_only=True)
    except TypeError:
        return torch_module.load(model_path, map_location=device)


def load_python_module(module_path: Path) -> Any:
    spec = importlib.util.spec_from_file_location("sign2text_tcn_model", module_path)
    if spec is None or spec.loader is None:
        raise ImportError(f"failed to load model module: {module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def resolve_model_version(
    settings: ModelSettings,
    train_config: dict[str, Any],
    model_path: Path,
) -> str:
    if settings.model_version:
        return settings.model_version
    model_dir_name = train_config.get("model_dir_name")
    if model_dir_name:
        return str(model_dir_name)
    return model_path.stem
