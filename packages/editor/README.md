# packages/editor

Robot Framework 三视图编辑器。衍生自 **viadee/roboscope v0.16.1（Apache-2.0）**，
上游基线与抽取口径见 [UPSTREAM.md](./UPSTREAM.md)，衍生声明见 [NOTICE.md](./NOTICE.md)。

## 边界（硬约束）

| 层 | 允许改动 | 说明 |
|---|---|---|
| `src/components/editor/`、`src/components/ui/` | **原则上原样** | 上游组件；改写点在 EDT-02 逐个标注 |
| `src/api/` | 唯一适配缝 | 9 个文件，**零 HTTP**，全部委托注入的 `EditorAdapter` |
| `src/stores/`、`src/composables/`、`src/utils/` | 最小必要改动 | 去宿主耦合，行为保持 |

业务代码（`backend/`、`frontend/`）**不得反向侵入本包的内部 store / flow 节点组件**；
宿主只通过 `index.ts` 的公开出口与 `configureEditorAdapter()` 接线。

## 宿主接入方式

```ts
import { configureEditorAdapter, RobotEditor } from '@robot-autotest/editor'

configureEditorAdapter({
  searchKeywords: (q, repoId) => platformApi.searchKeywords(q, repoId),
  saveFile: (repoId, path, content) => platformApi.saveFile(repoId, path, content),
})

// 组件只吃内容，不认识「仓库」概念（EDT-02 收敛后的契约）
<RobotEditor :content="content" :readonly="!canWrite" @save="onSave" />
```

未注入的能力一律降级为空数据（不抛异常），保证编辑器在断网 / 未接线时仍可打开只读内容。

## 构建与自检

```bash
cd packages/editor
npm install
npm run build          # vue-tsc --noEmit && vite build
```

实测（2026-11-02，本机 Windows）：`npm run build` exit 0，82 modules，
`dist/editor.js` 403.6 kB（gzip 81.0 kB）；`src/` 与 `dist/` 的
`axios|fetch(|XMLHttpRequest` 扫描均 **0 命中**。证据：`docs/evidence/v0.3/`。

## 许可四件事（P6 决策，2026-11-02）

1. `LICENSE-roboscope`：Apache-2.0 原文，未修改
2. `NOTICE.md`：衍生关系与逐条修改声明
3. 每个改写文件加文件头 `Modified by Robot-Autotest`（EDT-02 落地时补）
4. 品牌残留清零（EDT-07）

## WBS

EDT-01（本目录落成）→ EDT-02 改写 15 文件 → EDT-03 三视图 → EDT-06 往返保真 →
EDT-04 关键词面板 → EDT-05 只读 → EDT-07 中文与品牌 → EDT-08 许可 → EDT-09 保存链路。
进度真源：`docs/plan/WBS-STATUS.md`。
