# Multi-stage build for optimized image size
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Force rebuild - updated 2025-08-14 for Phase 2 PostgreSQL support
# Copy everything at once to avoid caching issues
COPY . .

# Clean everything before building to ensure fresh build
RUN rm -rf build/ .gradle/ && \
    ./gradlew clean bootJar --no-daemon --no-build-cache -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Add non-root user
RUN addgroup -g 1000 spring && \
    adduser -D -s /bin/sh -u 1000 -G spring spring

WORKDIR /app

# Install required packages including PostgreSQL client for health checks
RUN apk add --no-cache \
    curl \
    tzdata \
    postgresql-client \
    && rm -rf /var/cache/apk/*

# Set timezone
ENV TZ=Asia/Seoul

# Copy jar from builder stage (use wildcard to match any jar name)
COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar

# Create directories for logs and temp files
RUN mkdir -p /app/logs /app/temp && \
    chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# JVM options for container environment with PostgreSQL support
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=postgresql,docker \
    -Duser.timezone=Asia/Seoul"

# Health check with database connectivity validation
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]