# syntax=docker/dockerfile:1
#
# Multi-stage build for realty-rest, a standalone read-only HTTP service over
# the Realty database. Configured entirely by environment variables (see
# realty-web/realty-rest/README.md) -- there is no config file to bake in or mount.

# ---- build stage --------------------------------------------------------
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

# Copy the whole multi-module project: realty-rest depends on realty-backend
# and realty-backend-api via project(...) references, so the Gradle build
# needs the full source tree, not just the realty-rest subdirectory.
COPY . .

RUN ./gradlew --no-daemon :realty-web:realty-rest:shadowJar

# ---- runtime stage -------------------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runtime

# The base image ships neither wget nor curl; install wget for the
# HEALTHCHECK below and drop apt lists immediately after.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system realty && useradd --system --gid realty --no-create-home realty

WORKDIR /app
COPY --from=build /workspace/realty-web/realty-rest/build/libs/*-all.jar app.jar
RUN chown realty:realty app.jar

USER realty

# Default port; overridden by the REALTY_REST_PORT env var an operator sets to
# run on a different port. EXPOSE is documentation only -- it does not bind the
# port -- but the HEALTHCHECK below reads the same variable, so it always probes
# whatever port the app is actually listening on.
ENV REALTY_REST_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD wget -qO- "http://127.0.0.1:${REALTY_REST_PORT}/v1/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
