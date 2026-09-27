FROM eclipse-temurin:21-jre

WORKDIR /direction-evaluation-service
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} direction-evaluation-service.jar
ENTRYPOINT ["java", "-jar", "direction-evaluation-service.jar"]
