# syntax=docker/dockerfile:1.6
#
# Multi-stage build for the adjudis HTTP server.
#
# Build:
#   docker build -t adjudis-core:0.1.0 -f 06-adjudis-core/Dockerfile 06-adjudis-core
#
# Run:
#   docker run --rm -p 8080:8080 adjudis-core:0.1.0
#   curl http://localhost:8080/health

# ── build stage ──────────────────────────────────────────────────────────
FROM clojure:temurin-21-tools-deps-alpine AS build

WORKDIR /work
COPY deps.edn build.clj ./
# Pre-resolve deps to a separate layer so source changes don't redownload.
RUN clojure -P -T:build
COPY src        ./src
COPY resources  ./resources
RUN clojure -T:build uber

# ── runtime stage ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user — never run a service as root in a container.
RUN addgroup -S adjudis && adduser -S -G adjudis adjudis

WORKDIR /app
COPY --from=build /work/target/adjudis-core-0.1.0-standalone.jar /app/adjudis-core.jar

# Healthcheck against the /health endpoint. wget is in the alpine base.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget --quiet --spider http://localhost:${PORT:-8080}/health || exit 1

USER adjudis
ENV PORT=8080
EXPOSE 8080

# Non-AOT uberjar: invoke clojure.main as the entry point and tell it to
# run the adjudis.server namespace's -main. Adds ~2s of startup vs an AOT
# build; build time goes from 10+ minutes (Clara generates ~thousands of
# inner classes) to under a minute.
ENTRYPOINT ["java", "-jar", "/app/adjudis-core.jar", "-m", "adjudis.server"]
