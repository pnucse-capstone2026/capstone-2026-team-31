# The mock uses only Python's standard library; no dependency installation is needed.
FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app

COPY --chown=app:app fake-alpaca-api.py /app/fake-alpaca-api.py

USER app

EXPOSE 8080

CMD ["python3", "/app/fake-alpaca-api.py", "--host", "0.0.0.0", "--port", "8080", "--require-auth"]
