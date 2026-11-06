#!/usr/bin/env bash
# HRD-04：三步回滚（给 149 Linux 用）
#
#   ① 备份当前状态（DB dump + 产物清单 + 当前 jar/dist）
#   ② 停服 → 部署上一版（jar + dist）→ 可选执行 db/down 迁移
#   ③ 起服 → 健康检查 → 打印抽样验证清单
#
# 安全默认：--schema=keep（只回退代码，不回退 schema；加列对旧代码无害）。
# 真实执行必须显式 --yes；不带 --yes 时只打印将执行的命令并退出 0（等同干跑）。
# --schema=down 会删表/删列，除 --yes 外还要二次确认（TTY 里输入 DOWN，非 TTY 用 ROLLBACK_DOWN_CONFIRM=1）。
#
# 口令只从环境变量读：DB_PASSWORD（库）、必要时 DB_HOST/DB_PORT/DB_NAME/DB_USERNAME。
# 本脚本不写死任何口令，也不执行 DROP/DELETE 以外的数据清理动作。
# C9（2026-11-06 实测订正）：备份走 mysqldump 全库，新增表自动纳入，
# 不存在「逐表登记清单」；W2 起每波在证据 README 登记新表供人工核对。
#
# 用法：
#   bash scripts/rollback.sh --to v0.6-automation --dry-run
#   bash scripts/rollback.sh --to v0.6-automation --yes
#   bash scripts/rollback.sh --to v0.6-automation --yes --schema=down
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---------- 参数 ----------
TO=""
DRY_RUN=0
SCHEMA="keep"
YES=0
SCHEMA_TO=""

APP_DIR="${APP_DIR:-$HOME/robot-autotest}"
RELEASE_DIR="${RELEASE_DIR:-$APP_DIR/releases}"
BACKUP_DIR="${BACKUP_DIR:-$APP_DIR/backups}"
ARTIFACTS_DIR="${ARTIFACTS_DIR:-$APP_DIR/artifacts}"
JAR_PATH="${JAR_PATH:-$APP_DIR/app/robot-autotest-backend.jar}"
DIST_PATH="${DIST_PATH:-$APP_DIR/dist}"
SERVICE="${SERVICE:-robot-autotest.service}"
SERVER_PORT="${SERVER_PORT:-8081}"
BASE_URL="${BASE_URL:-http://127.0.0.1:$SERVER_PORT}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-robot_dev}"
DB_USERNAME="${DB_USERNAME:-robot}"
DB_PASSWORD="${DB_PASSWORD:-}"

SRC_JAR=""
SRC_DIST=""
BACKUP_RUN_DIR=""

usage() {
    sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --to) TO="$2"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        --schema) SCHEMA="$2"; shift 2 ;;
        --schema=*) SCHEMA="${1#*=}"; shift ;;
        --schema-to) SCHEMA_TO="$2"; shift 2 ;;
        --yes) YES=1; shift ;;
        --app-dir) APP_DIR="$2"; shift 2 ;;
        --release-dir) RELEASE_DIR="$2"; shift 2 ;;
        --backup-dir) BACKUP_DIR="$2"; shift 2 ;;
        --artifacts-dir) ARTIFACTS_DIR="$2"; shift 2 ;;
        --jar) JAR_PATH="$2"; shift 2 ;;
        --dist) DIST_PATH="$2"; shift 2 ;;
        --service) SERVICE="$2"; shift 2 ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        --src-jar) SRC_JAR="$2"; shift 2 ;;
        --src-dist) SRC_DIST="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "[rollback] 未知参数：$1（--help 查看用法）" >&2; exit 2 ;;
    esac
done

[[ -n "$TO" ]] || { echo "[rollback] 缺少 --to <版本标签>（例：v0.6-automation）" >&2; exit 2; }
[[ "$SCHEMA" == "keep" || "$SCHEMA" == "down" ]] || { echo "[rollback] --schema 只能是 keep 或 down" >&2; exit 2; }

SRC_JAR="${SRC_JAR:-$RELEASE_DIR/$TO/robot-autotest-backend.jar}"
SRC_DIST="${SRC_DIST:-$RELEASE_DIR/$TO/dist}"

