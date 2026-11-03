#!/usr/bin/env bash
# 两台服务器的轻量冒烟体检：健康检查 → ping → iperf3 打流 → perftest。
#
# 定位（重要）：这是**开发期验证平台执行链路**用的轻量用例，不是产品验收。
# 安全口径见 docs/adr/ADR-0013-执行安全分级与开发期禁跑令.md：
#   - 全部动作属 L0/L1：健康检查纯只读；iperf3 / perftest 只占用链路带宽，结束即退；
#   - **不装任何软件**：远端缺 iperf3 / perftest 只记 SKIP，不自动 apt install；
#   - **不改任何配置**：不动 MTU、不动 offload、不动 ring、不碰交换机；
#   - 唯一的副作用是临时起一个 iperf3 服务端进程（-1 一次即退 + trap 兜底 kill）。
#
# 用法（在 WSL / Linux 下跑，Windows Git Bash 缺 sshpass）：
#   export DUT_USER=xxx DUT_PASS=yyy
#   scripts/smoke-2node.sh --a 10.202.54.106 --b 10.202.54.107 --nic-a ens17np0 --nic-b ens17np0
#   scripts/smoke-2node.sh --a ... --b ... --nic-a ... --nic-b ... --skip-rdma --json out.json
#   scripts/smoke-2node.sh --a ... --b ... --nic-a ... --nic-b ... --dry-run
#
# 退出码：0 全通过（含 SKIP）；1 有 FAIL；2 参数或环境错误。

set -uo pipefail

# ------------------------------------------------------------------ 默认值
A_HOST=""; B_HOST=""; A_NIC=""; B_NIC=""
DUR=10            # iperf3 单次打流时长（秒）
RDMA_SECS=5       # perftest 时长（秒）
RDMA_DEV="mlx5_0" # perftest 设备名，环境不同用 --rdma-dev 覆盖
PING_COUNT=20
SKIP_TPUT=0; SKIP_RDMA=0; DRY_RUN=0
JSON_OUT=""
SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=8 -o LogLevel=ERROR"

# ------------------------------------------------------------------ 输出
C_OK=$'\033[32m'; C_NO=$'\033[31m'; C_SK=$'\033[33m'; C_H=$'\033[36m'; C_0=$'\033[0m'
if [[ ! -t 1 ]]; then C_OK=""; C_NO=""; C_SK=""; C_H=""; C_0=""; fi
RESULTS=()   # 每行 "PASS|FAIL|SKIP <阶段> <对象> <摘要>"
pass() { RESULTS+=("PASS $*"); printf "  ${C_OK}[ok]${C_0}   %s\n" "$*"; }
fail() { RESULTS+=("FAIL $*"); printf "  ${C_NO}[FAIL]${C_0} %s\n" "$*"; FAILED=1; }
skip() { RESULTS+=("SKIP $*"); printf "  ${C_SK}[skip]${C_0} %s\n" "$*"; }
head_() { printf "\n${C_H}== %s ==${C_0}\n" "$*"; }

FAILED=0
die() { printf "${C_NO}错误:${C_0} %s\n" "$*" >&2; exit 2; }

# ------------------------------------------------------------------ 参数
while [[ $# -gt 0 ]]; do
    case "$1" in
        --a) A_HOST="${2:-}"; shift 2 ;;
        --b) B_HOST="${2:-}"; shift 2 ;;
        --nic-a) A_NIC="${2:-}"; shift 2 ;;
        --nic-b) B_NIC="${2:-}"; shift 2 ;;
        --duration) DUR="${2:-}"; shift 2 ;;
        --rdma-dev) RDMA_DEV="${2:-}"; shift 2 ;;
        --ping-count) PING_COUNT="${2:-}"; shift 2 ;;
        --json) JSON_OUT="${2:-}"; shift 2 ;;
        --skip-tput) SKIP_TPUT=1; shift ;;
        --skip-rdma) SKIP_RDMA=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
        *) die "未知参数 $1（-h 看用法）" ;;
    esac
done
[[ -n "$A_HOST" && -n "$B_HOST" ]] || die "必须给 --a 与 --b 两个 IP"

DUT_USER="${DUT_USER:?请先 export DUT_USER}"
DUT_PASS="${DUT_PASS:-}"

# ssh 通道：优先 sshpass，其次无口令（key/agent）
if command -v sshpass >/dev/null 2>&1 && [[ -n "$DUT_PASS" ]]; then
    SSH_WRAP() { sshpass -p "$DUT_PASS" ssh $SSH_OPTS "$DUT_USER@$1" "$2"; }
