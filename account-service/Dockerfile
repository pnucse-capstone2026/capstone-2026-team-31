FROM eclipse-temurin:21-jre

WORKDIR /account-service

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} account-service.jar

ENTRYPOINT ["java", "-jar", "account-service.jar"]
