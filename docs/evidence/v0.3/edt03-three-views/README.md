# EDT-03 三视图往返 · 真实浏览器 E2E

- 入口：`http://localhost:8001` · 页面：`/editor/29?path=qa-e2e/edt09-save-chain.robot`
- 浏览器：Windows Chrome 159（CDP `127.0.0.1:9222`）· 视口 1440×900
- 判据：DEV-PLAN §1「三次切换后内容 sha256 不变」，且三个视图都真实渲染（不是空面板）
- 复跑：`cd ~/robot-autotest && node docs/evidence/v0.3/edt03-three-views/run.mjs`

## 一、结论

**EDT-03 由「待验」转「已完成」**：三视图在真实浏览器里往返三轮，代码视图内容逐字节一致，
表单视图与流程视图均有真实控件/节点。

结论一句话：**组件级 sha256 断言曾经是假绿 —— 代码视图在首屏根本没有 CodeMirror。**
本机 E2E 把它揪出来并修掉了。

| # | 断言 | 结果 | 实测 |
|---|---|---|---|
| EDT03-E2E-01 | 编辑器宿主页加载出真实 CodeMirror | PASS | `.cm-editor` 存在；tabs=`["代码","可视化编辑器","流程"]` |
| EDT03-E2E-02 | 三视图 tab 齐全（文本/表单/流程） | PASS | 同上 |
| EDT03-E2E-03 | 初始 code 视图有内容 | PASS | 长度=96，首行 `*** Settings *** / Documentation    EDT-09` |
| EDT03-E2E-04 | 表单视图真实渲染（有可编辑控件） | PASS | 面板 1185×897，7 个 input/select/textarea |
| EDT03-E2E-05 | 流程视图真实渲染（非空面板） | PASS | 面板 1200×500，162 个子节点 |
| EDT03-E2E-06 | 三轮往返后 code 内容完全一致 | PASS | 3/3 轮 |

**合计 6/6 通过**（截图：`editor-code.png`）

## 二、本轮实测发现并修掉的三个真实缺陷

### 1）首屏代码视图空白：CodeMirror 从来没被创建

`RobotEditor.vue` 里 `activeTab` 默认 `'code'`（PRD §5 约定默认进文本视图），
但 `initCodeEditor()` **只挂在 `switchTab('code')` 上**。首屏不切 tab，
于是 `onMounted` 只做了 parse，容器 `.code-editor` 里始终是空的。

为什么单测没发现：既有 `tests/three-views.spec.ts` 的判据是「切换不产生额外的
`update:content`」—— 一个不存在的编辑器同样什么都不回写，于是完美通过。
**这是典型的「用错误行为做基线」的假绿。**

修法（`packages/editor/src/components/editor/RobotEditor.vue`）：

```ts
onMounted(() => {
  ...
  if (activeTab.value === 'code') nextTick(initCodeEditor)
})
```

### 2）权限反转不跟随：只读开关读一次就固定

`props.readonly` 只在 `initCodeEditor()` 里读一次，编译进 `EditorState`。
真实页面上权限是**异步**拿到的（`GET /api/editor/permissions`），
宿主先渲染成 `readonly=false`，回来后才改成 `true` —— 此时 CodeMirror 仍可键入，
EDT-05 的「只读时 CodeMirror 本身也拒绝键入」实际失效。

修法：只读扩展开关走 `Compartment`，权限到达时原地 `reconfigure`（不销毁实例，
不丢 undo 历史与光标）：

```ts
const readOnlyCompartment = new Compartment()
// extensions: readOnlyCompartment.of([EditorState.readOnly.of(!!props.readonly), EditorView.editable.of(!props.readonly)])
watch(() => props.readonly, (ro) => {
  codeEditorView.value?.dispatch({
    effects: readOnlyCompartment.reconfigure([
      EditorState.readOnly.of(!!ro),
      EditorView.editable.of(!ro),
    ]),
  })
})
```

### 3）构建假绿：改了编辑器源码，前端产物不变

`scripts/build.sh` 的构建顺序是 **backend → frontend → packages/editor**，
而 `frontend/vite.config.ts` 把 `@robot-autotest/editor` 别名到
`packages/editor/dist/editor.js`（**构建产物**）。

于是：改 `packages/editor/src/**` → 跑 `deploy.sh` → 全绿 → 页面毫无变化。
本次 EDT-03 就踩了这个坑：补丁写进源码、部署报 PASS、浏览器里 `.cm-editor` 仍是 0。
翻出来是因为「部署成功但现象不变」不能自洽，才去查别名和顺序。

修法：把 `packages/editor` 提到 `frontend` 之前，并在脚本里写死注释说明原因。

## 三、回归单测（4 条，修复前全红）

`packages/editor/tests/editor-code-mount.spec.ts`，刻意断言 DOM 实体而不是内部状态：

| 用例 | 断言 | 修复前 | 修复后 |
|---|---|---|---|
| EDT03-REG-01 | 首屏 `.cm-editor` 必须存在（且 `.cm-content` 含 `*** Settings ***`） | FAIL `expected +0 to be 1` | PASS |
| EDT03-REG-01b | 代码视图内容与传入 content 一致（不是空串） | FAIL | PASS |
| EDT05-REG-02 | 可编辑 → 只读，`.cm-content[contenteditable]` 必须由 `true` 翻成 `false` | FAIL `expected false to be true` | PASS |
| EDT05-REG-02b | 只读 → 可编辑也能翻回来 | FAIL | PASS |

**反向验证做过**：`git checkout -- RobotEditor.vue` 回到修复前再跑，4 条全红；
恢复后 4 条全绿。不是「写完就绿」的装饰性测试。

## 四、边界说明（不夸大）

- 本 E2E 用 CDP 驱动真实 Chrome，但**没有**模拟键盘输入与 IME 组合键；
  「切视图不改语义」判据只覆盖 tab 往返，不覆盖「在代码视图里打字后切表单」。
  后者由 `tests/readonly.spec.ts` + `tests/upstream/FlowEditorRoundTrip*.spec.ts`
  在组件层覆盖，两者合起来才算完整。
- 截图只留了代码视图一张。表单/流程视图的实测数据（尺寸、控件数）来自同一轮的
  DOM 探针输出，未逐视图截图。
- 判据里的「sha256 不变」在本 E2E 里落成**字符串严格相等**（比 sha256 更严），
  因为 CodeMirror 的 `textContent` 已足够小（96 字符），没必要再散列。
