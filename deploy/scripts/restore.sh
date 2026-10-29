#!/bin/sh
set -eu
. "$(dirname "$0")/common.sh"
[ "$#" -eq 2 ] && [ "$2" = '--confirm-empty-target' ] || { echo '用法：restore.sh 备份目录 --confirm-empty-target（仅允许恢复到空数据库）' >&2; exit 1; }
SNAP=$(CDPATH= cd -- "$1" && pwd)
[ -f "$SNAP/COMPLETE" ] || { echo '备份未完整完成' >&2; exit 1; }
(cd "$SNAP" && sha256sum -c snapshot.sha256)
[ -z "$(dc ps --status running -q backend nginx)" ] || { echo '请先停止backend/nginx写入' >&2; exit 1; }
TABLES=$(dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -Nse "select count(*) from information_schema.tables where table_schema=DATABASE()" "$MYSQL_DATABASE"')
[ "$TABLES" = 0 ] || { echo '目标数据库非空，禁止覆盖；请使用新的Compose项目恢复' >&2; exit 1; }
dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < "$SNAP/database.sql"
image_command "$SNAP/images" import
PROJECT=$(project_name)
# Only regular UUID image names and directory entries from our generated archive are allowed.
"$PYTHON" -c 'import re,sys,tarfile; t=tarfile.open(sys.argv[1]); assert all((m.isdir() and m.name in (".","./")) or (m.isfile() and m.size<=5242880 and re.fullmatch(r"(?:\./)?[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}\.(?:jpg|jpeg|png|webp)",m.name)) for m in t.getmembers()), "旧图备份包含非法类型或路径"' "$(host_path "$SNAP/legacy-images.tgz")"
MSYS_NO_PATHCONV=1 docker run --rm --user 0 --entrypoint sh -v "$(host_path "$SNAP"):/transfer:ro" -v "${PROJECT}_legacy_images:/restore" "$(backend_image)" -c '[ -z "$(ls -A /restore)" ] || { echo "目标旧图片卷非空，禁止覆盖" >&2; exit 1; }; exec tar -C /restore --no-same-owner -xzf /transfer/legacy-images.tgz'
echo '数据及图片恢复完成；核对图片与历史订单后再启动backend/nginx。不会自动启用真实支付或消息。'
