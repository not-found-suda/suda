# Sign AI Server

V6 PyTorch TCN 모델을 사용해 서버 측 수어 인식을 수행하는 FastAPI 서버입니다.

모델 artifact 파일은 repository에 커밋하지 않습니다. 로컬 실행 시에도 같은 학습 run에서
나온 모델 파일 세트를 하나의 artifact 디렉터리에 두고, Docker 실행 시에는 그 디렉터리를
컨테이너의 `/models` 경로에 read-only로 mount해서 사용합니다.

## 로컬 실행

아래 명령은 `ai/ai-server`에서 실행합니다. Docker 밖에서 실행할 때 상대 경로는 현재
작업 디렉터리 기준으로 해석되므로, 모델/config 파일 경로는 절대 경로 사용을 권장합니다.

```bash
$env:SIGN_MODEL_PATH="C:\path\to\sign-model-artifact\best_sign_model_v6.pt"
$env:SIGN_TRAIN_CONFIG_PATH="C:\path\to\sign-model-artifact\train_config_v6.json"
$env:SIGN_LABEL_MAP_PATH="C:\path\to\sign-model-artifact\label_map_v6.json"
$env:SIGN_MODEL_MODULE_PATH="C:\path\to\sign-model-artifact\model.py"
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Docker 실행: 같은 Compose에서 로컬 AI 서버 사용

```bash
docker compose --profile local-ai up --build ai-server
```

기본 Compose 설정에서는 `8000` 포트를 Docker network 내부에만 노출합니다. host에서 AI
서버를 직접 호출해야 한다면 로컬에서 `uvicorn`으로 실행하거나, 개발용 override compose
파일에서 localhost 전용 port를 열어 사용합니다.

Docker 실행 시에는 `SIGN_MODEL_ARTIFACT_PATH`로 지정한 host 디렉터리가 컨테이너 내부
`/models`로 mount됩니다. 이 디렉터리에는 같은 학습 run에서 나온 아래 파일들이 함께 있어야
합니다.

- `best_sign_model_v6.pt`
- `train_config_v6.json`
- `label_map_v6.json`
- `model.py`

Spring을 같은 Docker Compose의 backend 컨테이너로 실행하면서 로컬 AI 서버를 함께 띄우는
경우에는 아래처럼 실행하고, backend에는 `BACKEND_SIGN_AI_BASE_URL=http://ai-server:8000`
값이 주입되도록 설정합니다.

```bash
docker compose --profile local-ai up --build backend ai-server
```

Spring을 로컬에서 실행하는 경우 이 서버는 `SIGN_AI_BASE_URL=http://localhost:8000`으로
호출합니다.

## GPU 서버 단독 배포

FastAPI AI 서버만 GPU 서버에 올릴 때는 repository root에서 아래 compose 파일을 사용합니다.

```bash
docker compose -f docker-compose.ai.yml up -d --build
```

`docker-compose.ai.yml`은 NVIDIA Container Toolkit이 설치된 서버에서 `gpus: all`로
컨테이너를 실행합니다. Spring 서버가 다른 머신에서 접근해야 한다면 GPU 서버 보안그룹 또는
방화벽에서 Spring 서버만 `8000` 포트에 접근할 수 있게 제한하고, 실행 환경변수는 아래처럼
설정합니다.

```bash
SIGN_MODEL_ARTIFACT_PATH=/srv/sign-ai/models/v6_24words_tcn
AI_SERVER_BIND_HOST=0.0.0.0
AI_SERVER_PORT=8000
```

이때 Spring 서버에는 실행 방식에 맞춰 아래 값을 설정합니다.

```bash
# Spring을 직접 실행하는 경우
SIGN_AI_BASE_URL=http://<gpu-private-ip>:8000

# Docker Compose backend 컨테이너로 실행하는 경우
BACKEND_SIGN_AI_BASE_URL=http://<gpu-private-ip>:8000
```

외부 사용자에게 AI 서버를 직접 공개하지 않고, 모바일은 Spring API만 호출하는 구조를
유지합니다.

원격 GPU AI 서버를 사용하는 Docker Compose 배포에서는 `local-ai` profile을 켜지 않습니다.
따라서 backend는 로컬 `ai-server` healthcheck를 기다리지 않고, 설정된
`BACKEND_SIGN_AI_BASE_URL`로 원격 AI 서버를 호출합니다.

## Endpoints

- `GET /health`
- `POST /internal/sign/predict`

`GET /health`는 PyTorch 모델이 정상 로드되기 전까지 HTTP 503을 반환합니다.

추론 endpoint는 flatten된 `30 * 332` feature sequence를 입력으로 받고, top candidates,
confidence, margin, accepted/rejectionReason 정보를 반환합니다.

Spring에서 전달한 `X-Trace-Id`는 FastAPI 로그와 추론 응답의 `traceId`에 함께 남깁니다.
운영 로그에는 feature 원문을 남기지 않고 sequenceLength, featureDimension, topK,
modelVersion, inferenceMs, accepted, gloss, confidence 같은 요약 정보만 기록합니다.
