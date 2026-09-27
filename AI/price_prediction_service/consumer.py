import os
import json
import math
import time
import uuid
from datetime import datetime, timezone
from collections import defaultdict, deque

import pika
import numpy as np
import tensorflow as tf
import joblib
from huggingface_hub import hf_hub_download

# 1. Environment & Configuration
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "guest")
RABBITMQ_VHOST = os.getenv("RABBITMQ_VHOST", "/")
# Defaults match the resource names declared by the existing Java publishers.
RABBITMQ_MARKET_TICKS_QUEUE = os.getenv("RABBITMQ_MARKET_TICKS_QUEUE", "market-data")
RABBITMQ_NEWS_SENTIMENT_QUEUE = os.getenv("RABBITMQ_NEWS_SENTIMENT_QUEUE", "news-sentiment")
RABBITMQ_PREDICTIONS_QUEUE = os.getenv("RABBITMQ_PREDICTIONS_QUEUE", "predictions")
RABBITMQ_PREDICTIONS_EXCHANGE = os.getenv(
    "RABBITMQ_PREDICTIONS_EXCHANGE", "predictions-exchange"
)
RABBITMQ_QUEUE_DURABLE = os.getenv("RABBITMQ_QUEUE_DURABLE", "true").lower() == "true"
RABBITMQ_MARKET_DATA_EXCHANGE = os.getenv(
    "RABBITMQ_MARKET_DATA_EXCHANGE", "market-data-exchange"
)
RABBITMQ_MARKET_DATA_ROUTING_KEY = os.getenv(
    "RABBITMQ_MARKET_DATA_ROUTING_KEY", "market-data"
)
RABBITMQ_NEWS_SENTIMENT_EXCHANGE = os.getenv(
    "RABBITMQ_NEWS_SENTIMENT_EXCHANGE", "news-sentiment-exchange"
)
RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY = os.getenv(
    "RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY", "news-sentiment"
)

REPO_ID = "andreamena/price-prediction-lstm-stock-news"
# Keep the consumer and its upstream Market Data contract on the same reviewed
# artifact revision. Override only as part of a coordinated model/contract rollout.
MODEL_REVISION = os.getenv(
    "PRICE_PREDICTION_MODEL_REVISION", "ef240954a295fc87de033ca853be215b567d53f8"
)
MODEL_FILE = "price_prediction_lstm_v1.keras"
PRICE_SCALER_FILE = "price_scaler_v1.pkl"
NEWS_SCALER_FILE = "news_scaler_v1.pkl"
METADATA_FILE = "metadata.json"

# 2. Load Model & Scalers
print("Loading model artifacts...")
model_path = hf_hub_download(repo_id=REPO_ID, filename=MODEL_FILE, revision=MODEL_REVISION)
price_scaler_path = hf_hub_download(repo_id=REPO_ID, filename=PRICE_SCALER_FILE, revision=MODEL_REVISION)
news_scaler_path = hf_hub_download(repo_id=REPO_ID, filename=NEWS_SCALER_FILE, revision=MODEL_REVISION)
metadata_path = hf_hub_download(repo_id=REPO_ID, filename=METADATA_FILE, revision=MODEL_REVISION)

model = tf.keras.models.load_model(model_path)
price_scaler = joblib.load(price_scaler_path)
news_scaler = joblib.load(news_scaler_path)
with open(metadata_path, "r") as f:
    metadata = json.load(f)

# In-memory storage for rolling price sequences, latest news, and the most
# recent market event per ticker.  The event clock is intentionally separate
# from wall time: historical replay can run faster than real time, but the
# model's sentiment decay and the measurement horizon must still advance on
# the replay timeline.
# Each deque discards its oldest item as soon as a new bar exceeds the window.
SEQUENCE_LENGTH = metadata.get("sequence_length", 20)
price_buffer = defaultdict(lambda: deque(maxlen=SEQUENCE_LENGTH))
latest_news = {}
latest_market_event = {}


def parse_event_time(value):
    """Return an aware UTC timestamp from the existing market-tick contract."""
    if value is None:
        return None
    try:
        if isinstance(value, (int, float)):
            return datetime.fromtimestamp(value, tz=timezone.utc)
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).astimezone(timezone.utc)
    except (TypeError, ValueError, OverflowError):
        return None


def iso_utc(value):
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")

