FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV HF_HOME=/app/.cache/huggingface
ARG HF_ENDPOINT=https://hf-mirror.com
ENV HF_ENDPOINT=${HF_ENDPOINT}

COPY ai-python/requirements.txt /app/requirements.txt
COPY .ci-artifacts/python/ai-wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r /app/requirements.txt \
  && rm -rf /wheels

ARG EMBEDDING_MODEL_NAME=BAAI/bge-base-zh-v1.5
COPY .ci-artifacts/python/ai-huggingface /app/.cache/huggingface

COPY ai-python/app /app/app
COPY env_loader.py /app/env_loader.py

CMD ["python", "-m", "app.worker.main"]
