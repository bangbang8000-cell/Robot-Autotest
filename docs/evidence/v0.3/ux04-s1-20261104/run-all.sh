#!/usr/bin/env bash
# UX-04 美术 S1（2026-11-04）证据复现脚本
# 在 WSL 权威副本项目根执行：
#   bash docs/evidence/v0.3/ux04-s1-20261104/run-all.sh
# 逐条实跑守门与测试命令，stdout 落盘为同目录 .log，并打印每条命令 rc。
# 本脚本是文件，内部 $变量 / $? 由 WSL bash 自身求值，不经 Windows 命令行层，
# 因此不会像「wsl.exe bash -lc '内联脚本'」那样被外层吃掉 $。
# fe-build 输出较长，仅保留末 40 行（README 中已注明为截断输出）。

cd /home/evergreen/robot-autotest || exit 99
OUT=docs/evidence/v0.3/ux04-s1-20261104

echo "=== UX-04 S1 复现开始 ==="

# 1) 前端生产构建：先跑，刷新 frontend/dist 供后续体积守门读取
set -o pipefail
( cd frontend && npm run build ) 2>&1 | tail -40 > "$OUT/fe-build.log"
printf '%-26s rc=%s\n' 'fe-build' "$?"
set +o pipefail

# 2) 前端单元测试
( cd frontend && npm run test ) > "$OUT/fe-test.log" 2>&1
printf '%-26s rc=%s\n' 'fe-test' "$?"

# 3) 全量 lint：secret-scan / eslint / mvn compile / 9 条静态守门
bash scripts/lint.sh > "$OUT/lint-sh.log" 2>&1
printf '%-26s rc=%s\n' 'lint-sh' "$?"

# 4) 静态守门逐条
python3 tools/check_contrast.py > "$OUT/check-contrast.log" 2>&1
printf '%-26s rc=%s\n' 'check-contrast' "$?"
python3 tools/check_i18n.py > "$OUT/check-i18n.log" 2>&1
printf '%-26s rc=%s\n' 'check-i18n' "$?"
python3 tools/check_tokens.py > "$OUT/check-tokens.log" 2>&1
printf '%-26s rc=%s\n' 'check-tokens' "$?"
python3 tools/check_colors.py > "$OUT/check-colors.log" 2>&1
printf '%-26s rc=%s\n' 'check-colors' "$?"
python3 tools/check_states.py > "$OUT/check-states.log" 2>&1
printf '%-26s rc=%s\n' 'check-states' "$?"
python3 tools/check_styleguide_tokens.py > "$OUT/check-styleguide.log" 2>&1
printf '%-26s rc=%s\n' 'check-styleguide' "$?"
python3 tools/check_styleguide_tokens.py --self-test > "$OUT/styleguide-selftest.log" 2>&1
printf '%-26s rc=%s\n' 'styleguide-selftest' "$?"
python3 tools/check_icons.py > "$OUT/check-icons.log" 2>&1
printf '%-26s rc=%s\n' 'check-icons' "$?"
python3 tools/check_icons.py --self-test > "$OUT/icons-selftest.log" 2>&1
printf '%-26s rc=%s\n' 'icons-selftest' "$?"
python3 tools/gen_accent_tokens.py --check > "$OUT/accent-tokens.log" 2>&1
printf '%-26s rc=%s\n' 'accent-tokens' "$?"
python3 tools/subset_font.py --check > "$OUT/font-subset.log" 2>&1
printf '%-26s rc=%s\n' 'font-subset' "$?"
python3 tools/check_bundle_budget.py > "$OUT/bundle-budget.log" 2>&1
printf '%-26s rc=%s\n' 'bundle-budget' "$?"
python3 tools/check_bundle_budget.py --self-test > "$OUT/bundle-selftest.log" 2>&1
printf '%-26s rc=%s\n' 'bundle-selftest' "$?"

# 5) 后端单元测试（本轮新增：强调色偏好白名单修复后必须复验）
( cd backend && mvn -B test ) > "$OUT/backend-mvn-unit.log" 2>&1
printf '%-26s rc=%s\n' 'backend-mvn-unit' "$?"

# 6) 后端集成测试（需本机环境文件；缺文件时如实记 SKIP，不写假通过）
if [ -f "$HOME/.config/robot-autotest/env" ]; then
  (
    set -a
    . "$HOME/.config/robot-autotest/env"
    set +a
    export DB_NAME=robot_it
    cd backend && mvn -B -Pit test
  ) > "$OUT/backend-mvn-it.log" 2>&1
  printf '%-26s rc=%s\n' 'backend-mvn-it' "$?"
else
  echo "[SKIP] 未找到 $HOME/.config/robot-autotest/env，集成测试未执行" \
    > "$OUT/backend-mvn-it.log"
  printf '%-26s rc=%s\n' 'backend-mvn-it' 'SKIP'
fi

# 7) 编辑器包单元测试（本轮未改编辑器，作为「未被牵连」的旁证）
( cd packages/editor && npm test ) > "$OUT/editor-vitest.log" 2>&1
printf '%-26s rc=%s\n' 'editor-vitest' "$?"

echo "=== UX-04 S1 复现结束 ==="
