#!/bin/zsh
set -e

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="${SCRIPT_DIR:h}"

if [[ -f "$PROJECT_ROOT/.env.local" ]]; then
  set -a
  source "$PROJECT_ROOT/.env.local"
  set +a
fi

cd "$SCRIPT_DIR"
exec mvn spring-boot:run
