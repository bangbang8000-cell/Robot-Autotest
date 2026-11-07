#!/usr/bin/env bash
# A3 复跑：真实 RF/pabot 命令取证（bench 4suite-2cases + legacy 17 TN-IMP）
# 判据：master(真实 RobotKernel.command) → 原样执行 → agent 留痕 → kernel_cmd_diff 0 行；
#       真实 output.xml 被 OutputXmlNormalizer 解析条数正确；--dryrun 双判无 [ ERROR ]。
set -u

ROOT="$HOME/robot-autotest"
EV="$ROOT/docs/evidence/v2.0/a3-rf-kernel"
RAW="$EV/raw"
RUN_ID="A3-RF-$(date +%Y%m%d_%H%M%S)"
CP="$ROOT/backend/target/classes:$(cat /tmp/backend-cp.txt)"

rm -rf "$RAW" /tmp/a3classes
mkdir -p "$RAW/artifacts" /tmp/a3classes

echo "== A3 $RUN_ID =="
javac -cp "$CP" -d /tmp/a3classes "$EV/CmdDump.java" || exit 1
echo "[1/5] 真实 RobotKernel.command → master.json"
java -cp "$CP:/tmp/a3classes" CmdDump build "$RAW/artifacts" "$RUN_ID" "a3-kernel" 2 \
  fixtures/bench/4suite-2cases/suite1.robot \
  fixtures/bench/4suite-2cases/suite2.robot \
  fixtures/bench/4suite-2cases/suite3.robot \
  fixtures/bench/4suite-2cases/suite4.robot > "$RAW/master.json" 2>"$RAW/master.err"
cat "$RAW/master.json"

echo "[2/5] 原样执行 master argv（不改造）"
python3 "$EV/run_master.py" "$RAW/master.json" "$RAW/agent.json" \
  "$RAW/stdout.log" "$RAW/stderr.log"
echo "  pabot exit=$?"

echo "[3/5] kernel_cmd_diff（必须 0 行差异）"
python3 "$ROOT/tools/kernel_cmd_diff.py" "$RAW/master.json" "$RAW/agent.json" \
  > "$RAW/diff.log" 2>&1
echo "  diff exit=$?"
cat "$RAW/diff.log"

echo "[4/5] 真实 output.xml → OutputXmlNormalizer 解析"
java -cp "$CP:/tmp/a3classes" CmdDump parse "$RAW/artifacts/shard-1/output.xml" "$RUN_ID" \
  > "$RAW/parse.log" 2>&1
echo "  parse exit=$?"
cat "$RAW/parse.log"

echo "[5/5] legacy 17 TN-IMP：同一内核命令 + --dryrun 双判"
java -cp "$CP:/tmp/a3classes" CmdDump build "$RAW/legacy-artifacts" "$RUN_ID" "legacy-kernel" 1 \
  fixtures/roundtrip/legacy > "$RAW/legacy-master.json" 2>"$RAW/legacy-master.err"
cat "$RAW/legacy-master.json"
python3 "$EV/run_master.py" "$RAW/legacy-master.json" "$RAW/legacy-agent.json" \
  "$RAW/legacy-stdout.log" "$RAW/legacy-stderr.log"
echo "  legacy pabot exit=$?"
python3 "$ROOT/tools/kernel_cmd_diff.py" "$RAW/legacy-master.json" "$RAW/legacy-agent.json" \
  > "$RAW/legacy-diff.log" 2>&1
echo "  legacy diff exit=$?"; cat "$RAW/legacy-diff.log"
java -cp "$CP:/tmp/a3classes" CmdDump parse "$RAW/legacy-artifacts/shard-1/output.xml" "$RUN_ID" \
  > "$RAW/legacy-parse.log" 2>&1
echo "  legacy parse exit=$?"; head -4 "$RAW/legacy-parse.log"

for round in 1 2; do
  python3 - "$RAW/master.json" "$RAW/dryrun-$round" "$round" <<'PY'
import json, os, pathlib, subprocess, sys
master, outdir, round_no = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
payload = json.loads(master.read_text(encoding="utf-8"))
argv = list(payload["argv"])
argv.insert(1, "--dryrun")
idx = argv.index("--outputdir") + 1
argv[idx] = str(outdir / "artifacts" / "shard-1")
outdir.mkdir(parents=True, exist_ok=True)
home = pathlib.Path.home()
env = dict(os.environ)
env["PATH"] = str(home / "rf-stack" / ".venv" / "bin") + os.pathsep + env.get("PATH", "")
env["TZ"] = "Asia/Shanghai"; env["LANG"] = "C.UTF-8"; env["PYTHONUNBUFFERED"] = "1"
with (outdir / "combined.log").open("wb") as log:
    proc = subprocess.run(argv, cwd=str(home / "robot-autotest"), env=env,
                          stdout=log, stderr=subprocess.STDOUT, timeout=600)
print(f"round={round_no} exit={proc.returncode}")
PY
  grep -c '\[ ERROR \]' "$RAW/dryrun-$round/combined.log" > "$RAW/dryrun-$round/error-count.txt" 2>/dev/null || true
  echo "  round=$round [ ERROR ] 次数=$(cat "$RAW/dryrun-$round/error-count.txt")"
  grep -c '[0-9] passed,[0-9] failed' "$RAW/dryrun-$round/combined.log" > "$RAW/dryrun-$round/summary-lines.txt" 2>/dev/null || true
  tail -2 "$RAW/dryrun-$round/combined.log"
done

echo "== 产物 =="
ls -l "$RAW" "$RAW/artifacts/shard-1" "$RAW/legacy-artifacts/shard-1"
