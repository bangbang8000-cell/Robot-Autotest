#!/usr/bin/env bash
# A2 复跑脚本：真实 pytest 内核取证（deepseek-harness 实测 + 六态样本 + hermes 规模样本）
# 用法: bash run.sh
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$HOME/venvs/deepseek-harness"
SAMPLE="$HOME/deepseek-harness"
HERMES="$HOME/.hermes/hermes-agent"
RAW="$HERE/raw"
STATES_RAW="$HERE/states/raw"

rm -rf "$RAW" "$STATES_RAW"
mkdir -p "$RAW" "$STATES_RAW"

echo "== A2 $(date -Is) =="
echo "venv: $VENV"
"$VENV/bin/python" --version
"$VENV/bin/python" -m pytest --version
"$VENV/bin/pip" list --format=freeze > "$RAW/pip-freeze.log"

export PYTHONPATH="$SAMPLE/python/sdk/src:$SAMPLE/python/sdk-runtime/src"

cd "$SAMPLE" || exit 1

echo "[1/4] collect-only -q"
timeout 120 "$VENV/bin/python" -m pytest --collect-only -q > "$RAW/collect-only.log" 2>&1
echo "  exit=$? ; tail:"; tail -2 "$RAW/collect-only.log"

echo "[2/4] 真实执行（与 PytestKernel.command 同参数）"
timeout 300 "$VENV/bin/python" -m pytest \
  --json-report --json-report-file="$RAW/pytest-report.json" \
  --junitxml="$RAW/junit.xml" -q > "$RAW/full-run.log" 2>&1
echo "  exit=$? ; tail:"; tail -2 "$RAW/full-run.log"

echo "[3/4] collect-only vs json-report 逐条对齐"
"$VENV/bin/python" "$HERE/compare.py" "$RAW/collect-only.log" "$RAW/pytest-report.json" \
  > "$RAW/compare.txt" 2>&1
echo "  compare exit=$?"; cat "$RAW/compare.txt"

echo "[4/4] 六态样本真实执行"
cd "$HERE/states" || exit 1
timeout 60 "$VENV/bin/python" -m pytest -p no:cacheprovider \
  --json-report --json-report-file="$STATES_RAW/pytest-report.json" \
  --junitxml="$STATES_RAW/junit.xml" -q > "$STATES_RAW/run.log" 2>&1
echo "  exit=$? ; tail:"; tail -3 "$STATES_RAW/run.log"

if [ -d "$HERMES" ] && [ "${SKIP_SCALE:-0}" != "1" ]; then
  echo "[scale] hermes-agent 依赖缺口扫描"
  "$VENV/bin/python" "$HERE/hermes_scan.py" > "$RAW/hermes-missing-deps.log" 2>&1 || true
  echo "  lines=$(wc -l < "$RAW/hermes-missing-deps.log")"
  echo "[scale] 全仓 collect-only（480s 上限，超时如实记录、不推测条数）"
  cd "$HERMES" || exit 1
  timeout 480 "$VENV/bin/python" -m pytest --collect-only -q -p no:cacheprovider \
    --continue-on-collection-errors > "$RAW/hermes-collect.log" 2>&1
  echo "  exit=$?"; tail -2 "$RAW/hermes-collect.log"
  echo "[scale] 有界子集 collect（tests/ 前 200 个 test_*.py）"
  FILES=$(find tests -name 'test_*.py' | head -200)
  timeout 300 "$VENV/bin/python" -m pytest --collect-only -q -p no:cacheprovider \
    --continue-on-collection-errors $FILES > "$RAW/hermes-subset-collect.log" 2>&1
  echo "  exit=$?"; tail -1 "$RAW/hermes-subset-collect.log"
fi

echo "== 原始产物 =="
ls -l "$RAW" "$STATES_RAW"
