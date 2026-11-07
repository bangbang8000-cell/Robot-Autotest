#!/usr/bin/env bash
# TC-v2.1-32 迁移往返专项：up → down → up 各一次 + 备份恢复。
#
# 判据（docs/release/v2.1-platform/TEST-PLAN.md §4.7 / §5.4）：
#   1) V9_1..V9_4 逐个 down 后，对应表/列/索引确实消失，且 V8_x 数据行数不变；
#   2) 重新 up（Flyway 重放到 9.4）成功，表清单与首次 up 后逐行一致；
#   3) 备份恢复后行数与备份时一致，且 db-audit 7 项 PASS。
#
# 用独立 scratch 库（默认 robot_it_local，robot 用户已授权且当前为空），不碰 robot_it / robot_dev：
#   - 从空库真跑 V1..V9_4（这就是「up」），不依赖既有库的历史状态；
#   - 脚本结束默认 DROP 掉 scratch 库（KEEP_DB=1 可保留排查）。
#
# 用法：
#   bash scripts/v21/migrate-roundtrip.sh
#   KEEP_DB=1 TARGET_DB=robot_it_local bash scripts/v21/migrate-roundtrip.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 未导出 DB_* 时，回落读本机环境文件（与 scripts/db-common.sh 同一口径，不把口令写进仓库）
if [[ -z "${DB_PASSWORD:-}" && -f "$HOME/.config/robot-autotest/env" ]]; then
    set -a
    # shellcheck disable=SC1090
    . "$HOME/.config/robot-autotest/env"
    set +a
fi

source "$ROOT/scripts/db-common.sh"

TARGET_DB="${TARGET_DB:-robot_it_local}"
EVID="$ROOT/docs/evidence/v2.1/TC32-MIG"
LOG="$EVID/32-up-down-up.log"
mkdir -p "$EVID"
# 客户端会因为 CLI 传 --password 打一行 Warning，它不是错误；从证据日志里滤掉以免淹没有效行
exec > >(sed -u '/Using a password on the command line interface can be insecure/d' | tee "$LOG") 2>&1

export DB_NAME="$TARGET_DB"

ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*"; }

fail=0
ok() { echo "  OK    $*"; }
bad() { echo "  FAIL  $*"; fail=$((fail + 1)); }
expect_eq() { # 标题 期望 实测
    if [[ "$3" == "$2" ]]; then ok "$1（$3）"; else bad "$1（期望 $2，实测 $3）"; fi
}

q() { db_exec "$DB_NAME" -e "$1" | tr -d '[:space:]'; }
row_count() { q "SELECT COUNT(*) FROM \`$DB_NAME\`.\`$1\`"; }
table_count() {
    q "SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema='$DB_NAME' AND table_name<>'flyway_schema_history'"
}
has_table() {
    q "SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema='$DB_NAME' AND table_name='$1'"
}
has_column() {
    q "SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema='$DB_NAME' AND table_name='$1' AND column_name='$2'"
}
has_index() {
    q "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
       WHERE table_schema='$DB_NAME' AND table_name='$1' AND index_name='$2'"
}
column_nullable() {
    q "SELECT is_nullable FROM information_schema.columns
       WHERE table_schema='$DB_NAME' AND table_name='$1' AND column_name='$2'"
}
flyway_max() {
    q "SELECT version FROM flyway_schema_history
       WHERE success=1 ORDER BY installed_rank DESC LIMIT 1"
}
table_list() {
    db_exec -e "SELECT table_name FROM information_schema.tables
                WHERE table_schema='$DB_NAME' AND table_name<>'flyway_schema_history'
                ORDER BY table_name"
}

migrate_to() {
    local target="$1"
    say "mvn flyway:migrate -Dflyway.target=$target（DB_NAME=$DB_NAME）"
    ( cd "$ROOT/backend" && mvn -B -o -q flyway:migrate -Dflyway.target="$target" )
    say "flyway migrate 退出码 0；history max=$(flyway_max)；业务表=$(table_count)"
}

down_one() { # 版本 文件名
    local ver="$1" file="$2"
    say "down $ver：执行 db/down/$file"
    db_exec "$DB_NAME" < "$ROOT/db/down/$file"
    # 手写 down 不维护 flyway_schema_history，必须显式删该版本记录，否则重放 up 会报 already applied
    db_exec "$DB_NAME" -e "DELETE FROM flyway_schema_history WHERE version='$ver'"
    say "down $ver 完成；history max=$(flyway_max)；业务表=$(table_count)"
}

dump_db() { # 输出文件
    local out="$1"
    if command -v mariadb-dump >/dev/null 2>&1; then
        mariadb-dump --no-tablespaces --single-transaction --skip-comments \
            --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
            "$DB_NAME" > "$out"
    else
        mysqldump --no-tablespaces --single-transaction --skip-comments \
            --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
            "$DB_NAME" > "$out"
    fi
    echo "  备份 $(basename "$out")：$(wc -c < "$out") 字节"
}

# ---------------------------------------------------------------- 0. 前置检查
say "=== TC-v2.1-32 迁移往返开始 ==="
say "库：$DB_USERNAME@$DB_HOST:$DB_PORT/$DB_NAME；证据目录：$EVID"

