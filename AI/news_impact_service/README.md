# News Impact Prediction Service

## Role in the System

This service is responsible for the News Impact Analysis module of the 24-Hour Trading Bot. It consumes a stock ticker and news headline from RabbitMQ, predicts the expected market direction using the fine-tuned FinBERT model, and publishes the prediction to RabbitMQ for use in the trading decision pipeline.

## Model

- **Base Model:** ProsusAI/FinBERT
- **Fine-tuned on:** Kaggle Stock News Sentiment Dataset

## Performance

- Accuracy: **65.58%**
- F1 Score: **67.35%**

## Installation

```bash
pip install -r requirements.txt
```

## Run the Service

```bash
uvicorn app:app --reload
```

The service will be available at:

```
http://127.0.0.1:8000/docs
```

## RabbitMQ configuration

All RabbitMQ connection and topology values are configurable through environment variables.

| Variable | Default |
| --- | --- |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |
| `RABBITMQ_VHOST` | `/` |
| `RABBITMQ_HEARTBEAT` | `60` |
| `RABBITMQ_BLOCKED_CONNECTION_TIMEOUT` | `300` |
| `RABBITMQ_RETRY_DELAY_SECONDS` | `5` |
| `RABBITMQ_INPUT_EXCHANGE` | `financial-news-exchange` |
| `RABBITMQ_INPUT_EXCHANGE_TYPE` | `direct` |
| `RABBITMQ_INPUT_EXCHANGE_DURABLE` | `true` |
| `RABBITMQ_INPUT_QUEUE` | `financial-news` |
| `RABBITMQ_INPUT_QUEUE_DURABLE` | `true` |
| `RABBITMQ_INPUT_ROUTING_KEY` | `financial-news` |
| `RABBITMQ_OUTPUT_EXCHANGE` | `news-sentiment-exchange` |
| `RABBITMQ_OUTPUT_EXCHANGE_TYPE` | `direct` |
| `RABBITMQ_OUTPUT_EXCHANGE_DURABLE` | `true` |
| `RABBITMQ_OUTPUT_QUEUE` | `news-sentiment` |
| `RABBITMQ_OUTPUT_QUEUE_DURABLE` | `true` |
| `RABBITMQ_OUTPUT_ROUTING_KEY` | `news-sentiment` |
| `RABBITMQ_OUTPUT_DELIVERY_MODE` | `2` |

The service uses consumer auto-acknowledgement. A processing failure is logged but is not retried or negatively acknowledged.

For example:

```bash
export RABBITMQ_HOST=localhost
export RABBITMQ_USER=guest
export RABBITMQ_PASSWORD=guest
export RABBITMQ_VHOST=/
```
