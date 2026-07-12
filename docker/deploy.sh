#!/bin/bash
# Usage: ./docker/deploy.sh [--allow-unsafe] [--migrate-only] [--frontend-only]
#   --allow-unsafe    Allow schema changes that drop tables or columns (data loss)
#   --migrate-only    Run migrations only; skip app and frontend deployment
#   --frontend-only   Deploy frontend only; skip migrations and app deployment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-prod.yaml"
ENV_FILE="$SCRIPT_DIR/.env.prod"
FRONTEND_WEB_ROOT="/var/www/campalert"
ALLOW_UNSAFE=false
MIGRATE_ONLY=false
FRONTEND_ONLY=false

for arg in "$@"; do
  case $arg in
    --allow-unsafe)   ALLOW_UNSAFE=true ;;
    --migrate-only|--migrations-only) MIGRATE_ONLY=true ;;
    --frontend-only)  FRONTEND_ONLY=true ;;
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

if [ "$FRONTEND_ONLY" = "false" ]; then
  echo "Pulling latest migrate images..."
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate pull

  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate rm --stop --force atlas-dev-db 2>/dev/null || true

  echo "Running migrations..."
  if [ "$ALLOW_UNSAFE" = "true" ]; then
    echo "WARNING: --allow-unsafe passed; destructive schema changes will not be blocked."
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate run --rm migrate \
      schema apply \
      --url "postgres://${DB_USERNAME}:${DB_PASSWORD}@localhost:5432/campalert?sslmode=disable" \
      --to "file:///db/schema.sql" \
      --dev-url "postgres://dev:dev@localhost:5433/dev?sslmode=disable" \
      --config file:///db/atlas.hcl \
      --auto-approve
  else
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate run --rm migrate
  fi
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate rm --stop --force atlas-dev-db

  if [ "$MIGRATE_ONLY" = "true" ]; then
    echo "Migrations complete. Skipping app and frontend deployment."
    exit 0
  fi

  echo "Pulling latest images..."
  APP_IMAGE=$(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile app config --images app)
  OLD_APP_IMAGE_ID=$(docker image inspect "$APP_IMAGE" --format '{{.Id}}' 2>/dev/null || true)
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile app pull

  echo "Starting infra services..."
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

  echo "Rolling out app..."
  docker rollout -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile app --timeout 300 app

  # $APP_IMAGE (e.g. davismariotti/campalert:latest) is a moving tag: every pull re-points it at a new
  # digest, leaving the previous digest dangling. Remove only that specific superseded image rather than
  # a host-wide `docker image prune`, which would also sweep up dangling images from unrelated stacks
  # on this box (SurvivalAPI, etc).
  NEW_APP_IMAGE_ID=$(docker image inspect "$APP_IMAGE" --format '{{.Id}}' 2>/dev/null || true)
  if [ -n "$OLD_APP_IMAGE_ID" ] && [ "$OLD_APP_IMAGE_ID" != "$NEW_APP_IMAGE_ID" ]; then
    echo "Removing superseded app image $OLD_APP_IMAGE_ID..."
    docker rmi "$OLD_APP_IMAGE_ID" || true
  fi
fi

echo "Deploying frontend..."
TMPDIR=$(mktemp -d)
LATEST_TAG=$(gh release view --repo davismariotti/CampAlert --json tagName --jq .tagName)
gh release download "$LATEST_TAG" --pattern "frontend-dist.zip" --repo davismariotti/CampAlert --output "$TMPDIR/frontend-dist.zip"
unzip -o "$TMPDIR/frontend-dist.zip" -d "$TMPDIR/extracted"
mkdir -p "$FRONTEND_WEB_ROOT"
cp -r "$TMPDIR/extracted/frontend/dist/." "$FRONTEND_WEB_ROOT/"
rm -rf "$TMPDIR"
echo "Frontend deployed to $FRONTEND_WEB_ROOT"
