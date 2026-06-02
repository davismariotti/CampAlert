#!/bin/bash
# Usage: ./docker/deploy.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-prod.yaml"
ENV_FILE="$SCRIPT_DIR/.env.prod"
FRONTEND_WEB_ROOT="/var/www/campalert"

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: $ENV_FILE not found. Copy .env.prod.example and fill in values."
  exit 1
fi

echo "Running migrations..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate run --rm migrate
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile migrate down

echo "Rolling out app..."
docker rollout -f "$COMPOSE_FILE" --env-file "$ENV_FILE" app

echo "Deploying frontend..."
TMPDIR=$(mktemp -d)
gh release download --latest --pattern "frontend-dist.zip" --repo davismariotti/CampAlert --output "$TMPDIR/frontend-dist.zip"
unzip -o "$TMPDIR/frontend-dist.zip" -d "$TMPDIR/extracted"
mkdir -p "$FRONTEND_WEB_ROOT"
cp -r "$TMPDIR/extracted/frontend/dist/." "$FRONTEND_WEB_ROOT/"
rm -rf "$TMPDIR"
echo "Frontend deployed to $FRONTEND_WEB_ROOT"
