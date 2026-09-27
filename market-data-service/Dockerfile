FROM eclipse-temurin:21-jre

WORKDIR /market-data-service

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} market-data-service.jar

ENTRYPOINT ["java", "-jar", "market-data-service.jar"]