#!/bin/sh
set -eu
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mc mb --ignore-existing local/fishmall-images
mc anonymous set none local/fishmall-images
mc admin user add local "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"
mc admin policy create local fishmall-images /policy.json
mc admin policy attach local fishmall-images --user "$MINIO_ACCESS_KEY"
