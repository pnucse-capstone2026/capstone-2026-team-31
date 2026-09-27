# syntax=docker/dockerfile:1.7
FROM --platform=linux/amd64 python:3.11-slim

WORKDIR /price-prediction-service

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/opt/huggingface \
    HF_HUB_OFFLINE=1

RUN pip install --no-cache-dir \
    pika \
    numpy \
    tensorflow \
    joblib \
    huggingface_hub \
    scikit-learn

ARG MODEL_REPO=andreamena/price-prediction-lstm-stock-news
ARG MODEL_REVISION=ef240954a295fc87de033ca853be215b567d53f8

# Download the reviewed model artifacts while building.  The runtime is kept
# offline so the worker loads these baked artifacts without a network request.
RUN HF_HUB_OFFLINE=0 python -c "from huggingface_hub import snapshot_download; snapshot_download(repo_id='${MODEL_REPO}', revision='${MODEL_REVISION}', allow_patterns=['price_prediction_lstm_v1.keras', 'price_scaler_v1.pkl', 'news_scaler_v1.pkl', 'metadata.json'])"

COPY consumer.py .

ENTRYPOINT ["python", "consumer.py"]
