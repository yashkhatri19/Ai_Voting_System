# Maven build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first for caching layers
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/votingsystem-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

# Disable devtools when running in container production environment
ENV SPRING_DEVTOOLS_RESTART_ENABLED=false

ENTRYPOINT ["java", "-jar", "app.jar"]
