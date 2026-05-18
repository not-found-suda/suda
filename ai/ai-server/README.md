# Sign AI Server

FastAPI skeleton for server-side sign inference.

## Run

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Docker

```bash
docker compose up --build ai-server
```

The default Compose service exposes port `8000` only inside the Docker network. If you need to call
the AI server directly from the host, run it locally with `uvicorn` or open a localhost-only port in
a development override compose file.

When Spring runs in Docker Compose, it should call this service with
`SIGN_AI_BASE_URL=http://ai-server:8000`.

When Spring runs locally, it should call this service with
`SIGN_AI_BASE_URL=http://localhost:8000`.

## Endpoints

- `GET /health`
- `POST /internal/sign/predict`

The prediction endpoint currently returns a stub response. PyTorch model loading and real inference
will be added in a follow-up task.
