# v0.3 · EDT-07 / EDT-08 实施规格（Spec）

**文档编号** RA-REL-2026-003-SPEC-EDT0708 ｜ **对应** WBS #27、#28 ｜ **门禁** G3
**范围** 中文包合并 + 品牌残留清零（EDT-07）；许可合规四件事（EDT-08）
**性质** AI 只能交付到 `待验`；EDT-08 是 C 级包，最终「合规通过」必须由人签署

---

## 0. 现状基线（2026-11-03 实测）

| 项 | 事实 | 依据 |
|---|---|---|
| WBS #27 / #28 | 均 `未开始`，无交付物 | `docs/plan/WBS-STATUS.md` |
| 上游中文包 | `roboscope-src/rs-0.16.1/frontend/src/i18n/locales/zh.ts`，**2,103 行** | `(Get-Content zh.ts).Count` = 2103 |
| 上游装载方式 | `createI18n({ messages: { de, en, fr, es, zh } })`；`zh.ts` 内部 `deepMerge(en, overrides)` | `i18n/index.ts`、`zh.ts` 头注释 |
| 编辑器用到命名空间 | `robotEditor` 143、`flowEditor` 122、`debug` 24、`recorder` 22、`common` 15 次（4 键）、`editor` 3；**唯一键 235** | 源码正则扫描 |
| 平台 i18n | `frontend/src/locales/{index,zh-CN,en-US}.ts`，`createI18n` 仅装载 `app/menu/common/login/project/assets/cases/admin/notFound` | 同上 |
| 键冲突 | 平台已有 `common.cancel/delete/save`，与编辑器同名同义；`common.close` 平台无 | 同上 |
| 编辑器当前渲染 | 宿主未合并编辑器消息 → `t()` 显示原始键名（FR-v0.3-24 未达成） | `frontend/src/locales/index.ts` 无编辑器消息 |
| 品牌·产品面 | `frontend/src`、`backend/src` **0 命中** | 扫描 |
| 品牌·编辑器 src | 58 行命中：归属头 20 文件、`RoboScopeHeal` 库名约 20 行、**真残留 4 处**、`paletteView.ts` 2 个 localStorage 键 | 扫描 |
| 品牌·产物 | `packages/editor/dist/editor.js` 7 处、`frontend/dist/assets/EditorView-DKOjf0GI.js` 3 处 | 扫描 |
| 品牌·文档面 | `docs/` 82 行 / 10 文件，全部为上游衍生与许可说明 | 扫描 |
| 许可现状 | 根 `LICENSE` 缺、根 `NOTICE` 缺、根 `LICENSE-roboscope` 缺；`packages/editor/LICENSE-roboscope` 与 `NOTICE.md` 已有；20 个改写文件已带 `Modified by Robot-Autotest` 头 | 目录/文件头扫描 |

---

## 1. EDT-07 交付规格

### 1.1 品牌扫描器（先建工具，再清理）

**新增 `scripts/brand-scan.sh`**（bash，Windows 下用 Git Bash / WSL 跑，与既有 `*.sh` 一致）。

- 扫描词：`roboscope|RoboScope|viadee|roboflow`（大小写不敏感）
- **产品面**（必须 0 命中，白名单外不得放行）：
  `packages/editor/src`、`packages/editor/index.ts`、`packages/editor/dist`、
  `frontend/src`、`frontend/dist`、`backend/src`
- **归属面**（允许保留，逐条说明，不计入产品面清零目标）：
  根 `LICENSE*`、根 `NOTICE`、`packages/editor/{LICENSE-roboscope,NOTICE.md,UPSTREAM.md,README.md}`、`docs/**`
- 白名单文件 **`brand-allow.txt`**（仓库根），每行 `路径glob|命中文本|理由`。
  只登记两类：
  1. Apache-2.0 §4(b) 要求的归属声明（文件头 `衍生自 viadee/roboscope v0.16.1`）；
  2. Robot Framework 库名 `RoboScopeHeal`（`.robot` 语法事实，非产品商标；改名会破坏用户脚本）。
- 输出：`docs/evidence/v0.3/edt07-brand-scan.log`，含「产品面命中数」「归属面命中数」「白名单放行条数」
  与非零退出码（产品面 > 0 即失败）。

### 1.2 真残留清理（4 处源码 + 2 个键）

