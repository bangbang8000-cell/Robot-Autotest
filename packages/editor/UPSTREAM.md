# UPSTREAM — 上游基线与本包构成

**归属** EDT-01 ｜ **更新** 2026-11-02

## 1. 上游基线

| 项 | 值 |
|---|---|
| 上游仓库 | `github.com/viadee/roboscope` |
| 版本 / 标签 | **v0.16.1**（`main` 最后 push 2026-10-28） |
| 许可 | **Apache-2.0**（原文见本目录 `LICENSE-roboscope`，未作任何修改） |
| 获取方式 | `codeload.github.com/.../tar.gz/refs/tags/v0.16.1`（`git clone` 会被 Connection reset） |
| 本地解包路径 | `e:\workspace\自动化测试\roboscope-src\rs-0.16.1`（工作区资产，不入本仓） |
| 抽取入口 | `frontend/src/components/editor/RobotEditor.vue` |

## 2. 闭包抽取结果（可复现）

```bash
node scripts/closure.mjs \
  <roboscope-src>/frontend/src/components/editor/RobotEditor.vue \
  --root <roboscope-src>/frontend/src
```

实测输出（2026-11-02）：

| 项 | 数值 |
|---|---:|
| 上游闭包文件 | **47** |
| 上游闭包行数 | **18,278** |
| 外部依赖 | 18 个（codemirror ×5、vue-flow ×4、dompurify、pinia、vue、vue-i18n、axios） |

本人对「47 / 18,231」基线的差异说明：基线报告的行数按「`\n` 计数」，本脚本按「按行切分」
（末行无换行也计一行），逐文件相加后多 47 行，属统计口径差异，文件集合一致。

## 3. 本包构成

```
packages/editor/
├── index.ts                    公共出口（RobotEditor / FlowEditor / 适配器注入）
├── package.json                独立构建（库模式）
├── tsconfig.json               @ -> ./src
├── vite.config.ts              lib 构建，外部依赖不外打
├── LICENSE-roboscope           Apache-2.0 原文（未改）
├── NOTICE.md                   衍生说明
├── src/
│   ├── api/                    ★ 适配缝（9 个文件，全部无 HTTP）
│   ├── components/editor/      上游编辑器本体 + flow 节点组件（原样）
│   ├── components/ui/          BaseButton / BaseModal（原样）
│   ├── components/debug/       DebugPanel / DebugPrereqDialog（原样，v0.4 接管）
│   ├── components/recorder/    SelectorPicker（原样，v0.4 接管）
│   ├── composables/            useToast / useKeywordSignatures / useRecordingSidecar / useFeatureFlags
│   ├── stores/                 ui / explorer / environments / repos / auth / debug
│   ├── types/                  api / domain / recorder
│   └── utils/                  robotLanguage / robotKeywordSignatures / healToggle / errors / …
```

实测（2026-11-02，本机 Windows / Node 22.16 / npm 10.9.2）：

| 判定 | 结果 | 证据 |
|---|---|---|
| 闭包可独立编译 | `npm run build` exit 0，82 modules，`dist/editor.js` 403.6 kB（gzip 81.0 kB） | `docs/evidence/v0.3/npm-build-editor.log` |
| 包内无 HTTP | `axios` / `fetch(` / `XMLHttpRequest` 在 `src/` 与 `dist/` 均 **0 命中** | 同上目录 `http-scan.log` |
| 体积 | gzip 81 kB ≪ NFR-v0.3-06 的 1.5 MB | 构建输出 |

## 4. 与 DEV-PLAN §2 的一处刻意偏差（需评审）

DEV-PLAN §2.1 要求「**必须裁剪的 8 个文件**」：`api/ai.api`、`api/auth.api`、`api/client.ts`、
`api/debug.api`、`api/environments.api`、`api/explorer.api`、`api/governance.api`、`api/repos.api`。

**本包的做法：这 8 个文件不作为「同路径原文件」保留，而是重写为薄适配缝（9 个文件，
`api/context.ts` + 8 个同名模块），内部零 HTTP，全部委托给宿主注入的 `EditorAdapter`。**

理由（为什么比「删掉再改写 15 个消费方」更符合门禁）：

1. **G3 要求 19 个上游测试「断言未改」**。上游 `tests/` 与部分组件直接 `import from '@/api/...'`；
   物理删除会让测试 import 失效，被迫改测试，反而踩红线。
2. 保留同名模块 = 消费方 import 路径不变，**上游组件正文改动最小**，把风险留给 EDT-02 的
   props 契约改造，而不是一次性掀翻。
3. 硬指标（N13「包内无 HTTP」）达成且可复扫——`src/` 与 `dist/` 均 0 命中。

**遗留**：适配缝里的空实现（如 `installPackage` 抛「未实现」）在 v0.3 宿主页里不会被走到；
若某个入口确实需要，必须由宿主 `configureEditorAdapter()` 注入，不允许在本包内补发请求。

## 5. 未做（留给后续包）

| 包 | 内容 |
|---|---|
| EDT-02 | `RobotEditor` / `FlowEditor` 的 props 收敛为 `content + readonly + height`，去 `repoId` / `filePath` |
| EDT-03 | 三视图切换与 sha256 保真 |
| EDT-04 | 关键词面板接平台 `/api/editor/keywords` |
| EDT-05 | 只读 + 权限联动 |
| EDT-07 | 中文包合并、品牌残留清零 |
| EDT-08 | 许可检查单（本包已落 `LICENSE-roboscope` 与 `NOTICE.md`，文件头与商标清理待做） |
| EDT-09 | 保存链路与版本 |
