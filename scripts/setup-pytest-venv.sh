#!/usr/bin/env bash
# 建立 pytest 专用 venv（C4 · D2 案 A）。
#
# 为什么需要这个脚本：config/pytest-venv.yml 的 `venv.created_by` 指向本文件，
# 而 v2.0 期间该文件从未存在，导致 ~/venvs/pytest-kernel 一直是空的，
# PytestKernel / PytestImportAdapter 因此从未在真实 pytest 上跑过（见 2026-11-06 评估文档）。
#
# 口径（与 config/pytest-venv.yml 严格一致）：
#   1. 与 RF 运行时 ~/rf-stack/.venv **隔离**，不复用、不互装插件；
#   2. 只装 config 里 allow 列的插件，deny 列的（执行宿主命令 / 联网）一律不装；
#   3. 导入期不联网、不装包（network_at_import / pip_install_at_import 均为 false）。
#
# 用法：
#   bash scripts/setup-pytest-venv.sh            # 幂等：已存在则只校验并补齐
#   bash scripts/setup-pytest-venv.sh --recreate # 删掉重建
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="${REPO_ROOT}/config/pytest-venv.yml"
RECREATE=0
for arg in "$@"; do
    case "${arg}" in
        --recreate) RECREATE=1 ;;
        -h|--help) sed -n '2,16p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "未知参数: ${arg}" >&2; exit 2 ;;
    esac
done

if [[ ! -f "${CONFIG}" ]]; then
    echo "[FAIL] 找不到 ${CONFIG}" >&2
    exit 2
fi

# 只解析本脚本用到的三个键，不引 PyYAML 依赖。
CONF_LINE="$(python3 - "${CONFIG}" <<'PY'
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
path = re.search(r'^\s*path:\s*"?([^"\n]+)"?\s*$', text, re.M)
python_min = re.search(r'^\s*python:\s*"?([^"\n]+)"?\s*$', text, re.M)
block = re.search(r'^\s*allow:\s*$\n((?:[ \t]*-[ \t]*\S.*\n)+)', text, re.M)

if not path:
    sys.exit("config 缺少 venv.path")
if not block:
    sys.exit("config 缺少 plugins.allow")

names = [line.strip().lstrip("-").strip()
         for line in block.group(1).splitlines() if line.strip()]
if not names:
    sys.exit("config 的 plugins.allow 为空")

print("\t".join([path.group(1).strip(),
                 python_min.group(1).strip() if python_min else ">=3.10",
                 " ".join(names)]))
PY
)"

VENV_PATH="$(printf '%s' "${CONF_LINE}" | cut -f1)"
PY_MIN="$(printf '%s' "${CONF_LINE}" | cut -f2)"
PLUGINS="$(printf '%s' "${CONF_LINE}" | cut -f3)"
VENV_DIR="${VENV_PATH/#\~/${HOME}}"

echo "[1/5] 配置真源: ${CONFIG}"
echo "      venv 路径: ${VENV_DIR}"
echo "      python  : ${PY_MIN}"
echo "      白名单  : ${PLUGINS}"

# --- 2. 解释器版本 ---------------------------------------------------------
PY_BIN="${PYTHON_BIN:-python3}"
command -v "${PY_BIN}" >/dev/null 2>&1 || { echo "[FAIL] 找不到解释器 ${PY_BIN}" >&2; exit 2; }
PY_VER="$("${PY_BIN}" -c 'import sys; print("%d.%d.%d" % sys.version_info[:3])')"
"${PY_BIN}" -c 'import sys; sys.exit(0 if sys.version_info[:2] >= (3, 10) else 1)' \
    || { echo "[FAIL] 需要 Python >= 3.10，当前 ${PY_VER}" >&2; exit 2; }
echo "[2/5] 解释器 ${PY_BIN} = ${PY_VER}"

# --- 3. 创建 venv ----------------------------------------------------------
if [[ -d "${VENV_DIR}" && ${RECREATE} -eq 1 ]]; then
    echo "      已存在且指定 --recreate，先删除"
    rm -rf "${VENV_DIR}"
fi
if [[ ! -x "${VENV_DIR}/bin/python" ]]; then
    echo "[3/5] 创建 venv"
    mkdir -p "$(dirname "${VENV_DIR}")"
    "${PY_BIN}" -m venv "${VENV_DIR}"
else
    echo "[3/5] venv 已存在，跳过创建（随后按白名单补齐）"
fi

VENV_PY="${VENV_DIR}/bin/python"

# --- 4. 安装白名单插件 -----------------------------------------------------
echo "[4/5] 安装白名单插件"
"${VENV_PY}" -m pip install --quiet --upgrade pip
# shellcheck disable=SC2086  # PLUGINS 需要按空格拆成多个参数
"${VENV_PY}" -m pip install --quiet ${PLUGINS}

# --- 5. 自检 ---------------------------------------------------------------
echo "[5/5] 自检"
"${VENV_PY}" - <<'PY'
import importlib
import sys
from importlib import metadata

failures = []
versions = {}
for module, dist in (("pytest", "pytest"),
                     ("pytest_jsonreport", "pytest-json-report"),
                     ("xdist", "pytest-xdist")):
    try:
        importlib.import_module(module)
        versions[dist] = metadata.version(dist)
    except Exception as exc:  # noqa: BLE001 - 自检要打印真实原因
        failures.append(f"{dist}: {exc}")

if failures:
    print("[FAIL] 导入失败：" + "; ".join(failures))
    sys.exit(1)

print("python            " + sys.version.split()[0])
for dist, version in versions.items():
    print(f"{dist:<18}{version}")
PY

# 「能 import」不等于「插件已注册」：--help 里没有 --json-report 就是没生效。
# 注意不能用 `pytest --help | grep -q`：本脚本开了 pipefail，grep 命中即退出会让
# pytest 吃到 SIGPIPE，管道整体返回 141，自检会假失败。
HELP_TEXT="$("${VENV_PY}" -m pytest --help 2>/dev/null || true)"
case "${HELP_TEXT}" in
    *--json-report*) : ;;
    *) echo "[FAIL] pytest 未注册 --json-report，插件没生效" >&2; exit 1 ;;
esac

echo "[OK] pytest 运行时就绪: ${VENV_DIR}"
echo "     跑法: ${VENV_PY} -m pytest <路径> -q"
