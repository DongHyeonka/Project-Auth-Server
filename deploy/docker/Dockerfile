# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy@sha256:93916be89a15149f1d7e72f0fce69a468106ac918c2dc82f37f65d6819fa73e2 AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY bootstrap/build.gradle bootstrap/build.gradle
COPY application/build.gradle application/build.gradle
COPY domain/build.gradle domain/build.gradle
COPY infrastructure/build.gradle infrastructure/build.gradle
COPY presentation/build.gradle presentation/build.gradle

RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :bootstrap:dependencies > /dev/null

COPY bootstrap/src bootstrap/src
COPY application/src application/src
COPY domain/src domain/src
COPY infrastructure/src infrastructure/src
COPY presentation/src presentation/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :bootstrap:bootJar :bootstrap:migrationBootJar && \
    JAR_PATH="$(find /workspace/bootstrap/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' ! -name '*-migration.jar' | head -n 1)" && \
    test -n "$JAR_PATH" && \
    cp "$JAR_PATH" /workspace/application.jar && \
    MIGRATION_JAR_PATH="$(find /workspace/bootstrap/build/libs -maxdepth 1 -type f -name '*-migration.jar' | head -n 1)" && \
    test -n "$MIGRATION_JAR_PATH" && \
    cp "$MIGRATION_JAR_PATH" /workspace/migration.jar && \
    java -Djarmode=tools -jar /workspace/application.jar extract --layers --destination /workspace/extracted

FROM eclipse-temurin:21-jre-jammy@sha256:fcf98f8a669c2778b2a1a145c7dac92a1f8fc71e967734bd2a749d2f42572db1

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 10001 spring && \
    useradd --system --uid 10001 --gid 10001 --create-home --home-dir /app spring

WORKDIR /app

COPY --chown=spring:spring --from=builder /workspace/extracted/dependencies/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/application/ ./
COPY --chown=spring:spring --from=builder /workspace/migration.jar /app/migration.jar

EXPOSE 8080

USER 10001:10001

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
