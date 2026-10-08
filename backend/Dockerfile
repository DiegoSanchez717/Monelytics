FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:21-jre-jammy
# Refresh runtime packages before dropping privileges; image scans enforce available fixes.
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd --system wealthpath && useradd --system --gid wealthpath --home-dir /app wealthpath
WORKDIR /app
COPY --from=build --chown=wealthpath:wealthpath /build/target/wealthpath-api-1.0.0.jar app.jar
USER wealthpath
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
