import logging
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, status
from fastapi.responses import JSONResponse

from app.model_service import ModelNotLoadedError, SignModelService
from app.schemas import (
    SignInferenceRequest,
    SignInferenceResponse,
)

logger = logging.getLogger("sign_ai")
sign_model_service = SignModelService()


def configure_sign_ai_logger() -> None:
    logger.setLevel(logging.INFO)
    logger.propagate = False
    if logger.handlers:
        return

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(
        logging.Formatter("%(levelname)s:%(name)s:%(message)s")
    )
    logger.addHandler(handler)


configure_sign_ai_logger()


@asynccontextmanager
async def lifespan(app: FastAPI):
    sign_model_service.load()
    yield


app = FastAPI(title="Sign AI Server", version="0.2.0", lifespan=lifespan)


@app.get("/health")
def health() -> JSONResponse:
    health_status = sign_model_service.health()
    http_status = (
        status.HTTP_200_OK
        if sign_model_service.is_loaded
        else status.HTTP_503_SERVICE_UNAVAILABLE
    )
    return JSONResponse(status_code=http_status, content=health_status)


@app.post("/internal/sign/predict", response_model=SignInferenceResponse)
def predict(
    request: SignInferenceRequest,
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
) -> SignInferenceResponse:
    trace_id = normalize_trace_id(x_trace_id)
    try:
        logger.info(
            "[SignInference] request start. traceId=%s, sequenceLength=%s, "
            "featureDimension=%s, topK=%s, requestedModelVersion=%s",
            trace_id,
            request.sequence_length,
            request.feature_dimension,
            request.top_k,
            request.model_version,
        )
        response = sign_model_service.predict(request, trace_id=trace_id)
        logger.info(
            "[SignInference] request success. traceId=%s, modelVersion=%s, "
            "inferenceMs=%s, accepted=%s, gloss=%s, confidence=%s, rejectionReason=%s",
            trace_id,
            response.model_version,
            response.inference_ms,
            response.accepted,
            response.gloss,
            response.confidence,
            response.rejection_reason,
        )
        return response
    except ModelNotLoadedError as exc:
        logger.warning(
            "[SignInference] model not loaded. traceId=%s, message=%s",
            trace_id,
            str(exc),
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc


def normalize_trace_id(trace_id: str | None) -> str:
    if trace_id is None or not trace_id.strip():
        return "-"
    return trace_id
