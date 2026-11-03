#!/usr/bin/env bash
# 后端启动自检 —— G1 出口判据「新机器 30 分钟内跑起空壳」的可复现证据。
#
# 为什么要有这个脚本
# ------------------
# 这个项目有 450+ 条单测、6 条集成测试，但**没有一个起过 Spring 上下文**：
# 单测全是纯对象 + 替身，集成测试只连 JDBC 和 Flyway。也就是说，
# 「整个应用能不能装配起来」这件事从来没被验证过 —— 缺 bean、循环依赖、
# 配置项拼错、异常码映射错，都只会在真启动时暴露。
#
# 本脚本启动真实进程，只认日志里那行
#   Started RobotAutotestApplication in X.X seconds
# 作为通过判据（它出现即代表所有 bean 装配完成、端口已监听）。端口能连上但
# 没有这行，说明 Tomcat 起来了而应用上下文失败 —— 那种情况必须判失败。
#
# 为什么用 spring-boot:run 而不是 java -jar
# -----------------------------------------
# 本项目工作区在 WSL 的 /mnt/e（9p 挂到 NTFS）。`mvn package` 的 repackage 一步要
# 把 jar 改名成 .jar.original，在 9p 上会报
#   Unable to rename '...jar' to '...jar.original'
# 随后该 jar 连 `rm`/`mv` 都报 Device or resource busy（Windows 侧句柄锁住）。
# spring-boot:run 直接用 target/classes，不做改名，因此不受影响 ——
# 对"能不能跑起来"这个判据来说，它比打包更直接，也少一个环境坑。
# 真正要验可交付 jar 时，请在 WSL 原生路径（如 ~/build）下打包再验，别在 /mnt/e 上打。
#
# 用法
# ----
#   set -a; . ~/.config/robot-autotest/env; set +a   # 或自行导出 DB_*
#   bash scripts/boot-check.sh
#
# 环境变量：DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD（DB_PASSWORD 必给）
#          SERVER_PORT（默认 18081）/ BOOT_TIMEOUT（默认 180 秒）/ BOOT_LOG
#
# 退出码：0 启动成功且端口可访问；1 启动失败；2 前置条件不满足

set -uo pipefail

PORT="${SERVER_PORT:-18081}"
TIMEOUT="${BOOT_TIMEOUT:-180}"
LOG="${BOOT_LOG:-/tmp/robot-autotest-boot.log}"
DB_NAME="${DB_NAME:-robot_it}"

cd "$(dirname "$0")/.." || exit 2

if [[ -z "${DB_PASSWORD:-}" ]]; then
    echo "[FAIL] 未设置 DB_PASSWORD。应用设计上缺口令即启动失败（FR-v0.1-07），这不是缺陷。" >&2
    echo "       先执行：set -a; . ~/.config/robot-autotest/env; set +a" >&2
    exit 2
fi

# CRED_MASTER_KEY：按 CONVENTIONS-backend.md 只从环境注入（32 字节 base64），缺失即启动失败，
# 这是**有意的**防盗设计（TEST-PLAN TC-v0.2-403 专测这条），不要"修"成给默认值。
# 启动自检不需要真密钥，故缺省时造一个临时的，并在输出里标明性质。
if [[ -z "${CRED_MASTER_KEY:-}" ]]; then
    CRED_MASTER_KEY="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
    export CRED_MASTER_KEY
    echo "[INFO] CRED_MASTER_KEY 未设置 → 本次用临时随机密钥（仅够启动，不能用于真实数据）"
fi

echo "[INFO] 启动方式: mvn spring-boot:run（不经打包，见脚本头注释）"
echo "[INFO] DB      : ${DB_HOST:-127.0.0.1}:${DB_PORT:-3306}/${DB_NAME}"
echo "[INFO] 端口    : $PORT"

case "$DB_NAME" in
    *_it) ;;
    *) echo "[WARN] DB_NAME=$DB_NAME 不以 _it 结尾 —— 启动会执行读操作，请确认这不是开发/生产库" >&2 ;;
esac

: >"$LOG"
START=$(date +%s)
( cd backend && mvn -B -o -q -DskipTests \
    spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Dserver.port=$PORT" \
    -Dspring-boot.run.profiles=dev ) >>"$LOG" 2>&1 &
MVN_PID=$!

cleanup() {
    # spring-boot:run 会 fork 子 JVM，只杀 mvn 会留下孤儿占着端口
    for child in $(pgrep -P "$MVN_PID" 2>/dev/null); do
        kill "$child" 2>/dev/null
    done
    kill "$MVN_PID" 2>/dev/null
    for _ in $(seq 1 24); do
        kill -0 "$MVN_PID" 2>/dev/null || break
        sleep 0.25
    done
    kill -9 "$MVN_PID" 2>/dev/null
    for child in $(pgrep -P "$MVN_PID" 2>/dev/null); do
        kill -9 "$child" 2>/dev/null
    done
}
trap cleanup EXIT

READY=0
while (( $(date +%s) - START < TIMEOUT )); do
    if grep -q "Started RobotAutotestApplication in" "$LOG" 2>/dev/null; then
        READY=1
        break
    fi
    if ! kill -0 "$MVN_PID" 2>/dev/null; then
        break
    fi
    sleep 1
done

ELAPSED=$(( $(date +%s) - START ))

if (( READY )); then
    echo "[ OK ] 启动成功，耗时 ${ELAPSED}s"
    grep -m1 "Started RobotAutotestApplication in" "$LOG"
else
    echo "[FAIL] ${ELAPSED}s 内未出现 Started 日志（或进程已退出）"
    echo "------- 日志尾部 -------"
    tail -40 "$LOG"
    echo "-----------------------"
    exit 1
fi

# 端口可达性：任何 HTTP 状态码都证明服务在监听；000 才是不可达。
# 注意这里不断言 200 —— 大多数接口需要鉴权，返回 401/403 是正常的。
# 但 5xx 一律记失败：那说明请求进到了服务端却炸了（2026-11-04 就是这样发现
# 未匹配路径被兜底成 500）。
echo ""
echo "HTTP 探活（401/403/404 属正常；5xx 视为失败）："
BAD=0
for p in / /api/auth/me /api/projects; do
    code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "http://127.0.0.1:${PORT}${p}" 2>/dev/null || echo 000)
    printf '  %-16s %s\n' "$p" "$code"
    case "$code" in
        000|5??) BAD=$((BAD + 1)) ;;
    esac
done

if (( BAD > 0 )); then
    echo ""
    echo "[FAIL] 有 ${BAD} 个探活返回 5xx 或不可达 —— 服务起来了但请求会炸，详见 $LOG"
    exit 1
fi

echo ""
echo "[PASS] 启动自检通过（${ELAPSED}s，日志 $LOG）"
