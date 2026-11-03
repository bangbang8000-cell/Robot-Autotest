# v0.3 编辑器 · 开发计划

**文档编号** RA-REL-2026-003-DEV ｜ **版本** v0.3 ｜ **门禁** G3

**范围** EDT-01~09、DOC-02（editor）｜ **对应 PRD** FR-v0.3-01 ~ 27

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| EDT-01 | 依赖闭包抽取（39 文件 / 15,946 行）+ monorepo 化 | 12 | G1 | **B** | 闭包内 http / axios / fetch 三种调用均 0 命中；`npm run build` 成功 |
| EDT-02 | 15 文件改写（去 repoId / HTTP / 宿主 i18n） | 20 | EDT-01 | **B** | 组件 props 只剩 `content` + 纯 UI 参数；单测绿 |
| EDT-03 | 三视图打通 | 15 | EDT-02 | B | 三次切换后 sha256 不变 |
| EDT-06 | 往返保真 + 19 个上游测试 | 15 | EDT-03 | **B（T1）** | 50 样本 `dryrun` 100%；19 测试全绿 |
| EDT-04 | 关键词面板接平台 | 15 | EDT-02 + 后端接口 | A | 断网降级正常；插入命中 |
| EDT-05 | 只读查看器 + 权限联动 | 8 | EDT-02、v0.2 的 RBAC | A | 只读下接口 403 |
| EDT-07 | 中文包 + 品牌清理 42 处 | 8 | EDT-02 | B | 全文搜索 0 命中（含 dist） |
| EDT-08 | 许可合规四件事 | 5 | EDT-01 | **C** | 发布检查单全绿（人签） |
| EDT-09 | 保存链路（内容→文件→版本） | 12 | EDT-03、v0.2 CAS-02 | A | 三次保存产生三个版本；未变不增版 |
| DOC-02 | `CONVENTIONS-editor.md` | 3 | EDT-02 | A | CI 能引用其中的红线（如禁止 import 业务） |

### 1.1 依赖链与并行

```
EDT-01 ─► EDT-02 ─┬─► EDT-03 ─► EDT-06（T1，串行不可并行）
                  ├─► EDT-04（需后端 keyword 接口，可先写假数据）
                  ├─► EDT-05
                  ├─► EDT-07
                  └─► EDT-09（需 v0.2 的 robot_file）
EDT-08（许可）与 EDT-01 同时开始，不然后面补 NOTICE 会漏文件
```

**EDT-06 不能与 EDT-03 并行**：往返保真要等三视图都通了才有意义，提前测是把问题推给 E2E。

---

## 2. 依赖闭包抽取（EDT-01）

### 2.1 抽取方法

以 `RobotEditor.vue` 为根，递归解析 `@/` 与相对路径 import，得到文件集合与行数。基线报告已给出量化：**47 文件 / 18,231 行**为依赖闭包，**39 文件 / 15,946 行**为核心（去掉 8 个可裁剪的辅助文件）。

```bash
# 复现命令（脚本 scripts/closure.mjs）
node scripts/closure.mjs frontend/src/components/editor/RobotEditor.vue
# 输出：文件数、总行数、外部 import 清单（应只剩 vue / pinia / codemirror / @vue-flow）
```

**必须裁剪的 8 个文件**：上游的 AI 对话面板、Repo 选择器、上传对话框、上游设置页组件、上游路由注册、上游 API client、上游 auth store、上游遥测。

### 2.2 裁剪判据（不是「看着没用」）

| 判据 | 处理 |
|---|---|
| 发起 HTTP 请求 | 必删（N13 硬约束） |
| 依赖 `repoId` 等宿主概念 | 必删或改写 |
| 依赖上游 i18n 实例 | 改为平台 i18n |
| 只被已删文件引用 | 连带删（避免死代码） |
| 纯展示且有复用价值（如语法高亮主题） | 保留 |

---

## 3. 15 个文件的改写规则（EDT-02）

### 3.1 props / emits 契约

**目标契约**（改写后）：

