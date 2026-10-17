FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/pg-simulator.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
