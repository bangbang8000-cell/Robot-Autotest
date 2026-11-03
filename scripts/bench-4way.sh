#!/usr/bin/env bash
# EXE-07 / T7-2：4 并发压测编排脚本。
#
# 用固定 fixture（fixtures/bench/4suite-2cases/suite{1..4}.robot：4 suite × 2 case，
# 每条用例固定 Sleep 6s，理论串行总时长 48s）对比三种执行形态的墙钟耗时：
#   serial  单进程串行跑 4 个套件（robot 一次传入 4 个套件路径）—— 基线
#   p4      pabot --processes 4 —— 目标形态
#   p2      pabot --processes 2 —— 对照组
#
# 指标口径（写死在这里，证据文件必须按这套口径引用）：
#   speedup  = serial 平均耗时 / p4 平均耗时。门槛：平均 ≥ 2.5x，且逐轮最差 ≥ 2.2x。
#   conflict = 租约冲突次数。判定方式：每次跑完 p4，对产物目录（含
#              pabot_results/pabot_manager.log 与各 shard 日志）递归检索冲突关键字，
#              命中行数计入该轮；各轮累加得到 conflict。关键字集见 CONFLICT_PATTERN，
#              覆盖 "Failed to acquire" 与 "lease conflict" 两类写法。门槛：0。
#   overhead = (p4 平均耗时 - serial 平均耗时/4) / (serial 平均耗时/4) × 100%。
#              即 p4 相对「理论 1/4」多花的耗时占比（进程启动 / 编排 / 结果合并的开销）。
#   p95      = p4 逐轮耗时的 P95（耗时升序排序，取 ceil(0.95×N) 位，N = 轮次数）。
#
# 安全：每轮只清空 <artifacts>/round-NN（NN 为两位轮次号，且必须落在 --artifacts 之下）；
#       脚本不对其他任何路径做删除。
#
# 退出码：
#   0  全部轮次跑完且统计已打印
#   1  clean_round_dir 拒绝了非法清理路径（安全兜底，正常不会出现）
#   2  参数非法
#   3  环境不足：venv/bin 不可执行、缺 pabot/robot、缺 fixture、artifacts 不可写、
#      date 不支持纳秒。此时不打印任何耗时与统计数字，避免证据里出现假数据
#   4  执行失败：某模式非零退出或未产出 output.xml。同样不打印统计数字
#
# 环境变量（与 spec §4.4 一致）：TZ=Asia/Shanghai、LANG=C.UTF-8、PYTHONUNBUFFERED=1，
# 并把 $VENV 前置到 PATH，子进程内嵌套调用的 robot 才落在同一个解释器环境。
#
# 用法见 `bash scripts/bench-4way.sh --help`。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ROUNDS=20
VENV="${HOME}/rf-stack/.venv/bin"
ARTIFACTS="$ROOT/artifacts/bench"
FIXTURES="$ROOT/fixtures/bench/4suite-2cases"

# 租约冲突关键字集：只认「拿不到租约」这一类明确失败，避免把日志里普通出现的
# lease 字样误判成冲突（口径见文件头 conflict 段）。
CONFLICT_PATTERN='Failed to acquire|failed to acquire|lease conflict|Lease conflict|conflicting lease|could not acquire lease|lease acquisition failed|resource is busy|Resource busy'

usage() {
    cat <<'USAGE'
用法：bash scripts/bench-4way.sh [选项]

  --rounds N        轮次数，正整数，默认 20
  --venv <path>     venv 的 bin 目录，默认 $HOME/rf-stack/.venv/bin
  --artifacts <dir> 产物根目录，默认 <仓库>/artifacts/bench
  -h, --help        打印本说明

模式：serial（单进程串行 4 套件）/ p4（pabot --processes 4）/ p2（pabot --processes 2 对照）
示例：bash scripts/bench-4way.sh --rounds 20 --venv "$HOME/rf-stack/.venv/bin"
USAGE
}

fail_env() {
    echo "[bench] 环境不足：$*" >&2
    echo "[bench] 未输出任何统计数据（本脚本不接受环境不足时的占位数字）。" >&2
    exit 3
}

fail_run() {
    echo "[bench] 执行失败：$*" >&2
    echo "[bench] 已中止，不打印任何耗时/统计数字；请先看失败轮次的 stdout.log 与 robot_stderr.out。" >&2
    exit 4
}

now_ms() {
    # 依赖 GNU date 的 %N（149 Ubuntu 与本机 Git Bash 均支持，preflight 已校验）
    local ns
    ns="$(date +%s%N)"
    echo $(( ns / 1000000 ))
}