```ts
// RobotEditor.vue
props: {
  content: string            // 唯一的内容输入
  readonly?: boolean         // 只读开关
  height?: string            // 纯 UI
}
emits: {
  save: (content: string) => void
  'update:content': (content: string) => void
  dirty: (isDirty: boolean) => void
}
```

**四条改写规则**：

| # | 规则 | 反例（上游做法） | 我们的做法 |
|---|---|---|---|
| 1 | 组件不认识「仓库」 | `repoId`、`filePath` 用于自己调 API 取内容 | 内容由父组件传入，组件不取数据 |
| 2 | 组件不发请求 | 内部 `axios.get('/api/repos/'+repoId)` | 全部移除；需要数据由 props 给 |
| 3 | 组件不自带 i18n 实例 | `i18n.global.t()` 绑上游 locale | 用平台 `useI18n()` |
| 4 | 组件不写路由 | `router.push` 跳上游页面 | 移除；跳转由父组件处理 |

**验证方式**：改完后 `grep -n "axios\|fetch(\|repoId\|router.push" packages/editor/src` 应为空（或仅注释）。

### 3.2 解析器归属（关键架构决定，ADR-0007）

`robotTextIO.ts`（774 行）是**解析的唯一真源**，纯 TypeScript，无外部依赖。

**决定：保留 TS 解析器，不在 Java 侧重写。**

| 选项 | 问题 |
|---|---|
| Java 重写解析器 | 两份实现必然分叉；RF 语法边角多，重写等于重新踩一遍坑 |
| **TS 保留 + 后端只存字符串** | ✅ 后端不解析 `.robot`，只存 `content`；解析只发生在浏览器 |

**代价**：后端无法在服务端校验语法（FR-v0.3-23 要求保存前解析）。**解决**：保存前由前端解析并把 `parse_ok` 与 `parse_error` 一起提交；后端不信前端时，用 `robot --dryrun` 在服务端兜底校验（v0.4 的执行能力，本版可先用 `python -m robot.parsing` 做 AST 解析校验）。

> 后端兜底校验建议用 `robot.api.get_model()`，**不引入第二个手写解析器**。这条写进 ADR，避免后人再造一个。

---

## 4. 往返保真的独立裁判（T1 的正确做法）

### 4.1 为什么不能用编辑器自证

编辑器自己解析、自己渲染、自己序列化，然后比较「我序列化的结果我能不能再解析」——这是循环论证。**必须有一个外部裁判。**

### 4.2 三层裁判

| 层 | 裁判 | 判什么 | 判定 |
|---|---|---|---|
| L1 可执行性 | `robot --dryrun` | 改完还能不能跑 | 退出码 0（硬性） |
| L2 结构等价 | Python `robot.api.get_model()` | 步骤/参数/设置是否语义一致 | 归一化后 AST 比对无差异 |
| L3 人眼抽验 | 人工 | 注释、空行、可读性 | 抽 10 个样本逐行看 |

```python
# tools/roundtrip_judge.py（要点）
from robot.api import get_model
import ast as pyast

def normalize(path):
    model = get_model(path)
    # 只取语义节点：TestCase 名称、Step 的关键词名与参数、Settings 键值
    return {
        "tests": [(t.name, [ (s.keyword, s.args) for s in t.body
                             if s.type in ("KEYWORD","FOR","IF","TRY") ])
                  for t in model.sections if t.type == "TESTCASE"],
    }

def judge(a, b):
    assert normalize(a) == normalize(b), "AST 语义不一致"
```

**三条纪律**：

1. `dryrun` 通过 ≠ 语义一致（可能少跑了一个断言仍然通过）。**必须两层都过**。
2. 归一化时**忽略行号、缩进、注释**——这些允许变化。
3. 样本里必须含「故意难」的语法（见 §5），否则 100% 通过毫无意义。

---

## 5. 50 个往返样本的构造（EDT-06）

| 来源 | 数量 | 覆盖什么 | 目录 |
|---|---:|---|---|
| 149 现有 Roce 套件 | 13 | 真实生产用例（含 metadata 十项、`Suite Setup`、库导入） | `fixtures/roundtrip/prod/` |
| 8001 的 461 条中抽取 | 17 | 各技术域典型步骤（iperf、PFC、RDMA、MTU、中断亲和） | `fixtures/roundtrip/legacy/` |
| 手工构造的语法边界 | 20 | 见下表 | `fixtures/roundtrip/syntax/` |

