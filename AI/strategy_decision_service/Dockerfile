# syntax=docker/dockerfile:1.7
# This image is explicitly built for Linux x86_64 (amd64) Kubernetes nodes.
FROM --platform=linux/amd64 python:3.11-slim

WORKDIR /strategy-decision-service

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

ENTRYPOINT ["python", "main.py"]
