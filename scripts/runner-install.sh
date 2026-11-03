#!/usr/bin/env bash
# WS-05：在 192.168.110.149 上装 GitHub Actions 自托管 runner（裸进程，非 Docker，非 root）。
#
# 本脚本**只装不注册**：注册需要一次性 RUNNER_TOKEN（在 GitHub 仓库
# Settings → Actions → Runners → New self-hosted runner 里现取，约 1 小时失效）。
# 拿不到 token 时脚本打印操作步骤后退出 0，不产生任何半注册状态。
#
# 用法：
#   bash scripts/runner-install.sh              # 安装（缺 RUNNER_TOKEN 时打印说明）
#   RUNNER_TOKEN=xxxx bash scripts/runner-install.sh   # 安装 + 注册 + 起服务
#   bash scripts/runner-install.sh --status     # 只看当前状态
#
# 参数来源（缺省值就是 149 的现状，换机时用环境变量覆盖）：
#   RUNNER_URL   仓库地址，默认 https://github.com/huanchen811509-silence/Robot-Autotest
#   RUNNER_NAME  runner 名，默认 rf-lab-149
#   RUNNER_LABELS 附加标签，默认 rf-lab（工作流要求 self-hosted,linux,x64,rf-lab）
#   RUNNER_DIR   安装目录，默认 ~/actions-runner
#   RUNNER_VERSION runner 版本，默认 2.321.0
set -euo pipefail

RUNNER_URL="${RUNNER_URL:-https://github.com/huanchen811509-silence/Robot-Autotest}"
RUNNER_NAME="${RUNNER_NAME:-rf-lab-149}"
RUNNER_LABELS="${RUNNER_LABELS:-rf-lab}"
RUNNER_DIR="${RUNNER_DIR:-$HOME/actions-runner}"
RUNNER_VERSION="${RUNNER_VERSION:-2.321.0}"
RUNNER_USER="$(id -un)"

status() {
    if [[ ! -d "$RUNNER_DIR" ]]; then
        echo "[runner] 未安装：$RUNNER_DIR 不存在"
        return 0
    fi
    echo "[runner] 目录：$RUNNER_DIR"
    if [[ -f "$RUNNER_DIR/.runner" ]]; then
        echo "[runner] 已注册：$RUNNER_NAME"
    else
        echo "[runner] 未注册（缺 .runner 文件）"
    fi
    if [[ -f "$RUNNER_DIR/svc.sh" ]]; then
        (cd "$RUNNER_DIR" && ./svc.sh status 2>/dev/null || echo "[runner] 服务未安装")
    fi
}

if [[ "${1:-}" == "--status" ]]; then
    status
    exit 0
fi

if [[ "$RUNNER_USER" == "root" ]]; then
    echo "[runner] 不要用 root 安装：runner 会以 root 执行 PR 里的代码" >&2
    exit 1
fi

if [[ ! -d "$RUNNER_DIR" ]]; then
    echo "[runner] 下载 runner $RUNNER_VERSION 到 $RUNNER_DIR"
    mkdir -p "$RUNNER_DIR"
    curl -fsSL -o /tmp/actions-runner.tar.gz \
        "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz"
    tar -xzf /tmp/actions-runner.tar.gz -C "$RUNNER_DIR"
    rm -f /tmp/actions-runner.tar.gz
else
    echo "[runner] 目录已存在，跳过下载：$RUNNER_DIR"
fi

# 依赖检查：runner 需要 libicu / libssl，149 上官方脚本会自己查
if ! (cd "$RUNNER_DIR" && ./bin/installdependencies.sh); then
    echo "[runner] installdependencies.sh 失败，通常需要 sudo 装 libicu-dev / libssl-dev" >&2
    echo "          sudo apt-get install -y libicu-dev libssl-dev" >&2
    exit 1
fi

if [[ -z "${RUNNER_TOKEN:-}" ]]; then
    echo ""
    echo "[runner] 已安装到 $RUNNER_DIR，但**未注册**（没有 RUNNER_TOKEN）。"
    echo "         注册需要一次性 token，操作："
    echo "           1. 浏览器打开 $RUNNER_URL/settings/actions/runners/new"
    echo "           2. 抄下页面里的 token（形如 AABBCC...，约 1 小时失效）"
    echo "           3. 在 149 上执行："
    echo "              RUNNER_TOKEN=<token> bash scripts/runner-install.sh"
    echo ""
    echo "         或者手工两条命令（等价）："
    echo "           cd $RUNNER_DIR && ./config.sh --url $RUNNER_URL --token <token> --name $RUNNER_NAME --labels $RUNNER_LABELS"
    echo "           cd $RUNNER_DIR && ./svc.sh install $RUNNER_USER && ./svc.sh start"
    exit 0
fi

echo "[runner] 注册：$RUNNER_NAME（labels: $RUNNER_LABELS）"
cd "$RUNNER_DIR"
./config.sh --url "$RUNNER_URL" --token "$RUNNER_TOKEN" \
    --name "$RUNNER_NAME" --labels "$RUNNER_LABELS" --unattended
./svc.sh install "$RUNNER_USER"
./svc.sh start

echo ""
echo "[runner] 完成。用 make ci-local 可在同一台机器上手工跑三关，不依赖 runner。"
status
