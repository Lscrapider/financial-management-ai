FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV HF_HOME=/app/.cache/huggingface

COPY ai-python/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

ARG EMBEDDING_MODEL_NAME=BAAI/bge-base-zh-v1.5
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${EMBEDDING_MODEL_NAME}')"

COPY ai-python/app /app/app
COPY env_loader.py /app/env_loader.py

CMD ["python", "-m", "app.worker.main"]
