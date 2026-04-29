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
    ./gradlew --no-daemon \
        :bootstrap:dependencies --configuration runtimeClasspath \
        :bootstrap:dependencies --configuration compileClasspath \
        -q

COPY bootstrap/src/main bootstrap/src/main
COPY application/src/main application/src/main
COPY domain/src/main domain/src/main
COPY infrastructure/src/main infrastructure/src/main
COPY presentation/src/main presentation/src/main

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :bootstrap:bootJar -x test && \
    JAR_PATH="$(find /workspace/bootstrap/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" && \
    test -n "$JAR_PATH" && \
    cp "$JAR_PATH" /workspace/application.jar && \
    java -Djarmode=tools -jar /workspace/application.jar extract --layers --destination /workspace/extracted

FROM eclipse-temurin:21-jre-jammy@sha256:fcf98f8a669c2778b2a1a145c7dac92a1f8fc71e967734bd2a749d2f42572db1

ARG GIT_SHA="unknown"
ARG BUILD_DATE="unknown"
ARG VERSION="dev"

LABEL org.opencontainers.image.source="https://github.com/donghyeon-ka/Project-Auth-Server" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.title="project-auth-server" \
      org.opencontainers.image.description="OAuth2 resource server (Keycloak-mediated federation)" \
      org.opencontainers.image.licenses="Apache-2.0"

RUN groupadd --system --gid 10001 spring && \
    useradd --system --uid 10001 --gid 10001 --create-home --home-dir /app spring

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="\
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/tmp/heapdump.hprof \
-XX:+UseG1GC \
-Djava.security.egd=file:/dev/./urandom \
-Duser.timezone=UTC"

COPY --chown=spring:spring --from=builder /workspace/extracted/dependencies/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --chown=spring:spring --from=builder /workspace/extracted/application/ ./
COPY --chown=spring:spring --from=builder /workspace/application.jar /app/application.jar

EXPOSE 8080
EXPOSE 8081

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
