#!/usr/bin/env bash
# UX-04c（两级菜单 + 金色调优 + 迁移第二批）证据复现脚本，2026-11-05。
#
# 在 WSL 权威副本项目根执行：
#   bash docs/evidence/v0.3/ux04c-20261105/run-all.sh
#
# 逐条实跑并把 stdout 落盘为同目录 .log，同时打印每条命令的真实 rc。
# 写成文件而不是 `wsl.exe bash -lc '内联'`：内联时 `$?` / `$变量` 会被 Windows
# 命令行层先展开一次，rc 恒等于 0，证据会变成假绿（这个坑本项目踩过）。
#
# fe-build 输出较长，只保留末 40 行；README 里已注明是截断输出。

cd /home/evergreen/robot-autotest || exit 99
OUT=docs/evidence/v0.3/ux04c-20261105

echo "=== UX-04c 复现开始 ==="

# 1) 前端生产构建：先跑，刷新 frontend/dist 供体积守门读取
set -o pipefail
( cd frontend && npm run build ) 2>&1 | tail -40 > "$OUT/fe-build.log"
printf '%-30s rc=%s\n' 'fe-build' "$?"
set +o pipefail

# 2) 前端单元测试（含本轮新增的 navigation.spec.ts 12 条）
( cd frontend && npm run test ) > "$OUT/fe-test.log" 2>&1
printf '%-30s rc=%s\n' 'fe-test' "$?"

# 3) 菜单结构与选中判定单测（单独跑一次，便于定位）
( cd frontend && npx vitest run src/config/navigation.spec.ts ) > "$OUT/navigation-spec.log" 2>&1
printf '%-30s rc=%s\n' 'navigation-spec' "$?"

# 4) 全量 lint：secret-scan / eslint / mvn compile / 12 条静态守门
bash scripts/lint.sh > "$OUT/lint-sh.log" 2>&1
printf '%-30s rc=%s\n' 'lint-sh' "$?"

# 5) 六预设派生（金色新值三项 WCAG 实测）
python3 tools/gen_accent_tokens.py > "$OUT/accent-tokens.log" 2>&1
printf '%-30s rc=%s\n' 'gen-accent-tokens' "$?"

# 6) 14 组 × 20 项对比度
python3 tools/check_contrast.py > "$OUT/check-contrast.log" 2>&1
printf '%-30s rc=%s\n' 'check-contrast' "$?"

# 7) 模板组件 ↔ import 对账（本轮新增守门）
python3 tools/check_component_imports.py > "$OUT/component-imports.log" 2>&1
printf '%-30s rc=%s\n' 'component-imports' "$?"

# 7b) 同一守门的负向自检：删掉一个 import 必须报 FAIL（否则这条守门是摆设）
rm -rf /tmp/import-selftest && mkdir -p /tmp/import-selftest/frontend
cp -r frontend/src /tmp/import-selftest/frontend/src
sed -i "/import EmptyState from/d" /tmp/import-selftest/frontend/src/views/AdminView.vue
python3 tools/check_component_imports.py --root /tmp/import-selftest \
    > "$OUT/component-imports-selftest.log" 2>&1
printf '%-30s rc=%s (期望 1)\n' 'component-imports-selftest' "$?"

# 8) 产物体积：图标 / vendor / 懒加载分包 / 字体 / styleguide 泄漏
python3 tools/check_bundle_budget.py > "$OUT/bundle-budget.log" 2>&1
printf '%-30s rc=%s\n' 'bundle-budget' "$?"

# 9) 字体子集覆盖（换文案后必须重生成，否则缺字）
python3 tools/subset_font.py --check > "$OUT/subset-font.log" 2>&1
printf '%-30s rc=%s\n' 'subset-font-check' "$?"

# 10) 后端单元测试（本轮未改后端，跑一遍排除连带）
( cd backend && mvn -B -q test ) > "$OUT/backend-mvn-test.log" 2>&1
printf '%-30s rc=%s\n' 'backend-mvn-test' "$?"

# 11) 本轮改动清单
git -C /home/evergreen/robot-autotest diff --stat > "$OUT/diff-stat.log" 2>&1
printf '%-30s rc=%s\n' 'diff-stat' "$?"

echo "=== 结束：日志在 $OUT ==="
