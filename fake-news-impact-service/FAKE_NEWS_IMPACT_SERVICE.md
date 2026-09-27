# Fake News Impact Service

fake-news-impact-service.py is a lightweight RabbitMQ-compatible replacement for AI/news_impact_service. It consumes the same input contract and publishes the same output contract, but replaces FinBERT inference with a random valid binary-class result.

## Message contracts

Input from financial-news-exchange / financial-news:

~~~json
{"ticker":"AAPL","headline":"Apple reported stronger than expected quarterly earnings."}
~~~

Output to news-sentiment-exchange / news-sentiment:

~~~json
{"ticker":"AAPL","label":1,"confidence":0.8134}
~~~

label is always 0 or 1; confidence is a four-decimal value from 0.5 through 1.0. Tickers are uppercased and trimmed, matching the real service. The random seed may be fixed with MOCK_RANDOM_SEED for repeatable test output.

## Run

~~~bash
python3 -m pip install -r fake-news-impact-service-requirements.txt
python3 fake-news-impact-service.py
~~~

The mock has the same RabbitMQ environment variables and defaults as the real service, including RABBITMQ_HOST, RABBITMQ_VHOST, input/output exchange, queue, routing-key, durability, and delivery-mode settings. It consumes with auto_ack=true; malformed input is logged and not requeued.

Do not run the mock and real service together against the same input queue: RabbitMQ will distribute messages between the two consumers. Use the mock as a replacement, or configure an isolated queue through the supported RABBITMQ environment variables.