| 文件 | 现状 | 改为 |
|---|---|---|
| `src/components/editor/flow/paletteView.ts:23-24` | `'roboscope.flowPalette.filter'` / `'.sort'` | `'robot-autotest.flowPalette.filter'` / `'.sort'`（旧键值丢失可接受，仅 UI 偏好） |
| `src/types/recorder.types.ts:127` | `... Upgrade RoboScope.` | `... 请升级 Robot Autotest。`（用户可见错误文案） |
| `src/utils/robotLanguage.ts:345` | 注释 `RoboScope-branded highlight style` | `平台品牌高亮样式（配色取自 frontend/src/assets/styles/main.css）` |
| `src/components/editor/RobotEditor.vue:374` | 注释 `RoboScope-branded highlight style` | 同上措辞 |

保留并登记白名单：文件头归属声明；`healToggle.ts` / `FlowEditor.vue` / `RobotEditor.vue` 中的
`RoboScopeHeal` 库名与 `Library    RoboScopeHeal` 语法。

### 1.3 中文包合并（独立命名空间，避免与平台共享键）

**命名空间决定**：编辑器键一律收敛到不与平台共享的名字。

| 现键前缀 | 处置 |
|---|---|
| `robotEditor.*`、`flowEditor.*`、`debug.*`、`recorder.*`、`editor.*` | **保留**（平台均无这些键，无冲突） |
| `common.*`（`save/cancel/delete/close`，15 个调用点） | **改名为 `editor.common.*`**，彻底消除与平台 `common.*` 的共享 |

**新增 `packages/editor/tools/extract-locales.mts`**（可复现生成器）：

1. 用 `esbuild` 把上游 `en.ts`、`zh.ts` 编译为临时 ESM 后 `import()`（不手抄、不 eval）；
2. 以 `deepMerge(en, zh overrides)` 得到完整中文，取 5 个命名空间；
3. 把其中的 `common` 节点重命名为 `editor.common`；
4. 写出
   - `packages/editor/src/locales/zh-CN.ts`
   - `packages/editor/src/locales/en-US.ts`
   （均为 `export default { robotEditor, flowEditor, debug, recorder, editor }` 纯对象）；
5. 内置断言：**zh 与 en 的叶子键集合完全一致**（缺一即非零退出），并打印键数。

**新增 `packages/editor/src/locales/index.ts`**：导出 `editorMessages: Record<'zh-CN'|'en-US', ...>`。

**改 `packages/editor/index.ts`**：`export { editorMessages } from './src/locales'`。

**改 `frontend/src/locales/index.ts`**：`messages` 顶层展开合并
`{ 'zh-CN': { ...zhCN, ...editorMessages['zh-CN'] }, 'en-US': { ...enUS, ...editorMessages['en-US'] } }`。

### 1.4 EDT-07 验证

**新增 `packages/editor/tests/i18n-locales.spec.ts`**（4 条）：

1. 源码中出现的 235 个键在 `zh-CN` 全部可解析（逐个 `get`，不允许 undefined）；
2. 同样在 `en-US` 全部可解析；
3. `zh-CN` 与 `en-US` 叶子键集合相等（数量与名单一致）；
4. `zh-CN` 的 `robotEditor/flowEditor/debug/recorder/editor` 子树值中不含成串英文字母（抽样正则），
   证明「切换 zh-CN 后无英文硬编码残留」。

命令与证据：

```bash
cd packages/editor && npx vite-node tools/extract-locales.mts   # 生成并自检键集合
cd packages/editor && NO_COLOR=1 npx vitest run                  # 全量（含新增 4 条）
cd packages/editor && npm run build                              # 产物重建
cd frontend && npm run build                                     # 宿主产物重建
bash scripts/brand-scan.sh                                       # 产品面 0 命中
```

证据：`edt07-locales.log`、`edt07-vitest.log`、`edt07-npm-build-editor.log`、
`edt07-frontend-build.log`、`edt07-brand-scan.log`。

---

## 2. EDT-08 交付规格（C 级，交付后人签）

### 2.1 许可决定

**项目根 `LICENSE` 采用 Apache License 2.0**。理由：本仓库包含 Apache-2.0 衍生代码，
同许可最省义务、无兼容争议；且 README 已声明仓库 public。生成方式：**逐字复制**
`packages/editor/LICENSE-roboscope`（即上游 Apache-2.0 原文），不手工改写条款。

