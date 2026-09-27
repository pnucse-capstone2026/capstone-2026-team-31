# Fake News Ingestion Service

A lightweight Python replacement for `news-ingestion-service`. At each process
boot it declares the production RabbitMQ topology and publishes exactly one
durable JSON message:

```json
{"ticker":"AAPL","headline":"Apple reports stronger-than-expected quarterly earnings."}
```

The message is published to the direct exchange `financial-news-exchange` with
routing key `financial-news`; that binding targets the durable
`financial-news` queue. The process remains running afterwards, but it never
reconnects to publish again, so its total output is one message per process
lifecycle.

## Run

```bash
cd fake-news-ingestion-service
python3 -m pip install -r fake-news-ingestion-service-requirements.txt
python3 fake-news-ingestion-service.py
```

Build the image used by `fake-news-ingestion-service.yml` with:

```bash
docker buildx build --platform linux/amd64 --load \
  -f Dockerfile.fake-news-ingestion-service \
  -t luigisr03/fake-news-ingestion-service:test .
```

It accepts the same RabbitMQ environment variables and defaults as the Spring
service: `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`,
`SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`, and
`SPRING_RABBITMQ_VIRTUAL_HOST`. Before the first successful publication, it
retries RabbitMQ connection failures every `RABBITMQ_RETRY_DELAY_SECONDS`
(default `5`).
