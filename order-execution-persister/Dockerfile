# syntax=docker/dockerfile:1.7
# This image is explicitly built for Linux x86_64 (amd64) Kubernetes nodes.
FROM --platform=linux/amd64 eclipse-temurin:21-jre

WORKDIR /order-execution-persister

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} order-execution-persister.jar

ENTRYPOINT ["java", "-jar", "order-execution-persister.jar"]
