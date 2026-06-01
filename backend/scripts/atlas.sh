#!/bin/bash

# Atlas schema management via Docker Compose
# Usage:
#   ./scripts/atlas.sh diff    — preview changes (schema.sql vs live DB)
#   ./scripts/atlas.sh apply   — apply changes to live DB
#   ./scripts/atlas.sh inspect — re-inspect live DB and overwrite schema.sql

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA_FILE="$SCRIPT_DIR/../db/schema.sql"
COMPOSE=(docker-compose -f "$SCRIPT_DIR/../../docker/docker-compose-dev.yaml" --profile migrate)
COMMAND=${1:-diff}

DB_URL="postgres://campalert:campalert@db:5432/campalert?sslmode=disable"
DEV_URL="postgres://dev:dev@atlas-dev-db:5432/dev?sslmode=disable"

case "$COMMAND" in
    diff)
        "${COMPOSE[@]}" run --rm migrate \
            schema diff \
            --from "$DB_URL" \
            --to "file:///db/schema.sql" \
            --dev-url "$DEV_URL"
        ;;
    apply)
        "${COMPOSE[@]}" run --rm migrate
        ;;
    inspect)
        "${COMPOSE[@]}" run --rm -T migrate \
            schema inspect \
            --url "$DB_URL" \
            --format '{{ sql . }}' \
            > "$SCHEMA_FILE"
        echo "Written to db/schema.sql"
        ;;
    *)
        echo "Usage: ./scripts/atlas.sh [diff|apply|inspect]"
        exit 1
        ;;
esac
