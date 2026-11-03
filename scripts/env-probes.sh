#!/usr/bin/env bash
# ENV-05：环境八探针。作为 G1 的 S 类（可脚本化）检查项。
#
# 与 scripts/env-check.sh 的分工：
#   env-check.sh  —— 开发构建自检（含 DB 变量、产物根），跑在 WSL 里，面向「我现在能不能编译」
#   env-probes.sh —— 环境就绪探针（含 chrome / DUT 这类构建用不到但验收要用的），
#                    面向「这台机器能不能开始验收」，机器可读、退出码可判定
#
# 退出码：任一硬项 FAIL → 1；只有软项告警 → 0。
#
# 硬项按平台分角色 —— 不这样分，Windows 侧会永远红 4 项，探针就失去判定意义：
#   WSL      HARD: java mvn node mysql rf pabot   （构建 + 执行内核 + 数据库都在这）
#   Windows  HARD: java mvn node                  （只负责编辑与编译）
#            SOFT: mysql rf pabot chrome dut      （数据库/执行内核在 WSL 验，基线 §2 明写）
set -uo pipefail

STRICT_DUT=0
[[ "${1:-}" == "--strict-dut" ]] && STRICT_DUT=1

HARD_FAIL=0
SOFT_WARN=0
ROWS=()

IS_WSL=0
if [[ -n "${WSL_DISTRO_NAME:-}" ]] || grep -qi microsoft /proc/version 2>/dev/null; then
    IS_WSL=1
    PLATFORM="WSL"
else
    PLATFORM="Windows/Git-Bash"
fi

# 该探针在本平台是否属硬项：是 → FAIL，否 → WARN（"本平台不需要，去另一平台验"）
grade() {
    if (( IS_WSL )); then echo FAIL; else
        case "$1" in
            java|mvn|node) echo FAIL ;;
            *) echo WARN ;;
        esac
    fi
}

# 探针结果记一行：add 级别 名称 值 修复建议
add() {
    local level="$1" name="$2" value="$3" hint="${4:--}"
    case "$level" in
        FAIL) HARD_FAIL=$((HARD_FAIL + 1)) ;;
        WARN) SOFT_WARN=$((SOFT_WARN + 1)) ;;
    esac
    ROWS+=("$(printf '%-3s|%-8s|%-34s|%s' "$level" "$name" "$value" "$hint")")
}

ver_major() { echo "$1" | sed -E 's/^[^0-9]*([0-9]+).*/\1/'; }

# ---- 1. java（后端编译目标 21）----
if command -v java >/dev/null 2>&1; then
    jv=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+(\.[0-9]+)*).*/\1/')
    jm=$(ver_major "$jv")
    if [[ "$jm" =~ ^[0-9]+$ ]] && (( jm >= 21 )); then
        add OK java "java $jv"
    else
        add FAIL java "java $jv（需 >= 21）" "sudo apt install openjdk-21-jdk-headless"
    fi
else
    add FAIL java "未找到" "sudo apt install openjdk-21-jdk-headless"
fi

# ---- 2. mvn（3.9.x 为验证版本）----
if command -v mvn >/dev/null 2>&1; then
    mv=$(mvn -v 2>/dev/null | head -1 | sed -E 's/Apache Maven ([0-9.]+).*/\1/')
    case "$mv" in
        3.9.*) add OK mvn "Maven $mv" ;;
        *)     add WARN mvn "Maven $mv（验证版本为 3.9.x）" "构建脚本按 3.9 验证过，3.8 未验证" ;;
    esac
elif (( IS_WSL == 0 )); then
    # 注意：本会话的 PATH 是进程启动时的快照。若刚把 Maven 写入注册表 PATH，
    # 这里仍报「未找到」属正常——重开终端即可，不是环境缺失。
    add FAIL mvn "本会话未找到（PATH 为启动时快照）" "已写入注册表 PATH 的：重开终端；否则装 3.9.x 并加入 PATH"
else
    add FAIL mvn "未找到" "sudo apt install maven（或装 3.9.x 到 PATH）"
fi

# ---- 3. node（>= 22）----
if command -v node >/dev/null 2>&1; then
    nv=$(node -v | sed 's/^v//')
    nm=$(ver_major "$nv")
    if [[ "$nm" =~ ^[0-9]+$ ]] && (( nm >= 22 )); then
        add OK node "node $nv"
    else
        add FAIL node "node $nv（需 >= 22）" "装 Node 22+"
    fi
else
    add FAIL node "未找到" "装 Node 22+"
fi

# ---- 4. mysql（客户端 + 服务可达 + 版本口径）----
if ! command -v mysql >/dev/null 2>&1 && ! command -v mariadb >/dev/null 2>&1; then
    add "$(grade mysql)" mysql "本平台无 mysql/mariadb 客户端" "数据库在 WSL；Windows 侧不装也能构建"
else
    CLI=$(command -v mysql || command -v mariadb)
    DBH="${DB_HOST:-127.0.0.1}"; DBP="${DB_PORT:-3306}"
    DBU="${DB_USERNAME:-robot}"
    if [[ -n "${DB_PASSWORD:-}" ]]; then
        out=$("$CLI" -h"$DBH" -P"$DBP" -u"$DBU" -p"$DB_PASSWORD" -N -B -e "SELECT VERSION();" 2>/dev/null | head -1)
    else
        out=$("$CLI" -h"$DBH" -P"$DBP" -u"$DBU" -N -B -e "SELECT VERSION();" 2>/dev/null | head -1)
    fi
    if [[ -n "$out" ]]; then
        add OK mysql "$DBH:$DBP $out"
    elif (exec 3<>/dev/tcp/"$DBH"/"$DBP") 2>/dev/null; then
        exec 3<&- 2>/dev/null
        add WARN mysql "$DBH:$DBP 端口通，未验登录" "设 DB_USERNAME/DB_PASSWORD 后复探"
    else
        add "$(grade mysql)" mysql "$DBH:$DBP 不可达" "WSL 内：sudo systemctl start mysql"
    fi
