# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only what Maven needs to resolve dependencies first, so this layer is cached until the POM changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), so they are skipped inside the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
    && mv target/space-booking-api-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination extracted

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/thiagoCardoso13/space-booking-api" \
      org.opencontainers.image.description="REST API to book meeting rooms, desks and sports courts" \
      org.opencontainers.image.licenses="MIT"
WORKDIR /app

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

# Dependencies change rarely, the application jar on every build: separate layers keep rebuilds and pulls small.
COPY --from=build --chown=app:app /workspace/extracted/lib/ lib/
COPY --from=build --chown=app:app /workspace/extracted/app.jar app.jar

USER app
EXPOSE 8080

# Secure by default: the prod profile refuses to start without JWT_SECRET and logs structured JSON.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health/liveness > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
