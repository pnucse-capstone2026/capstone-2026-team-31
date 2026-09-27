FROM eclipse-temurin:21-jre

WORKDIR /pnl-service
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} pnl-service.jar
ENTRYPOINT ["java", "-jar", "pnl-service.jar"]
