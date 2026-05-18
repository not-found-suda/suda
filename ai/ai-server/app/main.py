from time import perf_counter

from fastapi import FastAPI

from app.schemas import (
    SignInferenceCandidate,
    SignInferenceRequest,
    SignInferenceResponse,
)

app = FastAPI(title="Sign AI Server", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/internal/sign/predict", response_model=SignInferenceResponse)
def predict(request: SignInferenceRequest) -> SignInferenceResponse:
    started_at = perf_counter()
    model_version = request.model_version or "stub-v6-tcn"
    inference_ms = int((perf_counter() - started_at) * 1000)

    return SignInferenceResponse(
        gloss="unknown",
        confidence=0.0,
        margin=0.0,
        class_index=None,
        raw_gloss="unknown",
        accepted=False,
        rejection_reason="stub_response",
        top_candidates=[
            SignInferenceCandidate(
                rank=1,
                class_index=22,
                gloss="none",
                confidence=0.0,
            )
        ][: request.top_k],
        model_version=model_version,
        inference_ms=inference_ms,
    )