> 该决定与「根许可选型」属法律决策，规格中标注为 **待人工签署确认**；若人改为其他许可，
> 只需替换根 `LICENSE` 一个文件，其余交付物不受影响。

### 2.2 四件事逐一落地

| # | 交付 | 目标路径 | 判定 |
|---|---|---|---|
| 1 | 本项目许可 | 根 `LICENSE` | 存在 + 与 `LICENSE-roboscope` 内容一致（`cmp`/sha256 相等） |
| 2 | 上游许可原文 | 根 `LICENSE-roboscope` | 与 `packages/editor/LICENSE-roboscope` **字节一致** |
| 3 | 衍生声明 | 根 `NOTICE` | 含「基于 viadee/roboscope v0.16.1」「Apache-2.0」「修改内容清单」「商标不使用」四要素 |
| 4 | 文件头 | 20 个改写文件 | 每个文件头含 `衍生自 viadee/roboscope v0.16.1（Apache-2.0）` 与 `Modified by Robot-Autotest` |
| 5 | 品牌 | 见 §1 | 产品面扫描 0 命中 |

`packages/editor/NOTICE.md` 保留为**分包详细声明**；根 `NOTICE` 为仓库级声明并指向它。
同步把 `packages/editor/NOTICE.md` 末尾检查单的两项未完成项更新为已完成（并注明证据路径）。

### 2.3 发布检查单（人签）

新增 `docs/release/v0.3-editor/EDT-08-发布检查单.md`：四件事逐项 + 可复现命令 + 结果列 +
「签署人 / 日期」空栏。**AI 只填实测结果，不签署。**

### 2.4 EDT-08 验证

```bash
sha256sum LICENSE LICENSE-roboscope packages/editor/LICENSE-roboscope   # 前两者与第三一致
grep -c "viadee/roboscope v0.16.1" NOTICE                               # ≥1
grep -rl "Modified by Robot-Autotest" packages/editor/src | wc -l        # 20
bash scripts/brand-scan.sh                                              # 产品面 0
```

证据：`docs/evidence/v0.3/edt08-license-check.log`。

---

## 3. 明确不做

| 不做 | 原因 |
|---|---|
| 把上游 6 个语言包（de/fr/es）一并搬入 | 本项目只支持 zh-CN / en-US（FR-v0.1-10） |
| 把编辑器 235 个键全部重命名加 `editor.` 前缀 | 需改 300+ 调用点，触发 19 个上游测试断言风险；仅 `common.*` 与平台冲突，只改该处 |
| 把 `RoboScopeHeal` 库名改掉 | 是 `.robot` 上游库的语法事实，改名会让用户脚本执行失败 |
| 在代码里用变量拼接规避品牌扫描 | DEV-PLAN §7.1 明令禁止（自欺） |
| 由 AI 判定「合规通过」 | C 级包，人签 |

---

## 4. 风险与处置

| 风险 | 处置 |
|---|---|
| esbuild 编译上游 `en.ts` 失败（它含 TS 类型别名） | 失败即降级为「正则抽取字面量」并记录偏差，不手抄翻译；仍须过 §1.4 的键集合断言 |
| 合并后平台页面文案被覆盖 | 编辑器键前缀均与平台不同名；合并只做顶层展开，平台既有键原样保留（测试 4 条守护） |
| 宿主构建顺序（frontend 依赖 editor dist） | 先 `packages/editor npm run build` 再 `frontend npm run build`，证据日志按此顺序 |
| 根许可选型争议 | 单文件可替换；作为「待人工签署」项显式标注 |

---

## 5. 完成判定（WBS 置 `待验` 的硬条件）

- [ ] `edt07-brand-scan.log`：产品面 **0 命中**，归属面逐条登记
- [ ] `edt07-vitest.log`：编辑器全量用例全绿（原 211 + 新增 4）
- [ ] `edt07-npm-build-editor.log` / `edt07-frontend-build.log`：exit 0
- [ ] 根 `LICENSE` / `NOTICE` / `LICENSE-roboscope` 三文件齐备且校验和可复现
- [ ] 20 个改写文件头声明可枚举
- [ ] `EDT-08-发布检查单.md` 已产出并留签署栏
- [ ] WBS #27、#28 置 `待验`（#28 注明「待人工签署」）
