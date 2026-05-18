from pydantic import BaseModel, ConfigDict, Field, field_validator

DEFAULT_SEQUENCE_LENGTH = 30
DEFAULT_FEATURE_DIMENSION = 332
DEFAULT_TOP_K = 3
MAX_TOP_K = 5


def to_camel(value: str) -> str:
    parts = value.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SignInferenceRequest(CamelModel):
    model_version: str | None = None
    sequence_length: int = Field(default=DEFAULT_SEQUENCE_LENGTH, ge=1)
    feature_dimension: int = Field(default=DEFAULT_FEATURE_DIMENSION, ge=1)
    features: list[float]
    timestamps_ms: list[int] | None = None
    top_k: int = Field(default=DEFAULT_TOP_K, ge=1, le=MAX_TOP_K)

    @field_validator("features")
    @classmethod
    def validate_features(cls, value: list[float]) -> list[float]:
        if not value:
            raise ValueError("features must not be empty")
        return value

    @field_validator("features")
    @classmethod
    def validate_feature_length(cls, value: list[float], info) -> list[float]:
        data = info.data
        sequence_length = data.get("sequence_length", DEFAULT_SEQUENCE_LENGTH)
        feature_dimension = data.get("feature_dimension", DEFAULT_FEATURE_DIMENSION)
        expected_size = sequence_length * feature_dimension
        if len(value) != expected_size:
            raise ValueError("features length must equal sequenceLength * featureDimension")
        return value

    @field_validator("timestamps_ms")
    @classmethod
    def validate_timestamps(cls, value: list[int] | None, info) -> list[int] | None:
        if not value:
            return value
        sequence_length = info.data.get("sequence_length", DEFAULT_SEQUENCE_LENGTH)
        if len(value) != sequence_length:
            raise ValueError("timestampsMs length must equal sequenceLength")
        return value


class SignInferenceCandidate(CamelModel):
    rank: int
    class_index: int
    gloss: str
    confidence: float


class SignInferenceResponse(CamelModel):
    gloss: str
    confidence: float
    margin: float
    class_index: int | None = None
    raw_gloss: str | None = None
    accepted: bool
    rejection_reason: str | None = None
    top_candidates: list[SignInferenceCandidate] = Field(default_factory=list)
    model_version: str | None = None
    inference_ms: int | None = None
