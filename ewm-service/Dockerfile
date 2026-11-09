FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ewm-service-0.0.1-SNAPSHOT.jar ewm-service.jar
ENTRYPOINT ["java", "-jar", "/app/ewm-service.jar"]