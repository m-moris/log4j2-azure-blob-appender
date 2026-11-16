#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

export AZURITE_BLOB_PORT="${AZURITE_BLOB_PORT:-0}"
project="log4j2-azurite-$(date +%s)-$$"
compose=(docker compose --project-name "$project" --file compose.yaml)

cleanup() {
    status=$?
    trap - EXIT
    if (( status != 0 )); then
        "${compose[@]}" logs --no-color azurite >&2 || status=1
    fi
    if ! "${compose[@]}" down --timeout 5; then
        status=1
    fi
    exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"${compose[@]}" up --detach --wait --wait-timeout 60 azurite
address=$("${compose[@]}" port azurite 10000)
port=${address##*:}

mvn --batch-mode --no-transfer-progress -Pazurite "-Dazurite.port=$port" verify "$@"
