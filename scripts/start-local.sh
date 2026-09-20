#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."

if [ ! -f "$ROOT_DIR/.env" ]; then
  echo "Copy .env.example to .env before starting local infrastructure."
  exit 1
fi

cd "$ROOT_DIR/infrastructure/compose"
docker compose --env-file "$ROOT_DIR/.env" up -d

echo "Local infrastructure started. Inspect health with:"
echo "  docker compose ps"
echo "  docker compose logs -f"
