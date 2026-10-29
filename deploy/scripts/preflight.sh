#!/bin/sh
set -eu
. "$(dirname "$0")/common.sh"
command -v docker >/dev/null
command -v "$PYTHON" >/dev/null
docker compose version
dc config --quiet
CERT_SOURCE=$(dc config --format json | "$PYTHON" -c 'import json,sys; print(next(v["source"] for v in json.load(sys.stdin)["services"]["nginx"]["volumes"] if v["target"]=="/etc/nginx/certs"))')
[ -s "$CERT_SOURCE/fullchain.pem" ] && [ -s "$CERT_SOURCE/privkey.pem" ] || { echo '缺少HTTPS证书或私钥' >&2; exit 1; }
if [ -e "$ROOT/config/notifications.yml" ]; then echo '请核对真实订阅模板字段及发送开关'; fi
echo '配置语法预检通过；生产启动仍会校验密钥、微信登录及MinIO配置。'
