#!/bin/bash
# Usage: ./docker/deploy.sh [--allow-unsafe]
#   --allow-unsafe  Allow schema changes that drop tables or columns (data loss)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-prod.yaml"
ENV_FILE="$SCRIPT_DIR/.env.prod"
FRONTEND_WEB_ROOT="/var/www/campalert"
ALLOW_UNSAFE=false

for arg in "$@"; do
  case $arg in
    --allow-unsafe) ALLOW_UNSAFE=true ;;
  esac
done

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: $ENV_FILE not found. Copy .env.prod.example and fill in values."
  exit 1
fi

# Load env vars into shell so they're available for command overrides below.
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

echo "Running migrations..."
if [ "$ALLOW_UNSAFE" = "true" ]; then
  echo "WARNING: --allow-unsafe passed; destructive schema changes will not be blocked."
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate run --rm migrate \
    schema apply \
    --url "postgres://${DB_USERNAME}:${DB_PASSWORD}@localhost:5432/campalert?sslmode=disable" \
    --to "file:///db/schema.sql" \
    --dev-url "postgres://dev:dev@localhost:5433/dev?sslmode=disable" \
    --config /db/atlas.hcl \
    --auto-approve
else
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate run --rm migrate
fi
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate rm --stop --force atlas-dev-db

echo "Starting infra services..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

echo "Rolling out app..."
docker rollout -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile app app

echo "Deploying frontend..."
TMPDIR=$(mktemp -d)
LATEST_TAG=$(gh release view --repo davismariotti/CampAlert --json tagName --jq .tagName)
gh release download "$LATEST_TAG" --pattern "frontend-dist.zip" --repo davismariotti/CampAlert --output "$TMPDIR/frontend-dist.zip"
unzip -o "$TMPDIR/frontend-dist.zip" -d "$TMPDIR/extracted"
mkdir -p "$FRONTEND_WEB_ROOT"
cp -r "$TMPDIR/extracted/frontend/dist/." "$FRONTEND_WEB_ROOT/"
rm -rf "$TMPDIR"
echo "Frontend deployed to $FRONTEND_WEB_ROOT"
