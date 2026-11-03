#!/usr/bin/env bash
# HRD-03：24 小时稳定性压测（给 149 Linux 用；Windows 开发态不适用）
#
# 负载：轻量用例集每 10 分钟触发一次；4 并发用例集每 2 小时触发一次。
# 监控：JVM heap（jcmd）、进程 RSS（ps）、DB 连接数（mysql）、磁盘（df）、GC 次数（jstat），
#       每 --interval 秒落一行 CSV。
# 判定：无崩溃/无重启；RSS 增长 <20%；连接数不超过池上限（无泄漏）。
#
# 本脚本只做“采集与判定”，不会伪造任何数据：
#   - 缺 token / 缺用例集 ID / 缺 mysql 客户端 / 缺 DB_PASSWORD 时明确报错退出（exit 2）。
#   - 进程或 jcmd 不可用时对应列写 NA，并在摘要里把该项标成“无法判定”，不会当成通过。
#
# 用法：
#   STABILITY_TOKEN=<平台登录 token> DB_PASSWORD=<库口令> \
#   STABILITY_LIGHT_SET_ID=12 STABILITY_HEAVY_SET_ID=13 \
#   bash scripts/stability-24h.sh --project 1 --duration 24h --interval 60s
#
# 仅监控（不触发负载，用于冒烟/排障；不能作为 24h 压测结论）：
#   DB_PASSWORD=<库口令> bash scripts/stability-24h.sh --monitor-only \
#       --duration 3m --interval 20s --label smoke --process-pattern mysqld
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---------- 默认值 ----------
DURATION="24h"
INTERVAL="60s"
COOLDOWN="5m"
BASE_URL="http://127.0.0.1:8081"
OUT_DIR="$ROOT/artifacts/stability"
LABEL="24h"
PROJECT_ID=""
TOKEN="${STABILITY_TOKEN:-}"
LIGHT_SET="${STABILITY_LIGHT_SET_ID:-}"
HEAVY_SET="${STABILITY_HEAVY_SET_ID:-}"
MONITOR_ONLY=0
DISK_PATH="/"
PROCESS_PATTERN="${STABILITY_PROCESS_PATTERN:-robot-autotest.*\\.jar}"
JCMD_BIN="${STABILITY_JCMD:-}"
CONN_CEILING="${STABILITY_CONN_CEILING:-16}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-robot_dev}"
DB_USERNAME="${DB_USERNAME:-robot}"
DB_PASSWORD="${DB_PASSWORD:-}"

usage() {
    sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --duration) DURATION="$2"; shift 2 ;;
        --interval) INTERVAL="$2"; shift 2 ;;
        --cooldown) COOLDOWN="$2"; shift 2 ;;
        --project) PROJECT_ID="$2"; shift 2 ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        --out) OUT_DIR="$2"; shift 2 ;;
        --label) LABEL="$2"; shift 2 ;;
        --token) TOKEN="$2"; shift 2 ;;
        --light-set) LIGHT_SET="$2"; shift 2 ;;
        --heavy-set) HEAVY_SET="$2"; shift 2 ;;
        --disk-path) DISK_PATH="$2"; shift 2 ;;
        --process-pattern) PROCESS_PATTERN="$2"; shift 2 ;;
        --jcmd) JCMD_BIN="$2"; shift 2 ;;
        --conn-ceiling) CONN_CEILING="$2"; shift 2 ;;
        --monitor-only) MONITOR_ONLY=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "[stability] 未知参数：$1（--help 查看用法）" >&2; exit 2 ;;
    esac
done

# ---------- 工具函数 ----------
fail_dep() {
    echo "[stability] ERROR: $*" >&2
    exit 2
}

parse_duration() { # 24h / 3m / 20s / 裸秒数 -> 秒
    local raw="$1"
    if [[ "$raw" =~ ^([0-9]+)$ ]]; then echo "$raw"; return; fi
    if [[ "$raw" =~ ^([0-9]+)([smhd])$ ]]; then
        local n="${BASH_REMATCH[1]}" u="${BASH_REMATCH[2]}"
        case "$u" in
            s) echo "$n" ;;
            m) echo $((n * 60)) ;;
            h) echo $((n * 3600)) ;;
            d) echo $((n * 86400)) ;;
        esac
        return
    fi
    fail_dep "无法解析时长 '$raw'（例：24h / 3m / 60s）"
}

