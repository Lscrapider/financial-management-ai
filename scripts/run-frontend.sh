#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend-vue"

if [ -x "$FRONTEND_DIR/.tools/bin/pnpm" ]; then
  PNPM="$FRONTEND_DIR/.tools/bin/pnpm"
elif command -v pnpm >/dev/null 2>&1; then
  PNPM="pnpm"
else
  echo "pnpm is required. Install pnpm 11+ or run: corepack enable && corepack prepare pnpm@11.2.2 --activate" >&2
  exit 1
fi

cd "$FRONTEND_DIR"
exec "$PNPM" dev:ele --host "${FRONTEND_HOST:-0.0.0.0}"