elif [[ -z "$DUT_PASS" ]]; then
    SSH_WRAP() { ssh $SSH_OPTS -o BatchMode=yes "$DUT_USER@$1" "$2"; }
else
    die "给了 DUT_PASS 但本机没有 sshpass。Windows Git Bash 请改用 WSL 运行，或改用 ssh key（不设 DUT_PASS）"
fi

# 远端命令统一加超时，避免卡死；rc 与输出一起取回
rrun() { # rrun <host> <cmd> [timeout_s]
    local h="$1" c="$2" t="${3:-20}"
    SSH_WRAP "$h" "timeout ${t} bash -lc $(printf '%q' "$c")" 2>&1
}

# ------------------------------------------------------------------ 预检
head_ "S0 前置检查"
printf "  A=%s  B=%s  nic_a=%s  nic_b=%s  时长=%ss\n" \
    "$A_HOST" "$B_HOST" "${A_NIC:-自动}" "${B_NIC:-自动}" "$DUR"
if [[ $DRY_RUN -eq 1 ]]; then
    echo "  --dry-run：只打印计划，不连接任何设备。"
    exit 0
fi
for h in "$A_HOST" "$B_HOST"; do
    if rrun "$h" "true" 8 >/dev/null 2>&1; then pass "S0 ${h} SSH 可达"
    else fail "S0 ${h} SSH 不可达（后面阶段会连带失败，先修连通性）"; fi
done

# ------------------------------------------------------------------ S1 健康检查
# 全部只读命令。任何一条取不到值都只记 warn，不判 FAIL —— 健康检查是"看现状"，不是"卡门槛"。
health_one() { # health_one <label> <host> <nic>
    local L="$1" h="$2" nic="$3"
    local hn os kr up cpu load mem disk
    hn=$(rrun "$h" "hostname" 8 | tr -d '\r')
    os=$(rrun "$h" "grep -m1 PRETTY_NAME /etc/os-release | cut -d= -f2- | tr -d '\"'" 8 | tr -d '\r')
    kr=$(rrun "$h" "uname -r" 8 | tr -d '\r')
    up=$(rrun "$h" "uptime -p 2>/dev/null || uptime" 8 | tr -d '\r')
    pass "S1 ${L}($h) 主机 ${hn:-?} | ${os:-?} | kernel ${kr:-?} | ${up:-?}"

    cpu=$(rrun "$h" "nproc" 8 | tr -d '\r')
    load=$(rrun "$h" "cut -d' ' -f1-3 /proc/loadavg" 8 | tr -d '\r')
    mem=$(rrun "$h" "free -m | awk '/Mem:/{printf \"%d\", \$3*100/\$2}'" 8 | tr -d '\r')
    disk=$(rrun "$h" "df -P / | awk 'NR==2{gsub(/%/,\"\",\$5); print \$5}'" 8 | tr -d '\r')
    printf "  %s[info]%s   %s cpu=%s核 load=%s mem=%s%% disk=%s%%\n" \
        "$C_H" "$C_0" "$L" "${cpu:-?}" "${load:-?}" "${mem:-?}" "${disk:-?}"
    RESULTS+=("INFO S1 ${L} cpu=${cpu:-?} load=${load:-?} mem=${mem:-?}% disk=${disk:-?}%")
    [[ -n "$disk" && "$disk" -ge 90 ]] && fail "S1 ${L} 根分区使用率 ${disk}% ≥90%"

    if [[ -n "$nic" ]]; then
        local st sp
        st=$(rrun "$h" "cat /sys/class/net/$nic/operstate 2>/dev/null" 8 | tr -d '\r')
        sp=$(rrun "$h" "cat /sys/class/net/$nic/speed 2>/dev/null" 8 | tr -d '\r')
        if [[ "$st" == "up" ]]; then
            pass "S1 ${L} 网卡 ${nic} link=up speed=${sp:-?}Mb/s"
        else
            fail "S1 ${L} 网卡 ${nic} link=${st:-不存在}（打流会失败，先查线/端口）"
        fi
        local drv fw
        drv=$(rrun "$h" "ethtool -i $nic 2>/dev/null | awk -F': ' '/^driver/{print \$2}'" 8 | tr -d '\r')
        fw=$(rrun "$h" "ethtool -i $nic 2>/dev/null | awk -F': ' '/^firmware-version/{print \$2}'" 8 | tr -d '\r')
        printf "  %s[info]%s   %s %s driver=%s fw=%s\n" \
            "$C_H" "$C_0" "$L" "$nic" "${drv:-?}" "${fw:-?}"
        RESULTS+=("INFO S1 ${L} ${nic} driver=${drv:-?} fw=${fw:-?}")
    fi

    local ntp
    ntp=$(rrun "$h" "timedatectl show -p NTPSynchronized --value 2>/dev/null || chronyc tracking 2>/dev/null | head -1" 8 | tr -d '\r')
    if [[ "$ntp" == "yes" ]]; then pass "S1 ${L} 时钟已同步"
    else skip "S1 ${L} 时钟同步状态取不到（ntp=${ntp:-空}），不影响打流但影响日志对齐"; fi
}
head_ "S1 基础健康检查（只读）"
health_one "A" "$A_HOST" "$A_NIC"
health_one "B" "$B_HOST" "$B_NIC"

