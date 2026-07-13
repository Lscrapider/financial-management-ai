FROM python:3.12-slim

WORKDIR /app

COPY database/requirements.txt /app/database/requirements.txt
COPY .ci-artifacts/python/database-wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r /app/database/requirements.txt \
  && rm -rf /wheels

COPY database /app/database
COPY env_loader.py /app/env_loader.py

CMD ["python", "/app/database/init_database.py"]
