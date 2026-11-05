#!/usr/bin/env bash
# UX-04b 第三批（运行列表 + 用例列表迁库组件 + 视图级回归）证据复现脚本，2026-11-05。
#
# 在 WSL 权威副本项目根执行：
#   bash docs/evidence/v0.3/ux04b2-20261105/run-all.sh
#
# 逐条实跑并把 stdout 落盘为同目录 .log，同时打印每条命令的真实 rc。
# 写成文件而不是 `wsl.exe bash -lc '内联'`：内联时 $? / $变量 会被 Windows 命令行层
# 先展开一次，rc 恒等于 0，证据会变成假绿（这个坑本项目踩过）。

cd /home/evergreen/robot-autotest || exit 99
OUT=docs/evidence/v0.3/ux04b2-20261105

echo "=== UX-04b 第三批复现开始 ==="

# 1) 前端生产构建：先跑，刷新 frontend/dist 供体积守门读取
set -o pipefail
( cd frontend && npm run build ) 2>&1 | tail -40 > "$OUT/fe-build.log"
printf '%-30s rc=%s\n' 'fe-build' "$?"
set +o pipefail

# 2) 前端全量单测（本轮 47 → 61 条）
( cd frontend && npm run test ) > "$OUT/fe-test.log" 2>&1
printf '%-30s rc=%s\n' 'fe-test' "$?"

# 3) 两个视图的回归单独跑一次，便于失败时定位
( cd frontend && npx vitest run src/views ) > "$OUT/view-test.log" 2>&1
printf '%-30s rc=%s\n' 'view-test' "$?"

# 4) 状态色档显示契约（本轮新增 reviewStatusTone）
( cd frontend && npx vitest run src/utils/statusLabels.spec.ts ) > "$OUT/status-tone.log" 2>&1
printf '%-30s rc=%s\n' 'status-tone' "$?"

# 5) 全量 lint：secret-scan / eslint / mvn compile / 12 条静态守门
bash scripts/lint.sh > "$OUT/lint-sh.log" 2>&1
printf '%-30s rc=%s\n' 'lint-sh' "$?"

# 6) 模板组件 ↔ import 对账（防「未注册组件静默不渲染」）
python3 tools/check_component_imports.py > "$OUT/component-imports.log" 2>&1
printf '%-30s rc=%s\n' 'component-imports' "$?"

# 6b) 负向自检：删掉一个 import 必须报 FAIL（否则这条守门是摆设）
rm -rf /tmp/import-selftest2 && mkdir -p /tmp/import-selftest2/frontend
cp -r frontend/src /tmp/import-selftest2/frontend/src
sed -i "/import StatusTag from/d" /tmp/import-selftest2/frontend/src/views/RunsView.vue
python3 tools/check_component_imports.py --root /tmp/import-selftest2 \
    > "$OUT/component-imports-selftest.log" 2>&1
printf '%-30s rc=%s (期望 1)\n' 'component-imports-selftest' "$?"

# 7) 产物体积：图标 / vendor / 懒加载分包 / 字体 / styleguide 泄漏
python3 tools/check_bundle_budget.py > "$OUT/bundle-budget.log" 2>&1
printf '%-30s rc=%s\n' 'bundle-budget' "$?"

# 8) 字体子集覆盖（改注释也会进子集，必须重生成）
python3 tools/subset_font.py --check > "$OUT/subset-font.log" 2>&1
printf '%-30s rc=%s\n' 'subset-font-check' "$?"

# 9) i18n 键集 / 硬编码中文 / 受控词表
python3 tools/check_i18n.py > "$OUT/check-i18n.log" 2>&1
printf '%-30s rc=%s\n' 'check-i18n' "$?"

# 10) 后端单元测试（本轮未改后端，跑一遍排除连带）
( cd backend && mvn -B -q test ) > "$OUT/backend-mvn-test.log" 2>&1
printf '%-30s rc=%s\n' 'backend-mvn-test' "$?"

# 11) 本轮改动清单
git -C /home/evergreen/robot-autotest diff --stat > "$OUT/diff-stat.log" 2>&1
printf '%-30s rc=%s\n' 'diff-stat' "$?"

echo "=== 结束：日志在 $OUT ==="