# ------------------------------------------------------------------ S2 ping
head_ "S2 ping 连通性（双向）"
ping_one() { # ping_one <label> <src> <dst>
    local L="$1" src="$2" dst="$3" out loss rtt
    out=$(rrun "$src" "ping -c $PING_COUNT -W 2 -q $dst" $((PING_COUNT * 2 + 10)))
    loss=$(sed -n 's/.*, \([0-9.]*\)% packet loss.*/\1/p' <<<"$out" | tail -1)
    rtt=$(sed -n 's/.*min\/avg\/max.*= \([0-9.]*\)\/\([0-9.]*\)\/.*/\2/p' <<<"$out" | tail -1)
    if [[ -z "$loss" ]]; then
        fail "S2 ${L}（${dst}）ping 无输出，可能未安装或不可达"
    elif [[ "$loss" == "0" ]]; then
        pass "S2 ${L}→${dst} 丢包 0% RTT 平均 ${rtt:-?} ms"
    elif awk "BEGIN{exit !($loss < 1)}"; then
        skip "S2 ${L}→${dst} 丢包 ${loss}%（<1%，可接受但需记录）"
    else
        fail "S2 ${L}→${dst} 丢包 ${loss}% RTT ${rtt:-?} ms"
    fi
}
ping_one "A→B" "$A_HOST" "$B_HOST"
ping_one "B→A" "$B_HOST" "$A_HOST"

# ------------------------------------------------------------------ S3 iperf3
ipf_json_sum() {
    # iperf3 -J 是多行 JSON（踩过坑：按行解析必失败），这里整段喂给 python 解析。
    # 兼容 sum_received（3.x 新版）/ sum（旧版）/ sum_sent 三种键名。
    local py; py=$(command -v python3 || command -v python) || { echo ""; return; }
    "$py" - "$1" <<'PY' 2>/dev/null
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print(""); raise SystemExit
end = d.get("end", {})
for key in ("sum_received", "sum", "sum_sent"):
    if key in end and "bits_per_second" in end[key]:
        print("%.2f" % (end[key]["bits_per_second"] / 1e9)); break
else:
    print("")
PY
}
head_ "S3 iperf3 打流（TCP，${DUR}s）"
if [[ $SKIP_TPUT -eq 1 ]]; then
    skip "S3 按 --skip-tput 跳过"
else
    if ! rrun "$B_HOST" "command -v iperf3 >/dev/null" 10 >/dev/null 2>&1; then
        skip "S3 B 端无 iperf3，跳过打流（不自动安装）"
    elif ! rrun "$A_HOST" "command -v iperf3 >/dev/null" 10 >/dev/null 2>&1; then
        skip "S3 A 端无 iperf3，跳过打流（不自动安装）"
    else
        SRV_PID=""
        cleanup_srv() { [[ -n "${SRV_PID:-}" ]] && rrun "$B_HOST" "pkill -f 'iperf3 -s -p 5201'" 8 >/dev/null 2>&1; }
        trap cleanup_srv EXIT INT TERM
        rrun "$B_HOST" "pkill -f 'iperf3 -s -p 5201' 2>/dev/null; nohup iperf3 -s -p 5201 -1 >/tmp/iperf3-srv.log 2>&1 & sleep 1; pgrep -f 'iperf3 -s -p 5201' >/dev/null && echo SRV_UP" 12 | grep -q SRV_UP \
            || fail "S3 B 端 iperf3 服务端未起来（看 /tmp/iperf3-srv.log）"
        SRV_PID="started"
        for streams in 1 8; do
            tmp=$(mktemp)
            rrun "$A_HOST" "iperf3 -c $B_HOST -p 5201 -t $DUR -P $streams -J" $((DUR + 25)) > "$tmp" 2>/dev/null
            gbps=$(ipf_json_sum "$tmp")
            rm -f "$tmp"
            if [[ -z "$gbps" ]]; then fail "S3 ${streams} 流：取不到吞吐（连接失败或服务端已退）"
            else printf "  %s[info]%s   %s\n" "$C_H" "$C_0" "TCP ${streams} 进程/流 → ${gbps} Gbps"
                 RESULTS+=("INFO S3 tcp_${streams}stream=${gbps}Gbps"); fi
            # -1 服务端一次即退，第二组要重启；先清端口再起，避免残留占用
            if [[ "$streams" == "1" ]]; then
                rrun "$B_HOST" "pkill -f 'iperf3 -s -p 5201' 2>/dev/null; sleep 1; nohup iperf3 -s -p 5201 -1 >/tmp/iperf3-srv2.log 2>&1 & sleep 1" 15 >/dev/null 2>&1
            fi
        done
        pass "S3 iperf3 打流完成（结果见上，阈值不在本脚本判定——轻量体检只记录现状）"
    fi
