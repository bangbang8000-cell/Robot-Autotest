#!/usr/bin/env bash
# A5 一条命令复跑：真实浏览器 13 条 TC + 图像分析
# 前置：Chrome 以 --remote-debugging-port=9222 运行；应用 8001 已起；env 内有 QA_E2E_PASSWORD
set -uo pipefail
cd "$(dirname "$0")"

node run.mjs
RC=$?

PY=${PYTHON:-$HOME/rf-stack/.venv/bin/python}
[ -x "$PY" ] || PY=python3
"$PY" analyze.py
ARC=$?

echo "run.mjs exit=$RC  analyze.py exit=$ARC"
exit $(( RC != 0 || ARC != 0 ))
