IMAGE_NAME ?= news-ingestion-service
IMAGE_TAG ?= 1.0.2
AMD64_IMAGE_TAG ?= $(IMAGE_TAG)-amd64

.PHONY: image-amd64

# Produces a separate local Linux x86_64 image, e.g.
# news-ingestion-service:1.0.2-amd64.
image-amd64:
	docker buildx build --platform linux/amd64 --file Dockerfile.amd64 --tag $(IMAGE_NAME):$(AMD64_IMAGE_TAG) --load .