case "$DB_NAME" in
    robot_it|robot_dev|mysql|sys)
        echo "[TC32] 拒绝执行：$DB_NAME 是真实业务/系统库，本脚本只允许 scratch 库" >&2
        exit 1
        ;;
esac
if [[ "$DB_NAME" != *_it* ]]; then
    echo "[TC32] 拒绝执行：DB_NAME=$DB_NAME 不是集成测试库（名字须含 _it，与 db-reset/db-undo 安全阀口径对齐）" >&2
    exit 1
fi

for tool in mvn; do
    command -v "$tool" >/dev/null 2>&1 || { echo "[TC32] 缺少命令：$tool" >&2; exit 1; }
done

# ---------------------------------------------------------------- 1. 首次 up
say "--- 1. 首次 up：空库 → V9_4 ---"
db_exec -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;
            CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
migrate_to 9.4

expect_eq "V9_4 业务表数" 66 "$(table_count)"
expect_eq "flyway history 最大版本" 9.4 "$(flyway_max)"
expect_eq "execution_node 已建" 1 "$(has_table execution_node)"
expect_eq "run.node_id 已加" 1 "$(has_column run node_id)"
expect_eq "report_template 已建" 1 "$(has_table report_template)"
expect_eq "asset_version 已建" 1 "$(has_table asset_version)"
expect_eq "plan_group 已建" 1 "$(has_table plan_group)"

# ---------------------------------------------------------------- 2. 夹具数据
say "--- 2. 写入夹具数据（V1 项目 + V8_1 报表 + V9_1 计划组）---"
db_exec "$DB_NAME" -e "
INSERT INTO project (id, code, name, status, visibility)
  VALUES (9001, 'TC32-ROUNDTRIP', 'TC32 往返夹具', 1, 'private');
INSERT INTO test_report (project_id, run_id, title, state)
  VALUES (9001, 'TC32-RUN-1', 'TC32 报表 1', 'DRAFT'),
         (9001, 'TC32-RUN-2', 'TC32 报表 2', 'FROZEN');
INSERT INTO plan_group (project_id, name, description)
  VALUES (9001, 'TC32 计划组', '往返夹具，down V9_1 后应随之消失');"
BASELINE_PROJECT=$(row_count project)
BASELINE_REPORT=$(row_count test_report)
BASELINE_GROUP=$(row_count plan_group)
expect_eq "夹具 project 行数" 1 "$BASELINE_PROJECT"
expect_eq "夹具 test_report 行数（V8_1 数据）" 2 "$BASELINE_REPORT"
expect_eq "夹具 plan_group 行数" 1 "$BASELINE_GROUP"

table_list > "$EVID/32-tables-after-up.txt"
say "首次 up 后表清单快照：32-tables-after-up.txt（$(wc -l < "$EVID/32-tables-after-up.txt") 行）"

# ---------------------------------------------------------------- 3. 备份（up 后状态）
say "--- 3. 备份（up 后状态，供恢复比对）---"
BACKUP_UP="$EVID/32-backup-after-up.sql"
dump_db "$BACKUP_UP"
restore_baseline() {
    expect_eq "备份时 project 行数" "$BASELINE_PROJECT" "$(row_count project)"
    expect_eq "备份时 test_report 行数" "$BASELINE_REPORT" "$(row_count test_report)"
    expect_eq "备份时 plan_group 行数" "$BASELINE_GROUP" "$(row_count plan_group)"
    expect_eq "备份时业务表数" 66 "$(table_count)"
}

# ---------------------------------------------------------------- 4. 逐个 down
say "--- 4. down：V9_4 → V9_3 → V9_2 → V9_1 ---"

say "-- 4.1 down V9_4（execution_node）--"
down_one 9.4 V9_4__execution_node.down.sql
expect_eq "down 后 execution_node 表消失" 0 "$(has_table execution_node)"
expect_eq "down 后 run.node_id 列消失" 0 "$(has_column run node_id)"
expect_eq "down 后 idx_run_node 索引消失" 0 "$(has_index run idx_run_node)"
expect_eq "down V9_4 后业务表数" 65 "$(table_count)"
expect_eq "down V9_4 后 test_report 行数不变" "$BASELINE_REPORT" "$(row_count test_report)"
expect_eq "down V9_4 后 project 行数不变" "$BASELINE_PROJECT" "$(row_count project)"