DURATION_S="$(parse_duration "$DURATION")"
INTERVAL_S="$(parse_duration "$INTERVAL")"
COOLDOWN_S="$(parse_duration "$COOLDOWN")"
[[ "$INTERVAL_S" -ge 1 ]] || fail_dep "--interval 至少 1 秒"

# ---------- 依赖检查（缺就退出，不造假） ----------
command -v curl >/dev/null 2>&1 || fail_dep "缺少 curl"
command -v ps >/dev/null 2>&1 || fail_dep "缺少 ps"
command -v df >/dev/null 2>&1 || fail_dep "缺少 df"
command -v mysql >/dev/null 2>&1 || fail_dep "缺少 mysql 客户端（DB 连接数是必采指标）"
[[ -n "$DB_PASSWORD" ]] || fail_dep "缺少 DB_PASSWORD（连接数采集需要库口令，仓库不落口令）"

if [[ "$MONITOR_ONLY" -eq 0 ]]; then
    [[ -n "$TOKEN" ]] || fail_dep "缺少 token：设 STABILITY_TOKEN 或 --token（--monitor-only 可只采指标）"
    [[ -n "$PROJECT_ID" ]] || fail_dep "缺少 --project（触发负载必须指定项目 ID）"
    [[ -n "$LIGHT_SET" ]] || fail_dep "缺少 STABILITY_LIGHT_SET_ID / --light-set（轻量用例集 ID）"
    [[ -n "$HEAVY_SET" ]] || fail_dep "缺少 STABILITY_HEAVY_SET_ID / --heavy-set（4 并发用例集 ID）"
fi

mkdir -p "$OUT_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
CSV="$OUT_DIR/stability-${LABEL}.csv"
TXT="$OUT_DIR/stability-${LABEL}.txt"
LOAD_LOG="$OUT_DIR/stability-${LABEL}-load.log"

echo "[stability] 模式=$([[ $MONITOR_ONLY -eq 1 ]] && echo 仅监控 || echo 负载+监控) label=$LABEL 时长=${DURATION_S}s 间隔=${INTERVAL_S}s"
echo "[stability] 输出：$CSV / $TXT"

# ---------- 采集 ----------
find_pid() {
    # 用 ps 全量匹配，再排除本脚本自身（脚本命令行里可能就带 --process-pattern 的值，
    # 用 pgrep -f 会把执行命令替换的子 shell 当成目标进程，导致 restarts 假报警）。
    ps -eww -o pid=,args= 2>/dev/null \
        | PROCESS_PATTERN="$PROCESS_PATTERN" SELF_PID="$BASHPID" awk \
              '$1 != ENVIRON["SELF_PID"] && $0 !~ /stability-24h\.sh/ \
               && $0 ~ ENVIRON["PROCESS_PATTERN"] {pid=$1} END{if(pid) print pid}'
}

resolve_jcmd() {
    if [[ -n "$JCMD_BIN" && -x "$JCMD_BIN" ]]; then echo "$JCMD_BIN"; return; fi
    if command -v jcmd >/dev/null 2>&1; then command -v jcmd; return; fi
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jcmd" ]]; then echo "$JAVA_HOME/bin/jcmd"; return; fi
    echo ""
}

JCMD="$(resolve_jcmd)"
HAVE_JSTAT=0
command -v jstat >/dev/null 2>&1 && HAVE_JSTAT=1
[[ -z "$JCMD" ]] && echo "[stability] 警告：未找到 jcmd，JVM heap 列将记为 NA（149 上请把 JDK bin 放进 PATH）"
[[ "$HAVE_JSTAT" -eq 0 ]] && echo "[stability] 警告：未找到 jstat，GC 次数列将记为 NA"

