#!/bin/sh
set -eu

if [ -n "${GLACIER_DATABASE_PASSWORD_FILE:-}" ]; then
  GLACIER_DATABASE_PASSWORD="$(sed -e 's/[[:space:]]*$//' "${GLACIER_DATABASE_PASSWORD_FILE}")"
  export GLACIER_DATABASE_PASSWORD
fi

if [ -n "${GLACIER_SMTP_PASSWORD_FILE:-}" ]; then
  GLACIER_SMTP_PASSWORD="$(sed -e 's/[[:space:]]*$//' "${GLACIER_SMTP_PASSWORD_FILE}")"
  export GLACIER_SMTP_PASSWORD
fi

exec java ${JAVA_OPTS:-} -jar quarkus-run.jar
