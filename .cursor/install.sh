#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

for cmd in git curl node; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Required command not found: $cmd" >&2
    exit 1
  fi
done

if [[ ! -f README.md ]]; then
  echo "Expected README.md at repository root" >&2
  exit 1
fi

node scripts/verify-workspace.mjs

echo "project-uno workspace bootstrap complete"
