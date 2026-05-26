FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV SIGN_MODEL_PATH=/models/best_sign_model_v6.pt
ENV SIGN_TRAIN_CONFIG_PATH=/models/train_config_v6.json
ENV SIGN_LABEL_MAP_PATH=/models/label_map_v6.json
ENV SIGN_MODEL_MODULE_PATH=/models/model.py

COPY ai-server/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ai-server/app ./app

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