def apply_sentiment_decay(sentiment_data, reference_time):
    """
    Decays news sentiment towards 0 over time (e.g., half-life of 30 minutes).
    If no news exists, returns a neutral 0.0 impact.
    """
    if not sentiment_data:
        return 0.0, 0.0, 0.0
    
    # A negative age can only arise from an out-of-order upstream delivery.
    # Treat it as zero rather than amplifying the news signal or looking ahead.
    elapsed_minutes = max(
        0.0,
        (reference_time - sentiment_data["timestamp"]).total_seconds() / 60.0,
    )
    decay_factor = np.exp(-0.023 * elapsed_minutes) # decays significantly over 30-60 mins
    
    news_sentiment = sentiment_data["sentiment"]
    impact_confidence = sentiment_data["confidence"] * decay_factor
    weighted_news_impact = news_sentiment * impact_confidence
    
    return news_sentiment, impact_confidence, weighted_news_impact

def process_prediction(ticker, channel, triggered_by):
    """Runs LSTM inference when a complete price window is buffered."""
    processing_started = time.perf_counter()
    bars = price_buffer.get(ticker)
    if bars is None or len(bars) < SEQUENCE_LENGTH:
        return

    event_context = latest_market_event.get(ticker, {})
    # Live producers that do not include a usable timestamp retain the former
    # wall-clock behaviour. Historical replay always supplies this timestamp.
    reference_time = event_context.get("timestamp") or datetime.now(timezone.utc)

    # Strategy needs the price that corresponds to this prediction to size its
    # order and to construct a market-reachable limit price.  The latest close
    # is the most recent price registered for this ticker in our rolling window.
    latest_price = float(bars[-1]["Close"])
    if not math.isfinite(latest_price) or latest_price <= 0:
        print(f" [x] Skipping prediction for {ticker}: invalid latest close {latest_price!r}")
        return

    price_features = metadata["price_features"]
    price_array = np.array(
        [[bar[feature] for feature in price_features] for bar in bars],
        dtype=np.float32,
    )
    
    # Apply time-decay to news
    s_val, s_conf, s_weighted = apply_sentiment_decay(
        latest_news.get(ticker), reference_time
    )
    news_array = np.array([[s_val, s_conf, s_weighted]], dtype=np.float32)

    # Scale inputs
    price_scaled = price_scaler.transform(price_array).reshape(
        1, SEQUENCE_LENGTH, len(price_features)
    )
    news_scaled = news_scaler.transform(news_array)

    # Predict
    inference_started = time.perf_counter()
    prob = float(model.predict([price_scaled, news_scaled], verbose=0)[0][0])
    inference_ms = (time.perf_counter() - inference_started) * 1000.0
    direction = "UP" if prob >= 0.5 else "DOWN"
    confidence = prob if prob >= 0.5 else 1 - prob

    prediction_payload = {
        "predictionId": str(uuid.uuid4()),
        "ticker": ticker,
        "direction": direction,
        "probabilityUp": round(prob, 4),
        "confidence": round(confidence, 4),
        "latestPrice": latest_price,
        "triggeredBy": triggered_by,
        # Direction Evaluation compares this value with market-tick time.  It
        # must therefore use replay time at any playback speed, not wall time.
        "timestamp": iso_utc(reference_time),
        "modelVersion": MODEL_REVISION,
        # Additive observability metadata. Existing strategy and measurement
        # consumers parse only their established fields and ignore this object.
        "telemetry": {
            "inputEventId": event_context.get("event_id"),
            "logicalEventTimestamp": iso_utc(reference_time),
            "receivedAt": iso_utc(datetime.now(timezone.utc)),
            "inferenceMs": round(inference_ms, 3),
        },
    }

    # Publish to the configured prediction destination.
    total_processing_ms = (time.perf_counter() - processing_started) * 1000.0
    prediction_payload["telemetry"]["publishedAt"] = iso_utc(datetime.now(timezone.utc))
    prediction_payload["telemetry"]["totalProcessingMs"] = round(total_processing_ms, 3)
    channel.basic_publish(
        exchange=RABBITMQ_PREDICTIONS_EXCHANGE,
        routing_key=RABBITMQ_PREDICTIONS_QUEUE,
        body=json.dumps(prediction_payload)
    )
    print(json.dumps({
        "eventType": "prediction_performance",
        "predictionId": prediction_payload["predictionId"],
        "ticker": ticker,
        "triggeredBy": triggered_by,
        "inputEventId": event_context.get("event_id"),
        "logicalEventTimestamp": iso_utc(reference_time),
        "receivedAt": prediction_payload["telemetry"]["receivedAt"],
        "publishedAt": prediction_payload["telemetry"]["publishedAt"],
        "inferenceMs": round(inference_ms, 3),
        "totalProcessingMs": round(total_processing_ms, 3),
    }, sort_keys=True))
    print(f" [x] Published prediction for {ticker}: {direction} ({confidence*100:.1f}%)")

