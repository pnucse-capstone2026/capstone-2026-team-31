FROM eclipse-temurin:21-jre

WORKDIR /news-ingestion-service

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} news-ingestion-service.jar

ENTRYPOINT ["java", "-jar", "news-ingestion-service.jar"]