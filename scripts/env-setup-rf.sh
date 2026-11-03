#!/usr/bin/env bash
# ENV-01：RF / pabot 运行时原地重建。
#
# 为什么要脚本而不是「拷一份 venv」：venv 里 bin/robot 的 shebang 是绝对路径，
# 拷贝或移动后一律 bad interpreter。换机只能原地重建。
#
# 用法：
#   bash scripts/env-setup-rf.sh              # 装到 ~/rf-stack/.venv
#   RF_HOME=/opt/rf bash scripts/env-setup-rf.sh
#
# 做的事：
#   1. 检查 python3（>=3.10）与 venv 模块
#   2. 建 venv（已存在则复用，不删——删了会打断正在跑的执行）
#   3. 按 fixtures/rf-requirements.txt 装依赖（版本固定）
#   4. 把 venv/bin 前置进 ~/.bashrc（幂等，只加一次）
#   5. 就地验证：robot / pabot / SSHLibrary 三项
set -euo pipefail

RF_HOME="${RF_HOME:-$HOME/rf-stack}"
VENV="$RF_HOME/.venv"
INDEX="${PIP_INDEX:-https://pypi.tuna.tsinghua.edu.cn/simple}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQ="$ROOT/fixtures/rf-requirements.txt"

say() { echo "[rf] $*"; }

# ---- 1. python3 与 venv ----
command -v python3 >/dev/null 2>&1 || { echo "[rf][FAIL] 无 python3"; exit 1; }
PYV=$(python3 -c 'import sys;print("%d.%d"%sys.version_info[:2])')
say "python3 $PYV"
if ! python3 -m venv --help >/dev/null 2>&1; then
    echo "[rf][FAIL] 缺 venv 模块，先装：sudo apt install python3.${PYV#*.}-venv"
    exit 1
fi

# ---- 2. venv ----
mkdir -p "$RF_HOME"
# 只看 bin/python 在不在不够：建 venv 时若 ensurepip 失败，会留下「有 python、没 pip」
# 的半成品目录（本机就遇到过一次）。这种目录必须删掉重建，否则后面 pip 直接报
# "No module named pip"，看着像依赖问题，实际是 venv 建坏了。
if [[ -x "$VENV/bin/python" ]] && "$VENV/bin/python" -m pip --version >/dev/null 2>&1; then
    say "复用已有 venv：$VENV"
else
    if [[ -d "$VENV" ]]; then
        say "已有 venv 不完整（缺 pip），删除重建：$VENV"
        rm -rf "$VENV"
    else
        say "创建 venv：$VENV"
    fi
    python3 -m venv "$VENV"
    "$VENV/bin/python" -m pip --version >/dev/null 2>&1 || {
        echo "[rf][FAIL] venv 建好但 pip 不可用，检查 python3-venv 是否装全"
        exit 1
    }
fi

# ---- 3. 依赖 ----
[[ -f "$REQ" ]] || { echo "[rf][FAIL] 缺依赖清单 $REQ"; exit 1; }
say "安装依赖（源：$INDEX）"
"$VENV/bin/python" -m pip install -q --upgrade pip -i "$INDEX"
"$VENV/bin/python" -m pip install -q -i "$INDEX" -r "$REQ"

# ---- 4. PATH 前置（幂等）----
MARK="# >>> robot-autotest rf-stack >>>"
if ! grep -qF "$MARK" "$HOME/.bashrc" 2>/dev/null; then
    {
        echo ""
        echo "$MARK"
        echo "export PATH=\"$VENV/bin:\$PATH\""
        echo "# <<< robot-autotest rf-stack <<<"
    } >>"$HOME/.bashrc"
    say "已把 $VENV/bin 前置进 ~/.bashrc（新开 shell 生效）"
else
    say "~/.bashrc 已有 rf-stack 段，跳过"
fi

# ---- 5. 就地验证 ----
# 注意：不靠退出码判成败。RF 7.5.1 的 `robot --version` 输出正常但退出码是 251，
# 配合 `set -o pipefail` 会让 set -e 在管道处直接退出（连后面几行都不打印）。
# 所以这里先关掉 set -e，一律按输出内容判定。
set +e
fail=0
v=$("$VENV/bin/robot" --version 2>&1 | head -1)
if [[ "$v" == *"Robot Framework"* ]]; then say "robot     : $v"; else say "robot     : 失败（$v）"; fail=1; fi

# pabot --version 是 6 行组合输出（尾部还会打印一行 RF 版本），取最后一行会取错，
# 必须精确匹配以 "Version" 开头的那行（本机实测）。
v=$("$VENV/bin/pabot" --version 2>&1 | grep -m1 '^Version')
if [[ -n "$v" ]]; then say "pabot     : $v"; else say "pabot     : 失败（未输出版本行）"; fail=1; fi

v=$("$VENV/bin/python" -c 'import SSHLibrary;print(SSHLibrary.__version__)' 2>&1)
if [[ "$v" =~ ^[0-9] ]]; then say "SSHLibrary: $v"; else say "SSHLibrary: 失败（$v）"; fail=1; fi

# 顺带验证「裸名 robot 可被 pabot 子进程拉起」—— pabot 用 PATH 找 robot，
# 不是用 sys.executable 的兄弟路径；PATH 没前置时子进程会静默全失败。
if PATH="$VENV/bin:$PATH" command -v robot >/dev/null 2>&1; then
    say "PATH 检查: robot 可在未加绝对路径时被解析（pabot 子进程依赖此项）"
else
    say "PATH 检查: 失败 —— pabot 子进程会找不到 robot"
    fail=1
fi

if (( fail )); then
    echo "[rf][FAIL] 运行时未就绪"
    exit 1
fi
echo "[rf] OK：$VENV"
echo "[rf] 复探：bash scripts/env-probes.sh"