**20 条语法边界的必覆盖清单**（缺一条就不算覆盖）：

| # | 特性 | 为什么必须测 |
|---|---|---|
| 1 | `FOR ... END` 循环 + `IN ENUMERATE` | 缩进敏感，最易被重排 |
| 2 | `IF/ELSE IF/ELSE` | 分支嵌套 |
| 3 | `TRY/EXCEPT/FINALLY` | RF 5+ 特性，上游支持但易漏 |
| 4 | `WHILE` + `limit=` | 循环条件表达式 |
| 5 | 内嵌参数 `${var_${i}}` | 解析器最容易炸的地方 |
| 6 | 转义 `\${not_a_var}` | 字面量 vs 变量 |
| 7 | 续行 `...` 跨行参数 | 换行风格变化会破坏 |
| 8 | `[Template]` + 数据驱动多行 | 一个 test 多行是同一条用例 |
| 9 | `[Teardown]` / `[Setup]` / `[Timeout]` | Settings 段语义 |
| 10 | 中文关键字与中文参数值 | 编码与分词 |
| 11 | 空行与注释密集 | 注释保留率 |
| 12 | `*** Variables ***` 含列表/字典 | `${LIST}  @{...}` |
| 13 | `Library` 带 `WITH NAME` 与参数 | 导入语法 |
| 14 | `Resource` 导入 | 相对路径 |
| 15 | 单行 `Run Keyword If`（旧式） | 兼容老用例 |
| 16 | 极长行（>500 字符 JSON 参数） | 不被折叠或截断 |
| 17 | Tab 与空格混用 | 缩进归一化 |
| 18 | 末尾无换行 | 序列化边界 |
| 19 | CRLF 换行文件 | 跨平台 |
| 20 | 含 emoji / 全角标点的日志消息 | utf8mb4 与长度 |

---

## 6. 保存链路与版本（EDT-09）

```
编辑 → 解析（前端）→ 有错则拦截并提示行号
     → PUT /api/editor/save {fileId, content, sha256, parseOk}
         → 后端比对 sha256：相同 → 200 且不建版本（FR-v0.3-20）
                          → 不同 → 服务端 get_model() 兜底校验
                                   → 通过：更新 content + 建 version（version+1）
                                   → 失败：422 + 行号
```

| 表 | 字段要点 |
|---|---|
| `robot_file` | `content` 唯一真源、`sha256`、`current_version` |
| `robot_file_version` | `file_id`、`version`、`content`、`sha256`、`created_by`、`created_at`、`note` |

**并发保存**：乐观锁。`UPDATE ... WHERE id=? AND current_version=?`，影响行数 0 则返回 409「他人已修改，请刷新对比」。**不做自动合并**（合并 `.robot` 是另一个课题）。

---

## 7. 品牌清理与许可（EDT-07/08）

### 7.1 品牌扫描（脚本化，别靠肉眼）

```bash
# 扫描范围必须含构建产物
grep -rInE "roboscope|viadee|RoboScope" \
  packages/editor/src frontend/src frontend/dist backend/src docs \
  --exclude-dir=node_modules > brand-hits.txt
wc -l brand-hits.txt     # 目标：0
```

**白名单机制**：确需保留的（如 `NOTICE` 里的来源声明、Apache-2.0 许可证原文）用 `brand-allow.txt` 逐行登记路径 + 理由，扫描脚本排除白名单后必须为 0。

> 不要在代码里写「品牌名」变量规避扫描——那是自欺。要么删，要么登记理由。

### 7.2 许可四件事（EDT-08，C 级，人签）

| # | 事项 | 判定 |
|---|---|---|
| 1 | 仓库根保留 `LICENSE`（本项目许可）与 `LICENSE-roboscope`（上游 Apache-2.0 原文） | 两文件存在且未被修改 |
| 2 | `NOTICE` 写明衍生关系与版本 | 含「基于 viadee/roboscope v0.16.1 的编辑器组件衍生，遵循 Apache-2.0」 |
| 3 | 改写过的文件头保留版权声明 + 「Modified by ...」 | 15 个改写文件逐个检查 |
| 4 | 去除上游商标与 logo | 品牌扫描 0 命中 + `assets/` 下无上游 logo |

