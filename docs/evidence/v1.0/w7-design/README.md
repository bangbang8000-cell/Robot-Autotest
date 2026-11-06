# W7 / UIX-01..04 设计体系 · 交付证据（2026-11-06）

## 实现

| 包 | 交付 |
|---|---|
| UIX-01 | `theme.css` 新增严格 4px 栅格 8 档（`--grid-1..8` = 4..32px）、`--w7-surface` / `--w7-border` 浅深双主题覆盖、两档密度 `--w7-gap`；字体 6 级沿用既有 `--font-size-*`；`tools/check_w7_tokens.py` 校验**取值**并带 `--self-check` 破坏性元验证 |
| UIX-02 | `frontend/src/config/layout.ts` 三栏纯规则：列宽永远精确铺满视口（`side + content + drawer === viewport`），抽屉是**真实第三栅格列**而非覆盖层；窄视口先压抽屉、再自动折叠侧栏，内容区不低于 340px；`main.css` 三栏 grid + 折叠态 + 999px / 760px 断点；`useAiDrawer.ts` 折叠与宽度记忆 |
| UIX-03 | `viewState.ts` 四态单点判定（ERROR > LOADING > EMPTY > READY，加载中保留旧行不闪骨架）+ `ViewState.vue` 统一包装（`aria-live="polite"`）；`DataTable` 壳继续承载表格四态 |
| UIX-04 | `*:focus-visible` 全局焦点环、`prefers-reduced-motion` 降级、响应式断点、`aria-label` / `aria-live`；**8 页 × 2 主题截图基线待现场**（见 `screenshot-baseline.md`） |

## 实测（本机）

- `layout.spec.ts` 5 条：1440 默认利用率 ≥ 78%（实测 86.1%）、1280 开抽屉无横滚且为三列、六档视口列宽精确平铺不重叠、窄视口自动折叠、抽屉宽度钳制。
- `viewState.spec.ts` 3 条：错误优先、加载中保留旧行、四态全部可达且仅 READY 渲染内容。
- 前端 239 条单测 / `vue-tsc --noEmit` / eslint / `scripts/lint.sh` 全绿；
  `check_w7_tokens.py` 与其 `--self-check` 均通过（篡改 `--grid-3` 必须被拒绝）。
- `tools/px-allow.txt` 新增 `32px`：这是经评审的第 8 档栅格步长，不是随手加的散值。

## 边界：UIX-04 记 `待现场`

8 页 × 2 主题截图基线需要真实浏览器逐页截图与人工比对，本机无该条件，
**不伪造截图、不冒充完成**。状态置 `待现场`，其余 UIX-01..03 由 AI 验收为 `已完成`。
G4/G5/G8 未判过，本波不申请任何门禁。
