#!/bin/sh
set -eu
. "$(dirname "$0")/common.sh"
# Stop writes before migration; never delete source images.
[ -z "$(dc ps --status running -q backend nginx)" ] || { echo '请先停止backend/nginx写入' >&2; exit 1; }
dc run --rm --no-deps backend --spring.main.web-application-type=none --spring.flyway.enabled=false --notification.enabled=false --notification.worker-enabled=false --order.timeout-close-enabled=false --delivery.command=migrate --delivery.directory=/data/legacy-images