**Apache-2.0 的实务要点**：允许商用与闭源分发，但必须保留版权声明与 NOTICE，且改过的文件要标注修改。**不做「改名即原创」**——那是许可风险，不是技术问题。

---

## 8. 实施顺序（Wave）

| Wave | 内容 | 中间可验证态 |
|---|---|---|
| **W1** | EDT-01 抽取 + 裁剪 + EDT-08 启动 | `packages/editor` 能独立 build |
| **W2** | EDT-02 改写 15 文件（props 契约） | 一个最小宿主页传入 content 能渲染 |
| **W3** | EDT-03 三视图 + EDT-09 保存链路（并行） | 改一处保存成版本 |
| **W4** | EDT-06 往返保真（**本轮不许并行其他活**） | 50 样本报告 |
| **W5** | EDT-04 关键词面板 + EDT-05 只读 | 面板数据来自接口；只读 403 |
| **W6** | EDT-07 中文与品牌 + DOC-02 | 扫描 0 命中 |

**W4 单独占一个 Wave**：往返保真是 T1，出问题时需要全部注意力；与其他工作并行会让「到底是谁弄坏的」无法定位。

---

## 9. 完成判定

```bash
# 1. 闭包干净（无外部调用）
grep -rInE "axios|fetch\(|XMLHttpRequest" packages/editor/src | grep -v test   # 空

# 2. 构建
npm run build && ls packages/editor/dist

# 3. 19 个上游测试（断言未改）
npm run test -w packages/editor
git diff --stat HEAD~10 -- '**/*.spec.ts'    # 期望：无 spec 文件被改（除配置）

# 4. 往返 50 样本（独立裁判）
python tools/roundtrip_judge.py fixtures/roundtrip --report docs/evidence/v0.3/roundtrip.md
# 期望：L1 dryrun 50/50，L2 AST 等价 50/50

# 5. 三视图切换不丢
npx playwright test e2e/editor-three-views.spec.ts   # 断言 sha256 不变

# 6. 保存与版本
curl -X PUT .../api/editor/save -d '{"fileId":1,"content":"*** Test Cases ***\nT\n    Log    1"}'  # 200, version+1
curl -X PUT .../api/editor/save -d '<同一内容>'                                               # 200, version 不变

# 7. 品牌
bash scripts/brand-scan.sh   # 输出 0（白名单除外）
```

---

## 10. 风险与降级

| 风险 | 信号 | 降级 | 谁批 |
|---|---|---|---|
| 19 个上游测试必须改断言 | 断言改动 >3 处 | **不许改断言**；改为「记录差异 + ADR」，评审决定是接受行为变化还是修实现 | 大师 |
| 往返保真达不到 100% | 50 样本有 3~5 例失败 | 按失败类型收敛：① 注释丢失（接受，≤5%）；② 语义变化（**不可接受**，必须修） | silence |
| `FlowEditor.vue` 去业务化失控 | 改动超 1,500 行 | 流程视图降级为「只读渲染」，编辑只在文本/表单视图 | CCB（§13.3 预案） |
| 中文包合并冲突 | 与平台 i18n 键冲突 >50 处 | 编辑器用独立命名空间 `editor.*`，不与平台共享键 | — |
| 许可义务争议 | 法务/大师提出异议 | 停止发布，补 NOTICE 与文件头；不得以「内部使用」为由跳过 | 大师 |

---

## 11. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | `packages/editor`（39 文件闭包，15 文件改写）、平台宿主页、`/api/editor/*` 接口、版本表与迁移 |
| 文档 | `CONVENTIONS-editor.md`、`ADR-0007 解析器归属`、`ADR-0008 移植边界`、许可说明 |
| 证据 | `docs/evidence/v0.3/`：19 测试输出、50 样本往返报告、三视图 sha256 对比、品牌扫描、保存/版本日志、许可检查单 |
