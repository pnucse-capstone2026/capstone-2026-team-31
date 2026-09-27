import json
import logging
import os
import threading
import time

import pika
from fastapi import FastAPI
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    pipeline,
)


# --------------------------------------------------
# Model configuration
# --------------------------------------------------

MODEL_NAME = os.getenv("MODEL_NAME", "andreamena/finbert-stock-news-impact-v10")
MODEL_PATH = os.getenv("MODEL_PATH", MODEL_NAME)
MODEL_LOCAL_FILES_ONLY = os.getenv("MODEL_LOCAL_FILES_ONLY", "false").lower() in (
    "true",
    "1",
    "yes",
)

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


# --------------------------------------------------
# RabbitMQ configuration
# --------------------------------------------------

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "trading-bot-dev")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "1234")
RABBITMQ_VHOST = os.getenv("RABBITMQ_VHOST", "trading-bot-dev-vhost")
RABBITMQ_HEARTBEAT = int(os.getenv("RABBITMQ_HEARTBEAT", "60"))
RABBITMQ_BLOCKED_CONNECTION_TIMEOUT = int(
    os.getenv("RABBITMQ_BLOCKED_CONNECTION_TIMEOUT", "300")
)
RABBITMQ_RETRY_DELAY_SECONDS = int(
    os.getenv("RABBITMQ_RETRY_DELAY_SECONDS", "5")
)
RABBITMQ_INPUT_EXCHANGE_TYPE = os.getenv(
    "RABBITMQ_INPUT_EXCHANGE_TYPE", "direct"
)
RABBITMQ_OUTPUT_EXCHANGE_TYPE = os.getenv(
    "RABBITMQ_OUTPUT_EXCHANGE_TYPE", "direct"
)
RABBITMQ_INPUT_EXCHANGE_DURABLE = os.getenv(
    "RABBITMQ_INPUT_EXCHANGE_DURABLE", "true"
).lower() in ("true", "1", "yes")
RABBITMQ_OUTPUT_EXCHANGE_DURABLE = os.getenv(
    "RABBITMQ_OUTPUT_EXCHANGE_DURABLE", "true"
).lower() in ("true", "1", "yes")
RABBITMQ_INPUT_QUEUE_DURABLE = os.getenv(
    "RABBITMQ_INPUT_QUEUE_DURABLE", "true"
).lower() in ("true", "1", "yes")
RABBITMQ_OUTPUT_QUEUE_DURABLE = os.getenv(
    "RABBITMQ_OUTPUT_QUEUE_DURABLE", "true"
).lower() in ("true", "1", "yes")
RABBITMQ_OUTPUT_DELIVERY_MODE = int(
    os.getenv("RABBITMQ_OUTPUT_DELIVERY_MODE", "2")
)

# Existing News Ingestion Service RabbitMQ setup
INPUT_EXCHANGE = os.getenv("RABBITMQ_INPUT_EXCHANGE", "financial-news-exchange")
INPUT_QUEUE = os.getenv("RABBITMQ_INPUT_QUEUE", "financial-news")
INPUT_ROUTING_KEY = os.getenv("RABBITMQ_INPUT_ROUTING_KEY", "financial-news")

# Sentiment Service output
OUTPUT_EXCHANGE = os.getenv("RABBITMQ_OUTPUT_EXCHANGE", "news-sentiment-exchange")
OUTPUT_QUEUE = os.getenv("RABBITMQ_OUTPUT_QUEUE", "news-sentiment")
OUTPUT_ROUTING_KEY = os.getenv("RABBITMQ_OUTPUT_ROUTING_KEY", "news-sentiment")


# --------------------------------------------------
# FastAPI
# --------------------------------------------------

app = FastAPI(
    title="News Impact Prediction Service",
    description=(
        "Fine-tuned FinBERT service for predicting stock-news impact "
        "and processing financial news through RabbitMQ."
    ),
    version="2.0.0",
)


# --------------------------------------------------
# Load model
# --------------------------------------------------

tokenizer = AutoTokenizer.from_pretrained(
    MODEL_PATH,
    local_files_only=MODEL_LOCAL_FILES_ONLY,
)

model = AutoModelForSequenceClassification.from_pretrained(
    MODEL_PATH,
    local_files_only=MODEL_LOCAL_FILES_ONLY,
)

classifier = pipeline(
    "text-classification",
    model=model,
    tokenizer=tokenizer,
)


# --------------------------------------------------
# Shared prediction function
# --------------------------------------------------

