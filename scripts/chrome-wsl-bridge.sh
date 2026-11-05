#!/usr/bin/env bash
# 仅 WSL 开发机使用的 Chrome 桥接器（RPT-05 端到端验证脚手架，不属产品代码）。
#
# 背景：PdfRenderer 传给浏览器的参数是 --print-to-pdf=<Linux 绝对路径>，
# 但 WSL 里能用的浏览器只有 Windows Chrome，它不认识 /tmp/...，会把产物写到
# C:\tmp\... 导致后端判定「浏览器未生成文件」。本脚本把它翻译成 \\wsl.localhost\... 后再调用，
# 并在退出码为 0 但文件缺失时明确报错，避免把失败伪装成成功。
set -uo pipefail

CHROME="${CHROME_WSL_EXE:-/mnt/c/Program Files/Google/Chrome/Application/chrome.exe}"
PROFILE_DIR="${CHROME_WSL_PROFILE:-/tmp/rpt05-chrome-profile}"
mkdir -p "$PROFILE_DIR"

args=()
pdf_linux=""
for arg in "$@"; do
  case "$arg" in
    --print-to-pdf=*)
      pdf_linux="${arg#--print-to-pdf=}"
      args+=("--print-to-pdf=$(wslpath -w "$pdf_linux")")
      ;;
    *) args+=("$arg") ;;
  esac
done

if [ -z "$pdf_linux" ]; then
  echo "chrome-wsl-bridge: 未找到 --print-to-pdf 参数" >&2
  exit 2
fi
mkdir -p "$(dirname "$pdf_linux")"

"$CHROME" --no-sandbox --no-first-run \
  --user-data-dir="$(wslpath -w "$PROFILE_DIR")" "${args[@]}"
status=$?

if [ "$status" -eq 0 ] && [ ! -f "$pdf_linux" ]; then
  echo "chrome-wsl-bridge: Chrome 退出 0 但未生成 $pdf_linux" >&2
  exit 3
fi
exit "$status"
