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

echo "[deploy] 3/5 停旧进程（systemd unit + pid 文件 + 端口兜底）"
# 2026-11-06 实测：本地旁观实例由 systemd --user 托管（enable-linger），
# 不再 nohup —— nohup 起的进程随 wsl.exe 会话结束被 SIGTERM 杀掉，入口会静默消失。
stop_unit() {
  local unit="$1"
  if systemctl --user cat "$unit" >/dev/null 2>&1; then
    echo "[deploy] 停 unit $unit"
    systemctl --user stop "$unit" 2>/dev/null || true
  fi
}
stop_unit robot-autotest-entry.service
stop_unit robot-autotest-backend.service
stop_by_pidfile var/backend.pid
stop_by_pidfile var/entry.pid

# 2026-11-06 实测缺陷：W1 期间手工起过一次后端/入口，没写 pid 文件。
# 之后跑 deploy.sh 时新 jar 因 8081 被占用直接启动失败，但 8001 上旧入口还活着，
# /healthz 与 /api/health 照样 200 —— 部署脚本报 PASS，新接口却全是 404。
# 只认本仓库的进程特征，不误杀别人占用的端口。
stop_by_port() {
  local port="$1" pattern="$2" pid
  local pids
  pids="$(ss -ltnpH "sport = :${port}" 2>/dev/null \
    | grep -o 'pid=[0-9]*' | cut -d= -f2 | sort -u || true)"
  for pid in $pids; do
    if [[ -n "$pid" ]] && ps -p "$pid" -o args= | grep -q -- "$pattern"; then
      echo "[deploy] 停端口 ${port} 上的遗留进程 ${pid}（pid 文件缺失/失效）"
      kill "$pid" 2>/dev/null || true
    fi
  done
}

stop_by_port 8081 "backend/target/"
stop_by_port 8001 "serve-entry.mjs"

# 端口释放是异步的，不等就启动会复现上面的假绿
for port in 8081 8001; do
  for _ in $(seq 1 20); do
    if ! ss -ltnH "sport = :${port}" 2>/dev/null | grep -q .; then
      break
    fi
    sleep 1
  done
  if ss -ltnH "sport = :${port}" 2>/dev/null | grep -q .; then
    echo "[deploy] FAIL 端口 ${port} 仍被占用，拒绝继续（避免旧进程假绿）"
    exit 1
  fi
done

echo "[deploy] 4/5 启动后端 :8081 与统一入口 :8001"
# 托管口径：有 systemd --user unit 就交给 unit（常驻、可 restart、开机自起）；
# 没有 unit（新克隆未安装）才退回 nohup，并在输出里明示「会话结束会停」。
if systemctl --user cat robot-autotest-backend.service >/dev/null 2>&1 \
   && systemctl --user cat robot-autotest-entry.service >/dev/null 2>&1; then
  echo "[deploy] 托管方式：systemd --user（robot-autotest-backend / robot-autotest-entry）"
  systemctl --user start robot-autotest-backend.service
  for _ in $(seq 1 60); do
    ss -ltnH "sport = :8081" 2>/dev/null | grep -q . && break
    sleep 1
  done
  ss -ltnH "sport = :8081" 2>/dev/null | grep -q . \
    || { echo "[deploy] FAIL 后端 8081 未监听，看 journalctl --user -u robot-autotest-backend"; exit 1; }
  systemctl --user start robot-autotest-entry.service
else
  echo "[deploy] 托管方式：nohup（未安装 systemd unit，wsl.exe 会话结束会停）"
  nohup java -jar "$JAR" --planning.scheduler-enabled=true \
    > logs/backend.log 2>&1 &
  echo $! > var/backend.pid
  for _ in $(seq 1 60); do
    ss -ltnH "sport = :8081" 2>/dev/null | grep -q . && break
    sleep 1
  done
  nohup node scripts/serve-entry.mjs > logs/entry.log 2>&1 &
  echo $! > var/entry.pid
fi

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