def run_prediction(ticker: str, headline: str):
    ticker = ticker.upper().strip()
    headline = headline.strip()

    # V10 was trained using ticker context + headline
    model_input = f"{ticker} [SEP] {headline}"

    result = classifier(model_input)[0]

    label = result["label"]
    confidence = float(result["score"])

    if label == "LABEL_1":
        direction = "UP"
        numeric_label = 1
    else:
        direction = "DOWN"
        numeric_label = 0

    # Contract consumed by Price Prediction Service.
    return {
        "ticker": ticker,
        "label": numeric_label,
        "confidence": round(confidence, 4),
    }


# --------------------------------------------------
# REST endpoint
# --------------------------------------------------

@app.get("/")
def root():
    return {
        "service": "News Impact Prediction Service",
        "status": "running",
        "model": MODEL_NAME,
        "rabbitmq": {
            "inputQueue": INPUT_QUEUE,
            "outputQueue": OUTPUT_QUEUE,
        },
    }


# RabbitMQ setup
# --------------------------------------------------

def create_rabbitmq_connection():
    credentials = pika.PlainCredentials(
        username=RABBITMQ_USER,
        password=RABBITMQ_PASSWORD,
    )

    return pika.BlockingConnection(
        pika.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            virtual_host=RABBITMQ_VHOST,
            credentials=credentials,
            heartbeat=RABBITMQ_HEARTBEAT,
            blocked_connection_timeout=RABBITMQ_BLOCKED_CONNECTION_TIMEOUT,
        )
    )


def rabbitmq_consumer():
    while True:
        try:
            connection = create_rabbitmq_connection()
            channel = connection.channel()

            # Match the News Ingestion Service setup
            channel.exchange_declare(
                exchange=INPUT_EXCHANGE,
                exchange_type=RABBITMQ_INPUT_EXCHANGE_TYPE,
                durable=RABBITMQ_INPUT_EXCHANGE_DURABLE,
            )

            channel.queue_declare(
                queue=INPUT_QUEUE,
                durable=RABBITMQ_INPUT_QUEUE_DURABLE,
            )

            channel.queue_bind(
                exchange=INPUT_EXCHANGE,
                queue=INPUT_QUEUE,
                routing_key=INPUT_ROUTING_KEY,
            )

            # Declare our sentiment output
            channel.exchange_declare(
                exchange=OUTPUT_EXCHANGE,
                exchange_type=RABBITMQ_OUTPUT_EXCHANGE_TYPE,
                durable=RABBITMQ_OUTPUT_EXCHANGE_DURABLE,
            )

            channel.queue_declare(
                queue=OUTPUT_QUEUE,
                durable=RABBITMQ_OUTPUT_QUEUE_DURABLE,
            )

            channel.queue_bind(
                exchange=OUTPUT_EXCHANGE,
                queue=OUTPUT_QUEUE,
                routing_key=OUTPUT_ROUTING_KEY,
            )

            logger.info("Connection with RabbitMQ established. Waiting for messages from queue '%s'...", INPUT_QUEUE)

            def callback(_channel, _method, _properties, body):
                try:
                    message = json.loads(
                        body.decode("utf-8")
                    )

                    ticker = message["ticker"]
                    headline = message["headline"]

                    prediction = run_prediction(
                        ticker=ticker,
                        headline=headline,
                    )

                    _channel.basic_publish(
                        exchange=OUTPUT_EXCHANGE,
                        routing_key=OUTPUT_ROUTING_KEY,
                        body=json.dumps(prediction),
                        properties=pika.BasicProperties(
                            content_type="application/json",
                            delivery_mode=RABBITMQ_OUTPUT_DELIVERY_MODE,
                        ),
                    )



                except Exception as e:
                    # With auto-ack, RabbitMQ has already acknowledged this delivery.
                    # Log the failure and leave the message unprocessed.
                    logger.exception(
                        "Error processing RabbitMQ message (auto-ack=true): %s", e
                    )

            channel.basic_consume(
                queue=INPUT_QUEUE,
                on_message_callback=callback,
                auto_ack=True,
            )

            channel.start_consuming()

        except Exception as e:
            logger.exception("RabbitMQ connection error: %s", e)
            logger.info(
                "Retrying connection in %s seconds...",
                RABBITMQ_RETRY_DELAY_SECONDS,
            )

            time.sleep(RABBITMQ_RETRY_DELAY_SECONDS)


# --------------------------------------------------
# Start RabbitMQ consumer with FastAPI
# --------------------------------------------------

@app.on_event("startup")
def startup_event():
    rabbit_thread = threading.Thread(
        target=rabbitmq_consumer,
        daemon=True,
    )

    rabbit_thread.start()
