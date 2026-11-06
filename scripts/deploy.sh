#!/usr/bin/env bash
# ENVX-01 / ENVX-02（FR-v2.0-55）：一条命令「构建 + 部署 + 健康检查」。
#
#   bash scripts/deploy.sh                 # 完整部署（会停掉本脚本上次启动的进程）
#   bash scripts/deploy.sh --no-serve     # 只构建，不启停进程（CI / 旁观时用）
#
# 口令只从 ~/.config/robot-autotest/env 读，不写死、不打印。
# 代码回滚走 scripts/rollback.sh（HRD-04 三步），本脚本只负责「部署当前版本」。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p var logs

NO_SERVE=0
# 不能写 `[[ ... ]] && X=1`：set -e 下条件为假时整个 && 列表返回 1，脚本会静默退出
if [[ "${1:-}" == "--no-serve" ]]; then
  NO_SERVE=1
fi

ENV_FILE="${ROBOT_ENV_FILE:-$HOME/.config/robot-autotest/env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

echo "[deploy] 1/5 生成构建信息（关于页的版本 / commit / 依赖）"
python3 scripts/gen_build_info.py
cp CHANGELOG.md frontend/public/CHANGELOG.md

echo "[deploy] 2/5 构建后端 jar 与前端 dist"
bash scripts/build.sh

JAR="$(ls -1 backend/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
[[ -n "$JAR" ]] || { echo "[deploy] FAIL 没找到后端 jar"; exit 1; }
[[ -f frontend/dist/index.html ]] || { echo "[deploy] FAIL 没找到前端 dist"; exit 1; }

if [[ "$NO_SERVE" == "1" ]]; then
  echo "[deploy] --no-serve：构建完成，未启停任何进程"
  exit 0
fi

: "${DB_PASSWORD:?缺少 DB_PASSWORD，请在 $ENV_FILE 配置}"

stop_by_pidfile() {
  local file="$1"
  if [[ -f "$file" ]]; then
    local pid
    pid="$(cat "$file")"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[deploy] 停旧进程 $pid ($file)"
      kill "$pid" 2>/dev/null || true
      sleep 2
    fi
    rm -f "$file"
  fi
}

echo "[deploy] 3/5 停旧进程（只停本脚本 pid 文件记录的进程）"
stop_by_pidfile var/backend.pid
stop_by_pidfile var/entry.pid

echo "[deploy] 4/5 启动后端 :8081 与统一入口 :8001"
nohup java -jar "$JAR" --spring.profiles.active="${SPRING_PROFILE:-dev}" \
  > logs/backend.log 2>&1 &
echo $! > var/backend.pid
nohup node scripts/serve-entry.mjs > logs/entry.log 2>&1 &
echo $! > var/entry.pid

echo "[deploy] 5/5 健康检查（最多 60 秒）"
ok=0
for _ in $(seq 1 60); do
  if curl -fsS --noproxy '*' http://127.0.0.1:8001/healthz >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 1
done
[[ "$ok" == "1" ]] || { echo "[deploy] FAIL 入口健康检查超时，看 logs/entry.log"; exit 1; }

echo "[deploy] /healthz    $(curl -fsS --noproxy '*' http://127.0.0.1:8001/healthz)"
echo "[deploy] /api/health $(curl -fsS --noproxy '*' http://127.0.0.1:8001/api/health)"
echo "[deploy] 首页        $(curl -s -o /dev/null -w '%{http_code}' --noproxy '*' http://127.0.0.1:8001/)"
echo "[deploy] 深链接      $(curl -s -o /dev/null -w '%{http_code}' --noproxy '*' http://127.0.0.1:8001/settings)"
echo "[deploy] PASS 访问 http://127.0.0.1:8001"