read_rss_mb() { # pid -> MB
    local pid="$1" kb
    kb="$(ps -o rss= -p "$pid" 2>/dev/null | tr -d ' ' || true)"
    [[ -n "$kb" ]] && awk -v k="$kb" 'BEGIN{printf "%.1f", k/1024}' || echo "NA"
}

read_heap() { # pid -> "used_mb total_mb"
    local pid="$1" out used total
    [[ -z "$JCMD" ]] && { echo "NA NA"; return; }
    out="$("$JCMD" "$pid" GC.heap_info 2>/dev/null || true)"
    used="$(awk 'match($0,/used [0-9]+K/){s=substr($0,RSTART+5,RLENGTH-6); printf "%.1f", s/1024; exit}' <<<"$out")"
    total="$(awk 'match($0,/total [0-9]+K/){s=substr($0,RSTART+6,RLENGTH-7); printf "%.1f", s/1024; exit}' <<<"$out")"
    echo "${used:-NA} ${total:-NA}"
}

read_gc_count() { # pid -> 累计 YGC+FGC+CGC
    local pid="$1" n
    [[ "$HAVE_JSTAT" -eq 1 ]] || { echo "NA"; return; }
    n="$(jstat -gc "$pid" 2>/dev/null | awk 'NR==1{for(i=1;i<=NF;i++)h[i]=$i} NR==2{for(i=1;i<=NF;i++) if(h[i]=="YGC"||h[i]=="FGC"||h[i]=="CGC"||h[i]=="ZGC") s+=$i} END{if(s!="")printf "%d",s}')"
    echo "${n:-NA}"
}

read_db_conns() {
    local n
    n="$(MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" \
         --batch --skip-column-names -e \
         "SELECT COUNT(*) FROM information_schema.processlist WHERE user='$DB_USERNAME' AND db='$DB_NAME';" \
         2>/dev/null || true)"
    echo "${n:-NA}"
}

read_disk() { # -> "used_pct free_gb"
    df -P "$DISK_PATH" 2>/dev/null | awk 'NR==2{p=$5; gsub("%","",p); printf "%s %.1f", p, $4/1024/1024}'
}

http_code() {
    curl -s -o /dev/null -m 5 -w '%{http_code}' "$BASE_URL/api/_ping" 2>/dev/null || echo "000"
}

# ---------- 负载触发 ----------
LOAD_OK=0
LOAD_FAIL=0
trigger_run() { # $1=test_set_id $2=并发 $3=标签
    local set_id="$1" conc="$2" tag="$3" body code
    body="{\"projectId\":$PROJECT_ID,\"testSetId\":$set_id,\"concurrency\":$conc,\"runRequestId\":\"STAB-${LABEL}-${STAMP}-${tag}-$(date +%s)\"}"
    code="$(curl -s -o /tmp/stability-load-$$.json -m 15 -w '%{http_code}' \
        -X POST "$BASE_URL/api/execution/runs" \
        -H "Authorization: Bearer $TOKEN" \
        -H "X-Project-Id: $PROJECT_ID" \
        -H "Content-Type: application/json" \
        -d "$body" 2>/dev/null || echo 000)"
    if [[ "$code" =~ ^2 ]]; then
        LOAD_OK=$((LOAD_OK + 1))
    else
        LOAD_FAIL=$((LOAD_FAIL + 1))
    fi
    echo "$(date '+%F %T') trigger tag=$tag set=$set_id conc=$conc http=$code body=$(tr -d '\n' </tmp/stability-load-$$.json 2>/dev/null | head -c 200)" >>"$LOAD_LOG"
    rm -f "/tmp/stability-load-$$.json"
}

# ---------- 汇总（信号中断也会落盘） ----------
START_EPOCH="$(date +%s)"
LAST_EPOCH="$START_EPOCH"
SAMPLES=0
FIRST_PID=""
LAST_PID=""
RESTARTS=0
MISSING=0
FIRST_RSS=""
PEAK_RSS=""
FIRST_CONN=""
PEAK_CONN=""
LAST_CONN=""
HTTP_FAIL=0
ABORTED=0

