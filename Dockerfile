# syntax=docker/dockerfile:1

# ---- Build stage ---------------------------------------------------------------
FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Runtime stage -------------------------------------------------------------
FROM eclipse-temurin:17-jre

# bash runs shell.exec commands; procps provides kill, ps, top -b, free, ...
# tini runs as PID 1 and reaps orphaned processes (e.g. killed background jobs).
RUN apt-get update \
    && apt-get install -y --no-install-recommends bash procps tini \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 shell \
    && useradd --system --uid 10001 --gid shell --create-home --home-dir /home/shell --shell /bin/bash shell \
    && mkdir -p /app /data \
    && chown shell:shell /data

COPY --from=build /src/target/shell-server.jar /app/shell-server.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
# Tolerate CRLF line endings from a Windows checkout.
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod 0755 /app/entrypoint.sh

USER shell
# The accounts file lives in /data/users.properties (the default --users path, relative to here).
WORKDIR /data
VOLUME ["/data"]
EXPOSE 2704

ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]
CMD ["serve"]
