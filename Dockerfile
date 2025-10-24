# ---- Build Stage ----
FROM gradle:8.14-jdk17 AS builder
LABEL authors="lonwulf"

WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create a persistent data directory
RUN mkdir -p /app/data
VOLUME ["/app/data"]

# Entrypoint
CMD ["java", "-jar", "app.jar"]
