FROM python:3.11-slim

WORKDIR /app

COPY database/requirements.txt /app/database/requirements.txt
RUN pip install --no-cache-dir -r /app/database/requirements.txt

COPY database /app/database

CMD ["python", "/app/database/init_database.py"]
