FROM python:3.11-slim

WORKDIR /news-impact-service

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

ARG MODEL_NAME=andreamena/finbert-stock-news-impact-v10
ENV MODEL_NAME=${MODEL_NAME} \
    MODEL_PATH=/opt/models/finbert \
    MODEL_LOCAL_FILES_ONLY=true

# Bake the tokenizer and fine-tuned model into the image so container startup
# does not need network access to Hugging Face.
RUN python -c "import os; from transformers import AutoModelForSequenceClassification, AutoTokenizer; model_name = os.environ['MODEL_NAME']; model_path = os.environ['MODEL_PATH']; AutoTokenizer.from_pretrained(model_name).save_pretrained(model_path); AutoModelForSequenceClassification.from_pretrained(model_name).save_pretrained(model_path)"

COPY . .

EXPOSE 8000

ENTRYPOINT ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
