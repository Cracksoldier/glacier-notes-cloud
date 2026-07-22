FROM node:24.15.0-alpine AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build:production

FROM maven:3.9.11-eclipse-temurin-21-alpine AS backend-build

WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn/ .mvn/
COPY openapi/ openapi/
COPY backend/ backend/
COPY --from=frontend-build /workspace/frontend/dist/frontend/browser/ \
  backend/src/main/resources/META-INF/resources/
RUN ./mvnw -pl backend package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S -g 10001 glacier \
    && adduser -S -D -H -u 10001 -G glacier glacier \
    && mkdir -p /opt/glacier /var/lib/glacier-notes/images /var/lib/glacier-notes/backups /var/lib/glacier-notes/transfers/uploads \
    && chown -R glacier:glacier /opt/glacier /var/lib/glacier-notes

WORKDIR /opt/glacier
COPY --from=backend-build --chown=glacier:glacier \
  /workspace/backend/target/quarkus-app/ ./
COPY --chown=glacier:glacier deployment/docker/entrypoint.sh /usr/local/bin/glacier-entrypoint
RUN chmod 0555 /usr/local/bin/glacier-entrypoint

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"
EXPOSE 8080 9000
VOLUME ["/var/lib/glacier-notes/images", "/var/lib/glacier-notes/backups", "/var/lib/glacier-notes/transfers"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD wget -qO- http://127.0.0.1:9000/q/health/ready >/dev/null || exit 1

USER 10001:10001
ENTRYPOINT ["/usr/local/bin/glacier-entrypoint"]