fi

# ---- 5. rf（优先仓库约定的 ~/rf-stack/.venv）----
RF_BIN="$HOME/rf-stack/.venv/bin/robot"
if [[ -x "$RF_BIN" ]]; then
    add OK rf "RF $("$RF_BIN" --version 2>&1 | head -1 | sed 's/^Robot Framework //')（~/rf-stack/.venv）"
elif command -v robot >/dev/null 2>&1; then
    add WARN rf "RF $(robot --version 2>&1 | head -1 | sed 's/^Robot Framework //')（非 ~/rf-stack/.venv）" "口径应在 ~/rf-stack/.venv"
else
    add "$(grade rf)" rf "本平台未找到" "执行内核在 WSL：bash scripts/env-setup-rf.sh"
fi

# ---- 6. pabot（EXE-03 / EXE-07 依赖）----
# pabot --version 是 6 行组合输出（尾部还会打印一行 RF 版本），必须精确取版本行
pabot_ver() {
    "$@" --version 2>&1 | grep -m1 '^Version'
}
if [[ -x "$HOME/rf-stack/.venv/bin/pabot" ]]; then
    add OK pabot "$(pabot_ver "$HOME/rf-stack/.venv/bin/pabot")（~/rf-stack/.venv）"
elif command -v pabot >/dev/null 2>&1; then
    add WARN pabot "$(pabot_ver pabot)（非 ~/rf-stack/.venv）" "口径应在 ~/rf-stack/.venv"
else
    add "$(grade pabot)" pabot "本平台未找到" "执行内核在 WSL：bash scripts/env-setup-rf.sh"
fi

# ---- 7. chrome（RPT-05 PDF 打印链路才需要，软项）----
# 两种前缀都要试：Git Bash 用 /c/，WSL 用 /mnt/c/。同一个 Windows Chrome，
# 在 WSL 里调 headless 打印也可以（RPT-05 若走服务端打印，依赖这一项）。
CHROME=""
for c in \
    "/c/Program Files/Google/Chrome/Application/chrome.exe" \
    "/mnt/c/Program Files/Google/Chrome/Application/chrome.exe" \
    "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
    "/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"; do
    [[ -x "$c" ]] && CHROME="$c" && break
done
if [[ -z "$CHROME" ]]; then
    for c in google-chrome google-chrome-stable chromium chromium-browser; do
        command -v "$c" >/dev/null 2>&1 && CHROME="$(command -v "$c")" && break
    done
fi
if [[ -n "$CHROME" ]]; then
    add OK chrome "$(basename "$CHROME")"
else
    add WARN chrome "未找到" "RPT-05 PDF 打印链路需要；不影响构建"
fi

# ---- 8. DUT（EXE-08 实网才需要，软项；--strict-dut 转硬）----
probe_tcp() { (exec 3<>/dev/tcp/"$1"/"$2") 2>/dev/null && { exec 3<&-; return 0; } || return 1; }
if [[ -n "${DUT_A:-}" || -n "${DUT_B:-}" ]]; then
    for pair in "A:${DUT_A:-}" "B:${DUT_B:-}"; do
        label="${pair%%:*}"; host="${pair#*:}"
        [[ -z "$host" ]] && continue
        if probe_tcp "$host" 22; then
            add OK dut "$label $host:22 可达"
        elif (( STRICT_DUT )); then
            add FAIL dut "$label $host:22 不可达" "查设备电源 / 网段 / 防火墙"
        else
            add WARN dut "$label $host:22 不可达" "设备未在线；加 --strict-dut 可判失败"
        fi
    done
else
    add SKIP dut "未提供 DUT_A / DUT_B" "需要时：DUT_A=... DUT_B=... bash scripts/env-probes.sh"
fi

# ---- 输出 ----
echo "环境八探针 @ $(hostname 2>/dev/null || echo unknown) / $PLATFORM / $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo "---------------------------------------------------------------------------------------------------"
printf '%-3s|%-8s|%-34s|%s\n' "级" "探针" "实测值" "修复建议"
echo "---------------------------------------------------------------------------------------------------"
for r in "${ROWS[@]}"; do
    IFS='|' read -r lv nm va hi <<<"$r"
    printf '%-3s|%-8s|%-34s|%s\n' "$lv" "$nm" "$va" "$hi"
done
echo "---------------------------------------------------------------------------------------------------"

# 红项单独再列一遍：表格里看得见，但结尾这一句是给「一眼判断」用的
if (( HARD_FAIL > 0 )); then
    echo "[结果] FAIL：硬项 $HARD_FAIL 个不满足，软项告警 $SOFT_WARN 个"
    for r in "${ROWS[@]}"; do
        [[ "${r:0:4}" == "FAIL" ]] && echo "  红：$(echo "$r" | cut -d'|' -f2) — $(echo "$r" | cut -d'|' -f3)"
    done
    exit 1
fi
echo "[结果] PASS：硬项全绿，软项告警 $SOFT_WARN 个"
exit 0
