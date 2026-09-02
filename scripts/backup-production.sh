#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
ENV_FILE="$ROOT_DIR/.env.production"
BACKUP_ROOT="${BACKUP_ROOT:-$ROOT_DIR/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="$BACKUP_ROOT/$TIMESTAMP"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR/minio"
chmod 700 "$BACKUP_DIR"

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

"${compose[@]}" exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "$BACKUP_DIR/postgres.dump"

"${compose[@]}" run --rm --no-deps \
  -v "$BACKUP_DIR/minio:/backup" \
  --entrypoint /bin/sh createbuckets -c '
    set -eu
    mc alias set storage http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
    mc mirror --overwrite "storage/$APP_STORAGE_BUCKET" /backup
  '

docker run --rm \
  -v interview-guide_app_config:/source:ro \
  -v "$BACKUP_DIR:/backup" \
  alpine:3.21 tar -czf /backup/app-config.tar.gz -C /source .

cp "$ENV_FILE" "$BACKUP_DIR/env.production"
chmod 600 "$BACKUP_DIR/env.production"

echo "Backup created at $BACKUP_DIR"