say "-- 4.2 down V9_3（case_source_version）--"
down_one 9.3 V9_3__case_source_version.down.sql
expect_eq "down 后 asset_version 表消失" 0 "$(has_table asset_version)"
expect_eq "down 后 test_set.owner_scope 消失" 0 "$(has_column test_set owner_scope)"
expect_eq "down 后 test_set.source_asset_id 消失" 0 "$(has_column test_set source_asset_id)"
expect_eq "down 后 test_set.scope_project_id 消失" 0 "$(has_column test_set scope_project_id)"
expect_eq "down 后 uk_test_set_scope_code 索引消失" 0 "$(has_index test_set uk_test_set_scope_code)"
expect_eq "down 后 idx_test_set_source 索引消失" 0 "$(has_index test_set idx_test_set_source)"
expect_eq "down 后 test_set.project_id 恢复 NOT NULL" NO "$(column_nullable test_set project_id)"
expect_eq "down 后 uk_test_set_project_code 索引恢复" 1 "$(has_index test_set uk_test_set_project_code)"
expect_eq "down V9_3 后业务表数" 64 "$(table_count)"
expect_eq "down V9_3 后 test_report 行数不变" "$BASELINE_REPORT" "$(row_count test_report)"
expect_eq "down V9_3 后 project 行数不变" "$BASELINE_PROJECT" "$(row_count project)"

say "-- 4.3 down V9_2（report_template）--"
down_one 9.2 V9_2__report_template.down.sql
expect_eq "down 后 report_template 表消失" 0 "$(has_table report_template)"
expect_eq "down 后 report_template_field 表消失" 0 "$(has_table report_template_field)"
expect_eq "down V9_2 后业务表数" 62 "$(table_count)"
expect_eq "down V9_2 后 test_report 行数不变" "$BASELINE_REPORT" "$(row_count test_report)"
expect_eq "down V9_2 后 project 行数不变" "$BASELINE_PROJECT" "$(row_count project)"

say "-- 4.4 down V9_1（plan_group）--"
down_one 9.1 V9_1__plan_group.down.sql
expect_eq "down 后 plan_group 表消失" 0 "$(has_table plan_group)"
expect_eq "down 后 plan_group_item 表消失" 0 "$(has_table plan_group_item)"
expect_eq "down V9_1 后业务表数（回到 V8_6）" 60 "$(table_count)"
expect_eq "down 全部完成后 flyway history 最大版本" 8.6 "$(flyway_max)"
expect_eq "down 全部完成后 test_report 行数不变" "$BASELINE_REPORT" "$(row_count test_report)"
expect_eq "down 全部完成后 project 行数不变" "$BASELINE_PROJECT" "$(row_count project)"

# ---------------------------------------------------------------- 5. 再次 up
say "--- 5. 再次 up：V8_6 → V9_4 ---"
migrate_to 9.4
expect_eq "重放后业务表数" 66 "$(table_count)"
expect_eq "重放后 flyway history 最大版本" 9.4 "$(flyway_max)"
expect_eq "重放后 execution_node 表恢复" 1 "$(has_table execution_node)"
expect_eq "重放后 run.node_id 列恢复" 1 "$(has_column run node_id)"
expect_eq "重放后 report_template 表恢复" 1 "$(has_table report_template)"
expect_eq "重放后 asset_version 表恢复" 1 "$(has_table asset_version)"
expect_eq "重放后 plan_group 表恢复" 1 "$(has_table plan_group)"
expect_eq "重放后 test_report 行数不变" "$BASELINE_REPORT" "$(row_count test_report)"
expect_eq "重放后 project 行数不变" "$BASELINE_PROJECT" "$(row_count project)"

table_list > "$EVID/32-tables-after-reup.txt"
if diff -u "$EVID/32-tables-after-up.txt" "$EVID/32-tables-after-reup.txt" > "$EVID/32-tables-diff.log"; then
    ok "重放后表清单与首次 up 逐行一致（$(wc -l < "$EVID/32-tables-after-up.txt") 行）"
    echo "  （diff 空输出，见 32-tables-diff.log）"
else
    bad "重放后表清单与首次 up 不一致，见 32-tables-diff.log"
fi

# ---------------------------------------------------------------- 6. 备份恢复
say "--- 6. 备份恢复：DROP + CREATE + 导入 32-backup-after-up.sql ---"
db_exec -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;
            CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
db_exec "$DB_NAME" < "$BACKUP_UP"
say "导入完成；业务表=$(table_count)；history max=$(flyway_max)"
restore_baseline
table_list > "$EVID/32-tables-after-restore.txt"
if diff -u "$EVID/32-tables-after-up.txt" "$EVID/32-tables-after-restore.txt" > /dev/null; then
    ok "恢复后表清单与备份时一致"
else
    bad "恢复后表清单与备份时不一致（见 32-tables-after-restore.txt）"
fi

say "--- 7. 恢复后 db-audit（7 项）---"
if DB_NAME="$DB_NAME" bash "$ROOT/scripts/db-audit.sh"; then
    ok "db-audit PASS（7 项）"
else
    bad "db-audit 未通过"
fi

# ---------------------------------------------------------------- 8. 收尾
say "--- 8. 收尾 ---"
if [[ "${KEEP_DB:-}" == "1" ]]; then
    say "KEEP_DB=1，保留 scratch 库 $DB_NAME 供排查"
else
    db_exec -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;"
    say "已 DROP scratch 库 $DB_NAME（未触碰 robot_it / robot_dev）"
fi

echo "--------------------------------------------------------------"
if [[ $fail -gt 0 ]]; then
    echo "TC-v2.1-32: FAIL（$fail 项不符）"
    exit 1
fi
echo "TC-v2.1-32: PASS（up → down → up + 备份恢复全部符合判据）"