clean_round_dir() {
    # 只允许清理 --artifacts 之下、目录名 round-* 的路径
    local dir="$1"
    local base
    base="$(basename -- "$dir")"
    if [[ "$base" != round-* ]]; then
        echo "[bench] 拒绝清理非 round-* 目录：$dir" >&2
        return 1
    fi
    case "$dir" in
        "$ARTIFACTS"/*) ;;
        *)
            echo "[bench] 拒绝清理不在 $ARTIFACTS 之下的路径：$dir" >&2
            return 1
            ;;
    esac
    [[ -d "$dir" ]] && rm -rf -- "$dir"
    return 0
}

count_lease_conflicts() {
    # 打印命中行数（整数）；目录不存在按 0 计
    local dir="$1"
    if [[ ! -d "$dir" ]]; then
        printf '0'
        return
    fi
    grep -R -I -E -- "$CONFLICT_PATTERN" "$dir" 2>/dev/null | wc -l | tr -d ' '
}

avg_of() {
    # avg_of <n> <值...>
    local n="$1"
    shift
    if [[ "$n" -le 0 ]]; then
        printf 'N/A'
        return
    fi
    printf '%s\n' "$@" | awk -v n="$n" '{ s += $1 } END { printf "%.2f", s / n }'
}

p95_of() {
    # p95_of <n> <值...>：升序后取 ceil(0.95×n) 位
    local n="$1"
    shift
    if [[ "$n" -le 0 ]]; then
        printf 'N/A'
        return
    fi
    printf '%s\n' "$@" | sort -n | awk -v p=0.95 '
        { v[NR] = $1 }
        END {
            if (NR == 0) { print "N/A"; exit }
            idx = int(NR * p)
            if (idx != NR * p) idx = idx + 1
            if (idx < 1) idx = 1
            if (idx > NR) idx = NR
            print v[idx]
        }'
}

run_mode() {
    # run_mode <serial|p4|p2> <产物目录>；返回被测命令的退出码
    local mode="$1"
    local outdir="$2"
    local rc procs

    mkdir -p "$outdir" || return 1

    case "$mode" in
        serial)
            env TZ=Asia/Shanghai LANG=C.UTF-8 PYTHONUNBUFFERED=1 PATH="$VENV:$PATH" \
                "$VENV/robot" \
                --outputdir "$outdir" \
                --include bench \
                --name "Bench Serial" \
                "${SUITES[@]}" \
                >"$outdir/stdout.log" 2>"$outdir/robot_stderr.out"
            rc=$?
            ;;
        p4|p2)
            procs="${mode#p}"
            env TZ=Asia/Shanghai LANG=C.UTF-8 PYTHONUNBUFFERED=1 PATH="$VENV:$PATH" \
                "$VENV/pabot" \
                --outputdir "$outdir" \
                --processes "$procs" \
                --include bench \
                --name "Bench ${mode}" \
                "${SUITES[@]}" \
                >"$outdir/stdout.log" 2>"$outdir/robot_stderr.out"
            rc=$?
            ;;
        *)
            echo "[bench] 未知模式：$mode" >&2
            return 1
            ;;
    esac

    return "$rc"
}

# ---------- 参数解析 ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --rounds)
            [[ $# -ge 2 ]] || { echo "[bench] 参数错误：--rounds 缺少取值" >&2; exit 2; }
            ROUNDS="$2"
            shift 2
            ;;
        --rounds=*)
            ROUNDS="${1#*=}"
            shift
            ;;
        --venv)
            [[ $# -ge 2 ]] || { echo "[bench] 参数错误：--venv 缺少取值" >&2; exit 2; }
            VENV="$2"
            shift 2
            ;;
        --venv=*)
            VENV="${1#*=}"
            shift
            ;;
        --artifacts)
            [[ $# -ge 2 ]] || { echo "[bench] 参数错误：--artifacts 缺少取值" >&2; exit 2; }
            ARTIFACTS="$2"
            shift 2
            ;;
        --artifacts=*)
            ARTIFACTS="${1#*=}"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "[bench] 参数错误：无法识别的选项 $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ ! "$ROUNDS" =~ ^[0-9]+$ ]] || [[ "$ROUNDS" -le 0 ]]; then
    echo "[bench] 参数错误：--rounds 必须是正整数，收到 '$ROUNDS'" >&2
    exit 2
fi

# ---------- preflight（环境不足一律 exit 3，不输出数字） ----------
[[ -d "$VENV" ]] || fail_env "venv bin 目录不存在：$VENV"
[[ -x "$VENV/robot" ]] || fail_env "找不到可执行的 robot：$VENV/robot"
[[ -x "$VENV/pabot" ]] || fail_env "找不到可执行的 pabot：$VENV/pabot（149 上应为 ~/rf-stack/.venv/bin/pabot）"

if ! [[ "$(date +%s%N)" =~ ^[0-9]+$ ]]; then
    fail_env "当前 date 不支持纳秒（%N），无法按毫秒计时"
fi

SUITES=(
    "$FIXTURES/suite1.robot"
    "$FIXTURES/suite2.robot"
    "$FIXTURES/suite3.robot"
    "$FIXTURES/suite4.robot"
)
for f in "${SUITES[@]}"; do
    [[ -f "$f" ]] || fail_env "缺少压测 fixture：$f"
done

if [[ -z "$ARTIFACTS" || "$ARTIFACTS" == "/" ]]; then
    fail_env "产物根目录不合法：'$ARTIFACTS'"
fi
mkdir -p "$ARTIFACTS" 2>/dev/null || fail_env "产物根目录无法创建：$ARTIFACTS"
[[ -w "$ARTIFACTS" ]] || fail_env "产物根目录不可写：$ARTIFACTS"
# 归一化为绝对路径，供 clean_round_dir 做前缀比对
ARTIFACTS="$(cd "$ARTIFACTS" && pwd)"

echo "[bench] rounds=$ROUNDS"
echo "[bench] venv=$VENV"
echo "[bench] artifacts=$ARTIFACTS"
echo "[bench] fixtures=$FIXTURES（4 suite × 2 case，每条 Sleep 6s，理论串行 48s）"
echo "[bench] modes=serial（1 进程）/ p4（--processes 4）/ p2（--processes 2 对照）"
echo "[bench] env=TZ=Asia/Shanghai LANG=C.UTF-8 PYTHONUNBUFFERED=1 PATH 前置 venv"
echo ""

serial_ms=()
p4_ms=()
p2_ms=()
conflicts=0

# ---------- 主循环 ----------
for (( r = 1; r <= ROUNDS; r++ )); do
    rp="$(printf '%02d' "$r")"
    rdir="$ARTIFACTS/round-$rp"

    clean_round_dir "$rdir" || exit 1

    for mode in serial p4 p2; do
        outdir="$rdir/$mode"
        start="$(now_ms)"
        run_mode "$mode" "$outdir"
        rc=$?
        end="$(now_ms)"
        elapsed=$(( end - start ))

        if [[ "$rc" -ne 0 ]]; then
            fail_run "第 $rp 轮 mode=$mode 退出码 $rc（产物目录 $outdir）"
        fi
        [[ -s "$outdir/output.xml" ]] || fail_run "第 $rp 轮 mode=$mode 未产出 output.xml（产物目录 $outdir）"

        case "$mode" in
            serial) serial_ms+=("$elapsed") ;;
            p4) p4_ms+=("$elapsed") ;;
            p2) p2_ms+=("$elapsed") ;;
        esac

        echo "[bench] round=$rp mode=$mode elapsed_ms=$elapsed"

        if [[ "$mode" == "p4" ]]; then
            round_conflict="$(count_lease_conflicts "$outdir")"
            conflicts=$(( conflicts + round_conflict ))
            echo "[bench] round=$rp mode=p4 lease_conflict_hits=$round_conflict"
        fi
    done
done

# ---------- 汇总（只用实测值，不做任何估算） ----------
n="${#p4_ms[@]}"
if [[ "$n" -le 0 ]]; then
    fail_run "没有任何 p4 轮次数据"
fi

serial_avg="$(avg_of "$n" "${serial_ms[@]}")"
p4_avg="$(avg_of "$n" "${p4_ms[@]}")"
p2_avg="$(avg_of "${#p2_ms[@]}" "${p2_ms[@]}")"

if [[ "$serial_avg" == "N/A" || "$p4_avg" == "N/A" ]]; then
    fail_run "serial / p4 轮次数据缺失"
fi

speedup="$(awk -v a="$serial_avg" -v b="$p4_avg" 'BEGIN { if (b <= 0) print "N/A"; else printf "%.3f", a / b }')"
overhead="$(awk -v a="$serial_avg" -v b="$p4_avg" 'BEGIN { if (a <= 0) print "N/A"; else printf "%.2f%%", (b - a / 4) / (a / 4) * 100 }')"
worst_speedup="$(for (( i = 0; i < n; i++ )); do echo "${serial_ms[$i]} ${p4_ms[$i]}"; done \
    | awk '{ r = $1 / $2; if (NR == 1 || r < m) m = r } END { if (NR == 0) print "N/A"; else printf "%.3f", m }')"
p95="$(p95_of "$n" "${p4_ms[@]}")"

echo ""
echo "[bench] ================= 汇总（$n 轮） ================="
echo "[bench] serial_avg_ms=$serial_avg"
echo "[bench] p4_avg_ms=$p4_avg"
echo "[bench] p2_avg_ms=$p2_avg（对照，不参与门槛判定）"
echo "[bench] speedup=$speedup（serial_avg / p4_avg；门槛 ≥ 2.5）"
echo "[bench] worst_speedup=$worst_speedup（逐轮最小；门槛 ≥ 2.2）"
echo "[bench] conflict=$conflicts（p4 产物日志中租约冲突关键字命中行数；门槛 0）"
echo "[bench] overhead=$overhead（p4 相对理论 1/4 的额外耗时占比）"
echo "[bench] p95_ms=$p95（p4 逐轮耗时 P95）"
echo "[bench] artifacts=$ARTIFACTS（每轮产物在 round-NN/{serial,p4,p2}/）"
