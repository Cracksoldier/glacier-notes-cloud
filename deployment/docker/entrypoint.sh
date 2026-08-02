#!/bin/sh
set -eu

# Compose bind-mounts host secret files with their host ownership, and this container runs
# unprivileged as uid 10001. A file the operator can read is not necessarily one the container can,
# and the bare "sed: Permission denied" that follows says nothing about how to fix it.
require_readable() {
  [ -r "$1" ] && return 0
  echo "glacier: cannot read the secret file mounted at $1." >&2
  echo "glacier: this container runs as uid 10001, and Compose mounts host secrets with the" >&2
  echo "glacier: ownership they have on the host. Give the host file to that uid, for example" >&2
  echo "glacier: 'chown 10001:10001 <file>'. See deployment/README.md." >&2
  exit 1
}

if [ -n "${GLACIER_DATABASE_PASSWORD_FILE:-}" ]; then
  require_readable "${GLACIER_DATABASE_PASSWORD_FILE}"
  GLACIER_DATABASE_PASSWORD="$(sed -e 's/[[:space:]]*$//' "${GLACIER_DATABASE_PASSWORD_FILE}")"
  export GLACIER_DATABASE_PASSWORD
fi

if [ -n "${GLACIER_SMTP_PASSWORD_FILE:-}" ]; then
  require_readable "${GLACIER_SMTP_PASSWORD_FILE}"
  GLACIER_SMTP_PASSWORD="$(sed -e '$s/\r$//' "${GLACIER_SMTP_PASSWORD_FILE}")"
  export GLACIER_SMTP_PASSWORD
fi

exec java ${JAVA_OPTS:-} -jar quarkus-run.jar