# ---------- 迁移版本推导 ----------
# 真实迁移集：V1 / V2_1~V2_5 / V4_1~V4_3 / V5_1~V5_5 / V6_1~V6_3 / V7_1。
# v0.3 没有独立迁移，回退到 v0.3 时 schema 天花板按 v0.2 的 2.5。
schema_ceiling() {
    case "$1" in
        v0.1*) echo "1" ;;
        v0.2*|v0.3*) echo "2.5" ;;
        v0.4*) echo "4.3" ;;
        v0.5*) echo "5.5" ;;
        v0.6*) echo "6.3" ;;
        v1.0*) echo "7.1" ;;
        *) echo "" ;;
    esac
}

CEILING="${SCHEMA_TO:-$(schema_ceiling "$TO")}"

# 把 V6_3 / V7_1 这类文件名转成可比较的数值：主版本*100 + 次版本
file_version() {
    local base major minor
    base="$(basename "$1")"
    major="$(sed -E 's/^V([0-9]+)(_[0-9]+)?__.*/\1/' <<<"$base")"
    minor="$(sed -E 's/^V[0-9]+_([0-9]+)__.*/\1/' <<<"$base")"
    [[ "$minor" == "$base" ]] && minor=0
    echo $((10#$major * 100 + 10#${minor:-0}))
}

ceiling_version() {
    local c="$1" major minor
    major="${c%%.*}"
    minor=0
    [[ "$c" == *.* ]] && minor="${c#*.}"
    echo $((10#${major:-0} * 100 + 10#${minor:-0}))
}

DOWN_FILES=()
if [[ "$SCHEMA" == "down" ]]; then
    if [[ -z "$CEILING" ]]; then
        echo "[rollback] ERROR: 无法从 '$TO' 推导迁移版本；用 --schema-to <如 6.3> 显式指定" >&2
        exit 2
    fi
    CEIL_V="$(ceiling_version "$CEILING")"
    while IFS= read -r f; do
        v="$(file_version "$f")"
        [[ "$v" -gt "$CEIL_V" ]] && DOWN_FILES+=("$f")
    done < <(find "$ROOT/db/down" -maxdepth 1 -name 'V*.down.sql' | sort -rV)
fi

# ---------- 执行/打印 ----------
run_cmd() { # 干跑只打印；真实执行才跑
    if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
        echo "  \$ $*"
        return 0
    fi
    "$@"
}

db_client() {
    if command -v mariadb >/dev/null 2>&1; then echo mariadb; else echo mysql; fi
}

STEP_NO=0
STEP_NAME="参数校验"
on_error() {
    local rc=$?
    echo "" >&2
    echo "[rollback] 失败：第 ${STEP_NO} 步「${STEP_NAME}」退出码 ${rc}" >&2
    echo "[rollback] 下一步做什么：" >&2
    echo "  1) 先看本步输出的错误；备份目录：${BACKUP_RUN_DIR:-（尚未创建）}" >&2
    echo "  2) 服务是否已停：systemctl --user status $SERVICE" >&2
    echo "  3) 修好原因后用同一命令重跑（备份/部署/起服可重入；down 的重入边界见 docs/ROLLBACK.md §2）" >&2
    echo "  4) 不要手工删表/删库；需要回退本脚本动作见 docs/ROLLBACK.md §5" >&2
    exit "$rc"
}
trap on_error ERR

echo "[rollback] 目标版本：$TO"
echo "[rollback] 模式：$([[ "$DRY_RUN" -eq 1 ]] && echo 只打印 || { [[ "$YES" -eq 1 ]] && echo 真实执行 || echo 未带--yes，只打印; })"
echo "[rollback] schema：$SCHEMA${CEILING:+（目标迁移天花板 $CEILING）}"
echo "[rollback] 应用目录：$APP_DIR"
echo "[rollback] 发布包：$SRC_JAR"
echo "[rollback] 前端产物：$SRC_DIST"
echo "[rollback] 服务：$SERVICE  健康检查：$BASE_URL/api/_ping"

if [[ ! -f "$SRC_JAR" ]]; then
    echo "[rollback] 警告：发布包不存在：$SRC_JAR（干跑继续；真实执行会中止）"
fi
if [[ ! -d "$SRC_DIST" ]]; then
    echo "[rollback] 提示：前端产物目录不存在：$SRC_DIST（单 jar 内嵌 dist 时可忽略）"
fi

if [[ "$SCHEMA" == "down" && "$DRY_RUN" -eq 0 && "$YES" -eq 0 ]]; then
    echo "[rollback] 拒绝：--schema=down 属于破坏性操作，真实执行必须显式 --yes" >&2
    echo "[rollback] 本次只打印计划，未做任何操作（退出 3）" >&2
    DOWN_REFUSED=1
else
    DOWN_REFUSED=0
fi

if [[ "$SCHEMA" == "down" ]]; then
    echo "[rollback] 将按倒序执行以下 down 迁移："
    if [[ ${#DOWN_FILES[@]} -eq 0 ]]; then
        echo "  （无：目标迁移天花板已覆盖当前全部版本）"
    else
        printf '  - %s\n' "${DOWN_FILES[@]##*/}"
    fi
fi

# 真实执行前置检查
if [[ "$DRY_RUN" -eq 0 && "$YES" -eq 1 ]]; then
    [[ -f "$SRC_JAR" ]] || { echo "[rollback] ERROR: 发布包不存在：$SRC_JAR" >&2; exit 2; }
    command -v systemctl >/dev/null 2>&1 || { echo "[rollback] ERROR: 本机没有 systemctl；真实回滚只能在 149 执行" >&2; exit 2; }
    [[ -n "$DB_PASSWORD" ]] || { echo "[rollback] ERROR: 缺少 DB_PASSWORD（备份与 down 迁移都需要）" >&2; exit 2; }
    if [[ "$SCHEMA" == "down" ]]; then
        echo ""
        echo "!! --schema=down 会删表/删列，v$TO 之后产生的数据只能从备份恢复。"
        if [[ -t 0 ]]; then
            read -r -p "   确认请输入 DOWN： " answer
            [[ "$answer" == "DOWN" ]] || { echo "[rollback] 未确认，已取消" >&2; exit 3; }
        else
            [[ "${ROLLBACK_DOWN_CONFIRM:-}" == "1" ]] || {
                echo "[rollback] 非交互环境需 ROLLBACK_DOWN_CONFIRM=1 才能执行 down" >&2; exit 3;
            }
        fi
    fi
fi

# ---------- 第 1 步：备份当前状态 ----------
STEP_NO=1
STEP_NAME="备份当前状态"
echo ""
echo "[rollback] ① 备份当前状态"
BACKUP_RUN_DIR="${BACKUP_DIR}/rollback-$(date +%Y%m%d_%H%M%S)"
echo "  备份目录：$BACKUP_RUN_DIR"
run_cmd mkdir -p "$BACKUP_RUN_DIR"
    echo "  - DB 全量：db.sql.gz"
    if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
        echo "  \$ MYSQL_PWD=\"\$DB_PASSWORD\" mysqldump -h$DB_HOST -P$DB_PORT -u$DB_USERNAME --single-transaction --routines --triggers $DB_NAME | gzip -9 > $BACKUP_RUN_DIR/db.sql.gz"
    else
        MYSQL_PWD="$DB_PASSWORD" mysqldump --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" \
            --single-transaction --routines --triggers --events --default-character-set=utf8mb4 \
            --databases "$DB_NAME" | gzip -9 >"$BACKUP_RUN_DIR/db.sql.gz"
        gzip -t "$BACKUP_RUN_DIR/db.sql.gz"
        echo "  已生成并校验 db.sql.gz（$(du -h "$BACKUP_RUN_DIR/db.sql.gz" | cut -f1)）"
    fi

    echo "  - 产物清单：artifacts-manifest.tsv"
    if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
        echo "  \$ find $ARTIFACTS_DIR -type f -printf '%P\\t%s\\n' > $BACKUP_RUN_DIR/artifacts-manifest.tsv"
    else
        if [[ -d "$ARTIFACTS_DIR" ]]; then
            find "$ARTIFACTS_DIR" -type f -printf '%P\t%s\n' >"$BACKUP_RUN_DIR/artifacts-manifest.tsv"
            echo "  产物文件数：$(wc -l <"$BACKUP_RUN_DIR/artifacts-manifest.tsv")"
        else
            echo "  警告：产物目录不存在，写空清单"
            : >"$BACKUP_RUN_DIR/artifacts-manifest.tsv"
        fi
    fi

    echo "  - 当前应用包：current-jar.jar + current-dist.tar.gz"
    if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
        echo "  \$ cp $JAR_PATH $BACKUP_RUN_DIR/current-jar.jar"
        echo "  \$ tar -czf $BACKUP_RUN_DIR/current-dist.tar.gz -C $(dirname "$DIST_PATH") $(basename "$DIST_PATH")"
    else
        if [[ -f "$JAR_PATH" ]]; then
            cp -p "$JAR_PATH" "$BACKUP_RUN_DIR/current-jar.jar"
        fi
        if [[ -d "$DIST_PATH" ]]; then
            tar -czf "$BACKUP_RUN_DIR/current-dist.tar.gz" -C "$(dirname "$DIST_PATH")" "$(basename "$DIST_PATH")"
        fi
        echo "  当前包已备份（供回退本次回滚用）"
        echo "$TO" >"$BACKUP_RUN_DIR/rollback-target.txt"
    fi

# ---------- 第 2 步：停服 → 部署 → 可选 down ----------
STEP_NO=2
STEP_NAME="停服并部署上一版"
echo ""
echo "[rollback] ② 停服 → 部署上一版 → 可选 down 迁移"
run_cmd systemctl --user stop "$SERVICE"

run_cmd mkdir -p "$(dirname "$JAR_PATH")"
if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
    echo "  \$ cp $SRC_JAR ${JAR_PATH}.new && mv ${JAR_PATH}.new $JAR_PATH"
else
    cp "$SRC_JAR" "${JAR_PATH}.new"
    mv "${JAR_PATH}.new" "$JAR_PATH"
fi

if [[ -d "$SRC_DIST" || "$DRY_RUN" -eq 1 ]]; then
    run_cmd mkdir -p "$DIST_PATH"
    if command -v rsync >/dev/null 2>&1; then
        run_cmd rsync -a --delete "$SRC_DIST/" "$DIST_PATH/"
    else
        if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
            echo "  \$ cp -a $SRC_DIST/. $DIST_PATH/"
        else
            cp -a "$SRC_DIST/." "$DIST_PATH/"
        fi
    fi
fi

if [[ "$SCHEMA" == "down" && ${#DOWN_FILES[@]} -gt 0 ]]; then
    echo "  执行 down 迁移（版本倒序，来源 db/down/）："
    for f in "${DOWN_FILES[@]}"; do
        echo "  - ${f##*/}"
        if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
            echo "    \$ MYSQL_PWD=\"\$DB_PASSWORD\" $(db_client) -h$DB_HOST -P$DB_PORT -u$DB_USERNAME $DB_NAME < $f"
        else
            MYSQL_PWD="$DB_PASSWORD" "$(db_client)" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" <"$f"
        fi
    done
elif [[ "$SCHEMA" == "down" ]]; then
    echo "  无需执行 down（当前版本未高于目标天花板 $CEILING）"
fi

# ---------- 第 3 步：起服 → 健康检查 ----------
STEP_NO=3
STEP_NAME="起服并健康检查"
echo ""
echo "[rollback] ③ 起服 → 健康检查 → 抽样"
run_cmd systemctl --user start "$SERVICE"

if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
    echo "  \$ curl -sS -o /dev/null -w '%{http_code}' $BASE_URL/api/_ping   # 期望 200，最多重试 30 次"
else
    ok=0
    for i in $(seq 1 30); do
        code="$(curl -sS -o /dev/null -m 3 -w '%{http_code}' "$BASE_URL/api/_ping" 2>/dev/null || echo 000)"
        if [[ "$code" == "200" ]]; then ok=1; break; fi
        sleep 2
    done
    [[ "$ok" -eq 1 ]] || { echo "[rollback] ERROR: 健康检查 60s 内未返回 200（最后=$code）" >&2; false; }
    echo "  健康检查通过：$BASE_URL/api/_ping → 200"
fi

echo ""
echo "[rollback] 抽样验证清单（必须人工完成并记录）："
echo "  [ ] 登录：浏览器打开 $BASE_URL ，用管理员账号登录"
echo "  [ ] 看一个历史报告：/reports/<runId> 能打开，metadata 十项齐全"
echo "  [ ] 跑一条用例：选轻量用例集执行，状态到 passed/failed（不是卡在 running）"
echo "  [ ] 记录：备份目录 $BACKUP_RUN_DIR"
if [[ "$DOWN_REFUSED" -eq 1 ]]; then
    exit 3
fi
if [[ "$DRY_RUN" -eq 1 || "$YES" -eq 0 ]]; then
    echo "[rollback] 干跑完成（未改任何状态，退出 0）"
else
    echo "[rollback] 回滚动作完成。RTO 需从执行第一秒计到抽样最后一项通过；记录表见 docs/ROLLBACK.md"
fi
echo "[rollback] 备份目录：$BACKUP_RUN_DIR"
