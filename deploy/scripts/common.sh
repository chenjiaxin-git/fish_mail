#!/bin/sh
set -eu
umask 077
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE=${ENV_FILE:-$ROOT/.env}
[ -f "$ENV_FILE" ] || { echo '缺少部署.env文件' >&2; exit 1; }
PYTHON=${PYTHON:-python3}
host_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
dc() { MSYS_NO_PATHCONV=1 docker compose --env-file "$(host_path "$ENV_FILE")" -f "$(host_path "$ROOT/compose.yml")" "$@"; }
backend_image() { dc config --format json | "$PYTHON" -c 'import json,sys; print(json.load(sys.stdin)["services"]["backend"]["image"])'; }
project_name() { dc config --format json | "$PYTHON" -c 'import json,sys; print(json.load(sys.stdin)["name"])'; }
image_command() { dc run --rm --no-deps --user "$(id -u):$(id -g)" -v "$(host_path "$1"):/transfer" backend --spring.main.web-application-type=none --spring.flyway.enabled=false --notification.enabled=false --notification.worker-enabled=false --order.timeout-close-enabled=false "--delivery.command=$2" --delivery.directory=/transfer; }