def main():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    connection = pika.BlockingConnection(
        pika.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            virtual_host=RABBITMQ_VHOST,
            credentials=credentials,
        )
    )
    channel = connection.channel()

    # Bind the input queues to the direct exchanges used by the Java Market Data
    # Service and the News Impact Service. Queue declaration alone does not bind
    # a queue to a RabbitMQ direct exchange.
    channel.exchange_declare(
        exchange=RABBITMQ_MARKET_DATA_EXCHANGE,
        exchange_type="direct",
        durable=RABBITMQ_QUEUE_DURABLE,
    )
    channel.exchange_declare(
        exchange=RABBITMQ_NEWS_SENTIMENT_EXCHANGE,
        exchange_type="direct",
        durable=RABBITMQ_QUEUE_DURABLE,
    )
    channel.queue_declare(queue=RABBITMQ_MARKET_TICKS_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    channel.queue_declare(queue=RABBITMQ_NEWS_SENTIMENT_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    channel.queue_declare(queue=RABBITMQ_PREDICTIONS_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    if RABBITMQ_PREDICTIONS_EXCHANGE:
        channel.exchange_declare(
            exchange=RABBITMQ_PREDICTIONS_EXCHANGE,
            exchange_type="direct",
            durable=RABBITMQ_QUEUE_DURABLE,
        )
        channel.queue_bind(
            exchange=RABBITMQ_PREDICTIONS_EXCHANGE,
            queue=RABBITMQ_PREDICTIONS_QUEUE,
            routing_key=RABBITMQ_PREDICTIONS_QUEUE,
        )
    channel.queue_bind(
        exchange=RABBITMQ_MARKET_DATA_EXCHANGE,
        queue=RABBITMQ_MARKET_TICKS_QUEUE,
        routing_key=RABBITMQ_MARKET_DATA_ROUTING_KEY,
    )
    channel.queue_bind(
        exchange=RABBITMQ_NEWS_SENTIMENT_EXCHANGE,
        queue=RABBITMQ_NEWS_SENTIMENT_QUEUE,
        routing_key=RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY,
    )

    def on_market_tick(ch, method, properties, body):
        tick = json.loads(body)
        ticker = tick["ticker"]
        event_time = parse_event_time(tick.get("timestamp"))
        # Never move the replay clock backwards for a ticker. This guards the
        # no-look-ahead invariant when a broker reconnect redelivers an older
        # tick after a newer one has already been processed.
        current = latest_market_event.get(ticker, {}).get("timestamp")
        if event_time is None:
            event_time = current or datetime.now(timezone.utc)
        elif current is not None and event_time < current:
            print(f" [x] Ignoring out-of-order market tick for {ticker}: {iso_utc(event_time)}")
            return
        latest_market_event[ticker] = {
            "timestamp": event_time,
            "event_id": tick.get("eventId"),
        }
        price_buffer[ticker].append(tick["bar"])
        process_prediction(ticker, ch, "market_tick")

    def on_news_sentiment(ch, method, properties, body):
        news = json.loads(body)
        ticker = news["ticker"]
        latest_news[ticker] = {
            "sentiment": 1 if news.get("label") == 1 else -1,
            "confidence": news.get("confidence", 0.5),
            # News Impact does not currently forward the original headline
            # timestamp. At replay time the last causal market event is the
            # authoritative clock; this is also the clock used for its
            # resulting news-triggered prediction.
            "timestamp": latest_market_event.get(ticker, {}).get("timestamp")
            or datetime.now(timezone.utc),
        }
        print(f" [*] Updated sentiment for {ticker}")
        process_prediction(ticker, ch, "news_sentiment")

    channel.basic_consume(
        queue=RABBITMQ_MARKET_TICKS_QUEUE,
        on_message_callback=on_market_tick,
        auto_ack=True,
    )
    channel.basic_consume(
        queue=RABBITMQ_NEWS_SENTIMENT_QUEUE,
        on_message_callback=on_news_sentiment,
        auto_ack=True,
    )

    print(
        " [*] Prediction Service listening on "
        f"'{RABBITMQ_MARKET_TICKS_QUEUE}' & '{RABBITMQ_NEWS_SENTIMENT_QUEUE}'. "
        "Press CTRL+C to exit."
    )
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        channel.stop_consuming()
        connection.close()

if __name__ == "__main__":
    main()
