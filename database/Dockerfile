FROM python:3.11-slim

WORKDIR /app

COPY database/requirements.txt /app/database/requirements.txt
RUN pip install --no-cache-dir -r /app/database/requirements.txt

COPY database /app/database
COPY env_loader.py /app/env_loader.py

CMD ["python", "/app/database/init_database.py"]
