#!/bin/bash

# Atlas schema management via Docker
# Usage:
#   ./scripts/atlas.sh diff    — preview changes (schema.sql vs live DB)
#   ./scripts/atlas.sh apply   — apply changes to live DB (with confirmation)
#   ./scripts/atlas.sh inspect — re-inspect live DB and overwrite schema.sql

set -e

DB_URL="postgres://npsfinder:NBk4mZfZ7-ro7aLjK8@host.docker.internal:5432/npsfinder?sslmode=disable"
SCHEMA_FILE="$(cd "$(dirname "$0")/../db" && pwd)/schema.sql"
COMMAND=${1:-diff}

run_atlas() {
    docker network create atlas-net 2>/dev/null || true

    docker run --rm -d --name atlas-pg-dev --network atlas-net \
        -e POSTGRES_PASSWORD=dev -e POSTGRES_USER=dev -e POSTGRES_DB=dev \
        postgres:16-alpine >/dev/null 2>&1

    # Wait for dev postgres to be ready
    for i in $(seq 1 10); do
        docker exec atlas-pg-dev pg_isready -U dev >/dev/null 2>&1 && break
        sleep 1
    done

    docker run --rm --network atlas-net \
        -v "$(dirname "$SCHEMA_FILE"):/db" \
        arigaio/atlas "$@"

    docker stop atlas-pg-dev >/dev/null 2>&1 || true
    docker network rm atlas-net >/dev/null 2>&1 || true
}

case "$COMMAND" in
    diff)
        echo "Comparing schema.sql to live DB..."
        run_atlas schema diff \
            --from "$DB_URL" \
            --to "file:///db/schema.sql" \
            --dev-url "postgres://dev:dev@atlas-pg-dev:5432/dev?sslmode=disable"
        ;;
    apply)
        echo "Applying schema.sql to live DB..."
        run_atlas schema apply \
            --url "$DB_URL" \
            --to "file:///db/schema.sql" \
            --dev-url "postgres://dev:dev@atlas-pg-dev:5432/dev?sslmode=disable"
        ;;
    inspect)
        echo "Inspecting live DB and writing to db/schema.sql..."
        docker run --rm arigaio/atlas schema inspect \
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