fi

# ------------------------------------------------------------------ S4 perftest
head_ "S4 perftest（RDMA，${RDMA_SECS}s）"
if [[ $SKIP_RDMA -eq 1 ]]; then
    skip "S4 按 --skip-rdma 跳过"
elif ! rrun "$A_HOST" "command -v ib_write_bw >/dev/null" 10 >/dev/null 2>&1; then
    skip "S4 A 端无 perftest（ib_write_bw），跳过（不自动安装）"
else
    rrun "$B_HOST" "pkill -f ib_write_bw 2>/dev/null; nohup ib_write_bw -d $RDMA_DEV -D $RDMA_SECS >/tmp/perftest-srv.log 2>&1 & sleep 2" 15 >/dev/null 2>&1
    out=$(rrun "$A_HOST" "ib_write_bw -d $RDMA_DEV -D $RDMA_SECS $B_HOST" $((RDMA_SECS + 30)))
    # perftest 的表头行是「#bytes #iterations BW peak BW average MsgRate」——
    # 数字在**下一行**。直接 grep 'BW average' 只会拿到表头（无数字），故取下一行第 4 列。
    bw=$(awk '/BW average/{getline; print $4; exit}' <<<"$out")
    if [[ -n "$bw" ]]; then
        pass "S4 ib_write_bw ${RDMA_DEV} 平均 ${bw} Gb/s"
    else
        fail "S4 perftest 未取到带宽（设备名 ${RDMA_DEV} 可能不对，或 RDMA 未就绪；原始输出见下）"
        sed -n '1,8p' <<<"$out" | sed 's/^/         /'
    fi
    rrun "$B_HOST" "pkill -f ib_write_bw" 8 >/dev/null 2>&1
fi

# ------------------------------------------------------------------ 汇总
head_ "汇总"
n_pass=$(printf '%s\n' "${RESULTS[@]}" | grep -c '^PASS' || true)
n_fail=$(printf '%s\n' "${RESULTS[@]}" | grep -c '^FAIL' || true)
n_skip=$(printf '%s\n' "${RESULTS[@]}" | grep -c '^SKIP' || true)
printf "  PASS %s   FAIL %s   SKIP %s\n" "$n_pass" "$n_fail" "$n_skip"
if [[ $n_fail -gt 0 ]]; then
    echo "  失败项："
    printf '%s\n' "${RESULTS[@]}" | grep '^FAIL' | sed 's/^FAIL /    - /'
fi

if [[ -n "$JSON_OUT" ]]; then
    {
        echo "{"
        printf '  "a": "%s", "b": "%s", "duration": %s,\n' "$A_HOST" "$B_HOST" "$DUR"
        printf '  "pass": %s, "fail": %s, "skip": %s,\n' "$n_pass" "$n_fail" "$n_skip"
        printf '  "results": ['
        first=1
        while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            [[ $first -eq 1 ]] && first=0 || printf ','
            printf '\n    "%s"' "$(sed 's/"/\\"/g' <<<"$line")"
        done <<<"$(printf '%s\n' "${RESULTS[@]}")"
        printf '\n  ]\n}\n'
    } > "$JSON_OUT"
    echo "  JSON 已写出: $JSON_OUT"
fi

[[ $n_fail -eq 0 ]] && exit 0 || exit 1
