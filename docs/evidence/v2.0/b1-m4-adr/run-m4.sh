#!/usr/bin/env bash
# B1 / M4 可复跑脚本：pytest × 单项目 × 并行（ADR-0007 定案：xdist -n N）
#
# 判据：并行结果与串行逐条 nodeid 一致（不重复、不丢），json-report 由 xdist master 聚合。
# 用法：bash run-m4.sh [workers]
set -u
WORKERS="${1:-4}"
VENV="$HOME/venvs/deepseek-harness/bin/python"
PROJ="$HOME/deepseek-harness"
EV="$(cd "$(dirname "$0")" && pwd)"
RAW="$EV/raw"
mkdir -p "$RAW"
export PYTHONPATH="$PROJ/python/sdk/src:$PROJ/python/sdk-runtime/src"
cd "$PROJ" || exit 9

run_once() { # $1=tag $2=extra args...
  local tag="$1"; shift
  local t0 t1 rc
  t0=$(date +%s%3N)
  timeout 300 "$VENV" -m pytest --json-report --json-report-file="$RAW/m4-$tag-report.json" \
    --junitxml="$RAW/m4-$tag-junit.xml" -q "$@" > "$RAW/m4-$tag.log" 2>&1
  rc=$?
  t1=$(date +%s%3N)
  echo "$tag rc=$rc wall_ms=$((t1-t0))" >> "$RAW/m4-timing.txt"
  tail -1 "$RAW/m4-$tag.log" | sed "s/^/[$tag] /"
}

: > "$RAW/m4-timing.txt"
echo "== round 1: serial =="
run_once serial-r1
echo "== round 1: xdist -n $WORKERS =="
run_once xdist-r1 -n "$WORKERS"
echo "== round 2: serial =="
run_once serial-r2
echo "== round 2: xdist -n $WORKERS =="
run_once xdist-r2 -n "$WORKERS"

"$VENV" - "$RAW" "$WORKERS" <<'PY' | tee "$RAW/m4-compare.txt"
import json, sys, pathlib
raw = pathlib.Path(sys.argv[1]); workers = sys.argv[2]

def load(tag):
    d = json.loads((raw / f"m4-{tag}-report.json").read_text())
    return [t.get("nodeid") for t in d.get("tests", [])], d.get("summary", {})

res = {}
for tag in ("serial-r1", "xdist-r1", "serial-r2", "xdist-r2"):
    nodes, summary = load(tag)
    res[tag] = nodes
    print(f"{tag:9s} total={summary.get('total')} passed={summary.get('passed')} "
          f"failed={summary.get('failed')} skipped={summary.get('skipped')} "
          f"nodes={len(nodes)} unique={len(set(nodes))} dups={len(nodes)-len(set(nodes))}")

base = set(res["serial-r1"])
ok = True
for tag in ("xdist-r1", "serial-r2", "xdist-r2"):
    only_base = sorted(base - set(res[tag]))
    only_tag = sorted(set(res[tag]) - base)
    same = base == set(res[tag])
    ok = ok and same
    print(f"{tag:9s} vs serial-r1: identical_set={same} lost={len(only_base)} extra={len(only_tag)}")
    if only_base: print("  lost sample:", only_base[:3])
    if only_tag: print("  extra sample:", only_tag[:3])

times = {}
for line in (raw / "m4-timing.txt").read_text().splitlines():
    parts = dict(p.split("=") for p in line.split()[1:])
    times[line.split()[0]] = int(parts["wall_ms"])
for tag, ms in times.items():
    print(f"wall {tag:9s} = {ms} ms")
s = (times["serial-r1"] + times["serial-r2"]) / 2
x = (times["xdist-r1"] + times["xdist-r2"]) / 2
print(f"avg serial={s:.0f} ms  avg xdist(-n {workers})={x:.0f} ms  speedup={s/x:.2f}x")
print("RESULT:", "PASS (不重复不丢)" if ok else "FAIL")
sys.exit(0 if ok else 1)
PY
