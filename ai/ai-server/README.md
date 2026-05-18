# Sign AI Server

V6 PyTorch TCN 모델을 사용해 서버 측 수어 인식을 수행하는 FastAPI 서버입니다.

모델 artifact 파일은 repository에 커밋하지 않습니다. 로컬 실행 시에는 각 파일 경로를
환경변수로 지정하고, Docker 실행 시에는 같은 artifact 디렉터리를 `/models` 경로에
mount해서 사용합니다.

## 로컬 실행

아래 명령은 `ai/ai-server`에서 실행합니다. Docker 밖에서 실행할 때 상대 경로는 현재
작업 디렉터리 기준으로 해석되므로, 모델/config 파일 경로는 절대 경로 사용을 권장합니다.

```bash
$env:SIGN_MODEL_PATH="C:\path\to\best_sign_model_v6.pt"
$env:SIGN_TRAIN_CONFIG_PATH="C:\path\to\S14P31A404\ai\models\tcn_mobile\train_config_v6.json"
$env:SIGN_LABEL_MAP_PATH="C:\path\to\S14P31A404\ai\models\tcn_mobile\label_map_v6.json"
$env:SIGN_MODEL_MODULE_PATH="C:\path\to\S14P31A404\ai\src\sign2text_tcn\model.py"
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Docker 실행

```bash
docker compose up --build ai-server
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

Spring을 Docker Compose에서 실행하는 경우 이 서버는
`SIGN_AI_BASE_URL=http://ai-server:8000`으로 호출합니다.

Spring을 로컬에서 실행하는 경우 이 서버는 `SIGN_AI_BASE_URL=http://localhost:8000`으로
호출합니다.

## Endpoints

- `GET /health`
- `POST /internal/sign/predict`

`GET /health`는 PyTorch 모델이 정상 로드되기 전까지 HTTP 503을 반환합니다.

추론 endpoint는 flatten된 `30 * 332` feature sequence를 입력으로 받고, top candidates,
confidence, margin, accepted/rejectionReason 정보를 반환합니다.
