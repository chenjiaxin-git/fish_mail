#!/bin/sh
set -eu
. "$(dirname "$0")/common.sh"
BASE=${BACKUP_DIR:-$ROOT/backups}
mkdir -p "$BASE"
BASE=$(CDPATH= cd -- "$BASE" && pwd)
SNAP="$BASE/fishmall-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir "$SNAP" "$SNAP/images"
[ "$(dc ps --status running -q backend | wc -l)" -eq 1 ] && [ "$(dc ps --status running -q nginx | wc -l)" -eq 1 ] || { echo '备份前backend/nginx必须正在运行' >&2; exit 1; }
trap 'dc start backend nginx >/dev/null' EXIT
trap 'exit 1' HUP INT TERM
dc stop nginx backend
# Snapshot is taken while all application writes are stopped.
dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > "$SNAP/database.sql"
[ -s "$SNAP/database.sql" ]
image_command "$SNAP/images" export
dc run --rm --no-deps --entrypoint tar -v "$(host_path "$SNAP"):/transfer" backend -C /data/legacy-images -czf /transfer/legacy-images.tgz .
cp "$ENV_FILE" "$SNAP/deployment.env"
cp "$ROOT/compose.yml" "$ROOT/nginx.conf" "$SNAP/"
# Restricted archives: keep encrypted outside this host; never publish them with the code.
dc run --rm --no-deps --user 0 --entrypoint tar -v "$(host_path "$SNAP"):/transfer" backend -C /run/keys -czf /transfer/payment-keys.tgz .
dc run --rm --no-deps --user 0 --entrypoint tar -v "$(host_path "$SNAP"):/transfer" backend -C /config -czf /transfer/application-config.tgz .
dc run --rm --no-deps --entrypoint tar -v "$(host_path "$SNAP"):/transfer" nginx -C /etc/nginx/certs -czf /transfer/tls-certs.tgz .
if [ -f "$ROOT/config/notifications.yml" ]; then cp "$ROOT/config/notifications.yml" "$SNAP/"; fi
(cd "$SNAP" && sha256sum database.sql legacy-images.tgz payment-keys.tgz application-config.tgz tls-certs.tgz deployment.env compose.yml nginx.conf > snapshot.sha256)
printf '%s\n' 'complete' > "$SNAP/COMPLETE"
echo "备份成功：$SNAP；含受限配置，须加密后存放到独立介质。"
# Only this script's completed snapshots under the resolved backup directory are eligible.
find "$BASE" -mindepth 1 -maxdepth 1 -type d -name 'fishmall-????????T??????Z' -mtime +7 -exec sh -c 'for d do [ ! -f "$d/COMPLETE" ] || rm -rf -- "$d"; done' sh {} +
