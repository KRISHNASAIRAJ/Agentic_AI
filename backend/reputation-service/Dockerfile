FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
ARG JAR_FILE=target/reputation-service-1.0.0.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