finalize() {
    local end_epoch now_elapsed result rss_growth conn_leak conn_note
    end_epoch="$(date +%s)"
    now_elapsed=$((end_epoch - START_EPOCH))

    if [[ -n "$FIRST_RSS" && -n "$PEAK_RSS" ]]; then
        rss_growth="$(awk -v a="$FIRST_RSS" -v b="$PEAK_RSS" 'BEGIN{ if(a<=0){print "NA"} else {printf "%.1f%%", (b-a)/a*100} }')"
    else
        rss_growth="NA"
    fi

    if [[ -z "$LAST_CONN" || "$LAST_CONN" == "NA" ]]; then
        conn_leak="无法判定"
        conn_note="DB 连接数未采到"
    elif [[ "$PEAK_CONN" -gt "$CONN_CEILING" ]]; then
        conn_leak="有(+$((PEAK_CONN - CONN_CEILING)) 超出池上限)"
        conn_note="峰值 $PEAK_CONN > 上限 $CONN_CEILING（Hikari 池上限），按泄漏处理"
    else
        conn_leak="无"
        conn_note="峰值 $PEAK_CONN ≤ 池上限 $CONN_CEILING；空闲期停留在池上限属正常，不按基线首样本判定"
    fi

    if [[ "$ABORTED" -eq 1 ]]; then
        result="ABORTED"
    elif [[ "$MONITOR_ONLY" -eq 1 ]]; then
        result="MONITOR-ONLY"
    elif [[ -z "$FIRST_PID" || "$MISSING" -gt 0 || "$rss_growth" == "NA" || "$conn_leak" == "无法判定" ]]; then
        result="INCOMPLETE"
    elif [[ "$(awk -v g="${rss_growth%%%*}" 'BEGIN{print (g<20)?1:0}')" -eq 1 && "$conn_leak" == "无" && "$RESTARTS" -eq 0 && "$HTTP_FAIL" -eq 0 && "$LOAD_FAIL" -eq 0 ]]; then
        result="PASS"
    else
        result="FAIL"
    fi

    {
        echo "# 稳定性压测摘要（stability-${LABEL}）"
        echo "# 本文件由 scripts/stability-24h.sh 生成，数值均来自同目录 stability-${LABEL}.csv"
        echo "mode=$([[ $MONITOR_ONLY -eq 1 ]] && echo monitor-only || echo load+monitor)"
        echo "started_at=$(date -d "@$START_EPOCH" '+%F %T %Z')"
        echo "ended_at=$(date -d "@$end_epoch" '+%F %T %Z')"
        echo "elapsed_s=$now_elapsed"
        echo "samples=$SAMPLES"
        echo "restarts=$RESTARTS"
        echo "process_missing_samples=$MISSING"
        echo "rss_first_mb=${FIRST_RSS:-NA}"
        echo "rss_peak_mb=${PEAK_RSS:-NA}"
        echo "rss_growth=$rss_growth"
        echo "conn_baseline=${FIRST_CONN:-NA}"
        echo "conn_peak=${PEAK_CONN:-NA}"
        echo "conn_final=${LAST_CONN:-NA}"
        echo "conn_ceiling=$CONN_CEILING"
        echo "conn_leak=$conn_leak"
        echo "conn_note=$conn_note"
        echo "http_failures=$HTTP_FAIL"
        echo "load_triggers_ok=$LOAD_OK"
        echo "load_triggers_fail=$LOAD_FAIL"
        echo "result=$result"
        echo "judge_hint=PASS 需同时满足：restarts=0、rss_growth<20%、conn_leak=无、无 HTTP/触发失败、样本齐全"
    } >"$TXT"

    echo "[stability] 摘要：$TXT（result=$result, restarts=$RESTARTS, rss_growth=$rss_growth, conn_leak=$conn_leak）"
}

on_signal() {
    ABORTED=1
    finalize
    exit 130
}
trap on_signal INT TERM

# ---------- 主循环 ----------
echo "ts,elapsed_s,pid,rss_mb,heap_used_mb,heap_total_mb,gc_count,db_conns,disk_used_pct,disk_free_gb,http_code,load_ok,load_fail" >"$CSV"

NEXT_LIGHT="$START_EPOCH"
NEXT_HEAVY="$START_EPOCH"

