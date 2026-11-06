#!/usr/bin/env bash
# WS-01 骨架期构建脚本：存在什么就构建什么；都不存在时明确提示并退出 0。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

built=0

# NAV-03 / FR-v2.0-11：关于页的版本 / commit / 构建时间 / 依赖清单必须来自构建产物。
# 生成物被 .gitignore 忽略；未跑构建时 BuildInfoService 会显示 source=runtime，不伪装。
python3 scripts/gen_build_info.py

if [[ -f backend/pom.xml ]]; then
    command -v mvn >/dev/null 2>&1 || { echo "[build] 缺少 mvn，无法构建 backend"; exit 1; }
    echo "[build] backend: mvn -DskipTests package"
    (cd backend && mvn -q -DskipTests package)
    built=$((built + 1))
fi

# NAV-03 / FR-v2.0-12：版本迭代页的数据源是根 CHANGELOG.md，vite 只认 frontend/public/。
# 2026-11-06 实测：CHANGELOG.md 头部写着「build.sh 会同步」，但脚本里没有这一步，
# public/ 下一直是手工副本 —— 根文件改了、页面不变。这里把契约补上，根文件是唯一真源。
if [[ -f CHANGELOG.md && -d frontend/public ]]; then
    cp -f CHANGELOG.md frontend/public/CHANGELOG.md
    echo "[build] 同步 CHANGELOG.md -> frontend/public/CHANGELOG.md"
fi

# 顺序不能反：frontend 的 vite 别名指向 packages/editor/dist/editor.js，
# 编辑器必须先构建，否则前端打进的是上一轮的旧产物（构建全绿但改动不生效）。
if [[ -f packages/editor/package.json ]]; then
    command -v npm >/dev/null 2>&1 || { echo "[build] 缺少 npm，无法构建 packages/editor"; exit 1; }
    echo "[build] packages/editor: npm ci && npm run build"
    (cd packages/editor && npm ci && npm run build)
    built=$((built + 1))
fi

if [[ -f frontend/package.json ]]; then
    command -v npm >/dev/null 2>&1 || { echo "[build] 缺少 npm，无法构建 frontend"; exit 1; }
    echo "[build] frontend: npm ci && npm run build"
    (cd frontend && npm ci && npm run build)
    built=$((built + 1))
fi

if [[ $built -eq 0 ]]; then
    echo "[build] 骨架期：无可构建目标"
    echo "        backend/pom.xml、frontend/package.json 由 WS-02 / WS-03 建立后再纳入"
    exit 0
fi

echo "[build] PASS（构建目标 $built 个）"