echo "[stability] 开始采样（Ctrl-C 会落盘摘要并退出 130）"
while :; do
    NOW="$(date +%s)"
    ELAPSED=$((NOW - START_EPOCH))
    [[ "$ELAPSED" -ge "$DURATION_S" ]] && break

    if [[ "$MONITOR_ONLY" -eq 0 ]]; then
        if [[ "$NOW" -ge "$NEXT_LIGHT" ]]; then
            trigger_run "$LIGHT_SET" 1 "light"
            NEXT_LIGHT=$((NEXT_LIGHT + 600))
        fi
        if [[ "$NOW" -ge "$NEXT_HEAVY" ]]; then
            trigger_run "$HEAVY_SET" 4 "heavy"
            NEXT_HEAVY=$((NEXT_HEAVY + 7200))
        fi
    fi

    PID="$(find_pid || true)"
    if [[ -z "$PID" ]]; then
        MISSING=$((MISSING + 1))
        CODE="$(http_code)"
        [[ "$CODE" == "200" ]] || HTTP_FAIL=$((HTTP_FAIL + 1))
        echo "$(date '+%F %T'),$ELAPSED,NA,NA,NA,NA,NA,$(read_db_conns),$(read_disk | awk '{print $1","$2}'),$CODE,$LOAD_OK,$LOAD_FAIL" >>"$CSV"
    else
        if [[ -n "$LAST_PID" && "$PID" != "$LAST_PID" ]]; then
            RESTARTS=$((RESTARTS + 1))
        fi
        LAST_PID="$PID"
        [[ -z "$FIRST_PID" ]] && FIRST_PID="$PID"

        RSS="$(read_rss_mb "$PID")"
        read -r HEAP_USED HEAP_TOTAL <<<"$(read_heap "$PID")"
        GC="$(read_gc_count "$PID")"
        CONN="$(read_db_conns)"
        DISK="$(read_disk)"
        CODE="$(http_code)"
        [[ "$CODE" == "200" ]] || HTTP_FAIL=$((HTTP_FAIL + 1))

        if [[ "$RSS" != "NA" ]]; then
            [[ -z "$FIRST_RSS" ]] && FIRST_RSS="$RSS"
            [[ -z "$PEAK_RSS" ]] && PEAK_RSS="$RSS"
            PEAK_RSS="$(awk -v a="$PEAK_RSS" -v b="$RSS" 'BEGIN{print (b>a)?b:a}')"
        fi
        if [[ "$CONN" != "NA" ]]; then
            [[ -z "$FIRST_CONN" ]] && FIRST_CONN="$CONN"
            [[ -z "$PEAK_CONN" ]] && PEAK_CONN="$CONN"
            PEAK_CONN=$(( CONN > PEAK_CONN ? CONN : PEAK_CONN ))
            LAST_CONN="$CONN"
        fi

        echo "$(date '+%F %T'),$ELAPSED,$PID,$RSS,$HEAP_USED,$HEAP_TOTAL,$GC,$CONN,$(awk '{print $1","$2}' <<<"$DISK"),$CODE,$LOAD_OK,$LOAD_FAIL" >>"$CSV"
    fi
    SAMPLES=$((SAMPLES + 1))

    NOW="$(date +%s)"
    REMAIN=$((DURATION_S - (NOW - START_EPOCH)))
    [[ "$REMAIN" -le 0 ]] && break
    SLEEP_S="$INTERVAL_S"
    [[ "$SLEEP_S" -gt "$REMAIN" ]] && SLEEP_S="$REMAIN"
    sleep "$SLEEP_S"
done

# 负载结束后的静置期：让连接池回到空闲态再判泄漏（默认 5 分钟）
if [[ "$MONITOR_ONLY" -eq 0 && "$COOLDOWN_S" -gt 0 ]]; then
    echo "[stability] 负载结束，静置 ${COOLDOWN_S}s 后再做连接数判定"
    sleep "$COOLDOWN_S"
    CONN="$(read_db_conns)"
    [[ "$CONN" != "NA" ]] && LAST_CONN="$CONN"
fi

finalize
exit 0
