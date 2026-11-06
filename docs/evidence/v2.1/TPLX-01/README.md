# TPLX-01 证据 · 报告模板与字段白名单抽取

- **执行时间**：2026-11-06 21:20（GMT+8）
- **链路**：真实 HTTP（`:8001` → `:8081`）+ 真实 MySQL `robot_dev`（V9_2 已应用）+ jsdom 组件测试
- **结论**：**通过**。HTTP 17 项全 PASS；后端单测 11 条、渲染器回归 4 条、dump 1 条、前端组件 5 条全绿；
  抽取前后 EXTERNAL 渲染 **diff 为空**；`db-audit` 7 项 PASS（64 表）。
  过程中抓到一个**单测漏掉的真实缺陷**（创建模板没写字段，见 §4）。

## 1. 命令原文

```bash
cd ~/robot-autotest
# 迁移（真实库建表 + 内置 DEFAULT 模板）
bash scripts/db-migrate.sh
# 后端单测
cd backend && mvn -o -B test -Dtest=ReportTemplateServiceTest,ReportSnapshotTest,ReportViewRenderDumpTest
# 真实 HTTP 验收
cd .. && python3 docs/evidence/v2.1/TPLX-01/run.py
# 前端组件
cd frontend && npx vitest run src/views/ReportTemplatesView.spec.ts
# 库结构
cd .. && bash scripts/db-audit.sh
```

前置：后端 jar 含本包代码并已重启；`~/.config/robot-autotest/env` 内有 `QA_E2E_PASSWORD`。

## 2. 判据逐条核对

DEV-PLAN §10.5 三条判据 + 「真实链路」附加项：

| # | 判据 | 实测 | 结论 |
|---|---|---|---|
| 1 | 白名单外字段**接口侧**拦截 | 提交 `rawOutput` / `commands` → HTTP 400，消息点名两个非法键，且**未落库**（`TPLX01-07/08`） | 通过 |
| 2 | 白名单外字段**前端侧**不可选 | 勾选项 = `GET /fields` 返回的 21 项；平台模板里的 `swBuild` 因不在清单而不出现（`ReportTemplatesView.spec.ts` 第 1 条） | 通过 |
| 3 | 顺序持久化 | 提交顺序即 `sort_order`（0..n-1，服务端重排、不采信客户端序号）；上移后 payload 顺序随之变化（组件第 3 条 + `TPLX01-05`） | 通过 |
| 4 | 平台级模板可被多项目引用 | 平台 DEFAULT 在项目 29 与项目 44 的列表都可见；改它返回 400「平台级模板为内置」（`TPLX01-01/14`） | 通过 |
| 5 | 抽常量前后 EXTERNAL 渲染逐字段一致 | `render-before.json` 与 `render-after.json` 各 25 行，`diff` 为空 | 通过 |
| 6 | 必填字段不可删 | 服务端 400 并列出缺失项（`TPLX01-09`）；前端勾选框禁用且不可移除 | 通过 |
| 7 | 跨项目隔离 | 项目 44 读项目 29 的模板 → 403「模板属于其它项目」（`TPLX01-15`） | 通过 |
| 8 | 同项目重名 | HTTP 409（唯一键 `uk_report_template_name` 真的生效，不是 500） | 通过 |

## 3. 原始输出

`run.log` 摘要（完整见同目录 `run.log`）：

```
# 夹具项目 projectId=29，另一项目 projectId=44，已登录 qa-e2e-admin

PASS  TPLX01-01   列表 200 且可见平台级 DEFAULT       HTTP 200 模板数=1
PASS  TPLX01-02   内置模板落库 21 个字段（sort 0..20）   字段数=21
PASS  TPLX01-03   内置模板字段顺序 = 白名单声明顺序         前三个=['reportId','title','version']
PASS  TPLX01-04   可选字段 200 且 21 项，首尾与白名单一致   HTTP 200 项数=21
PASS  TPLX01-05   建模板 200，version=1，顺序按提交顺序   HTTP 200 version=1 字段数=9
PASS  TPLX01-06   自定义标题落库                     passRate.titleOverride=通过率
PASS  TPLX01-07   白名单外字段 400 且点名非法键          HTTP 400 字段不在 C5 白名单内: rawOutput, commands
PASS  TPLX01-08   非法请求未落库                     落库行数=0
PASS  TPLX01-09   缺必填字段 400 且列出缺失项            HTTP 400 模板缺少必填字段: watermark, frozenAt, version, title, disclaimer
PASS  TPLX01-10   重复字段 400                     HTTP 400 字段重复: passRate
PASS  TPLX01-11   同项目重名 409                    HTTP 409 同项目下已存在同名模板: tplx01-硬件组视图
PASS  TPLX01-12   更新 200 且 version=2、字段数=7      HTTP 200 version=2 字段数=7
PASS  TPLX01-13   库里 version=2 且字段被整体替换为 7 条  version/字段数=2/7
PASS  TPLX01-14   改平台级模板 400 且提示平台级          HTTP 400 平台级模板为内置模板，本版不可修改
PASS  TPLX01-15   跨项目读模板 403 且点名『属于其它项目』    HTTP 403 模板属于其它项目: 3
PASS  TPLX01-16   删除返回 200                    HTTP 200
PASS  TPLX01-17   删除后再取 404                   HTTP 404

合计 17 项，FAIL 0 项
```

单测与组件测试：

```
Tests run: 11, Failures: 0  -- ReportTemplateServiceTest
Tests run:  4, Failures: 0  -- ReportSnapshotTest（渲染器原有断言全部仍成立）
Tests run:  1, Failures: 0  -- ReportViewRenderDumpTest
✓ src/views/ReportTemplatesView.spec.ts (5 tests)
```

抽取前后 diff：

```
$ diff -u render-before.json render-after.json
（空）
$ wc -l render-before.json render-after.json
  25 render-before.json
  25 render-after.json
```

## 4. 抓到的缺陷与修法（本节是全篇重点）

**现象**：`TPLX01-05` 首轮读数是「字段数=0」——建模板返回 200、`version=1` 都对，但 `fields` 是空的。

**根因**：`ReportTemplateService.create` 只调了 `templates.insert(draft)`。真实实现里 `insert`
只往主表 `report_template` 写一行，字段是另一张表 `report_template_field`，必须再走一次
`replaceFields`。服务层漏了这一步。

**为什么单测全绿**：伪仓储 `FakeTemplateRepository.insert` 把 `draft.fields()` 一起存了
（"好心的实现"），于是「写了对象就等于写了字段」。真实 JDBC 实现没有这个语义，
测试就骗过了自己。

**修法**（两件事一起做，否则下次还会漏）：

1. 服务层补 `templates.replaceFields(projectId, id, fields, null)`；
2. **把伪仓储改得"不善良"**：`insert` 只存空字段列表，与真实实现同契约；
   再加一条用例「创建时字段必须真的落库」断言 `replaceFields` 被调用过一次。
   这样服务层再漏写，单测当场就红。

> 这是本轮第 2 个「单测绿、真实链路红」的例子（第 1 个是 ORCH-02 的 SVG 黑块）。
> 结论一致：**伪实现要贴着真实契约写，宁可少给，不要多给**。

## 5. 设计决定（后续维护要看）

| 决定 | 理由 | 反例（如果做成另一种） |
|---|---|---|
| 白名单抽成 `ReportFieldWhitelist`，渲染器只引用 | 模板要给用户「勾哪些字段」，这份清单会被接口 / 界面 / 渲染器三处引用，各写一份必然漂移 | 常量留在渲染器里 → 模板能选到的字段，导出时被判非法 |
| 模板只存 `field_key` + `sort_order`，**不存字段表达式** | 导出的取值逻辑只有一处（渲染器），模板只决定「要哪些、什么顺序」 | 模板里写取值表达式 → 两套渲染逻辑，导出一致性无从保证 |
| 必填字段不可从模板删除（水印 / 免责声明 / 标识 / 冻结时间） | 允许删水印 = 给了一条绕过 C5 的路 | 全可选 → 有人导出一份没水印的对外报告 |
| 平台级模板 `project_id = 0` 且**本版只读** | 与 V2_5 编辑器关键词同一套约定；跨项目共用的模板被某个项目改掉会波及所有人 | 任何项目都能改平台模板 → A 项目改一下 B 项目导出跟着变 |
| `sort_order` 由服务端按提交顺序重排 | 前端拖排可能出现两个同序号，采信客户端的序号会让顺序不确定 | 采信客户端序号 → 同序号时导出顺序随机 |
| 字段更新走「全量替换」（先删后插） | 模板字段是整体配置，逐条 diff 没有收益；历史可追溯性由报告快照承担 | 逐条 upsert → 删掉的字段残留，导出多出没配过的列 |
| `version` 每次更新自增 | TPLX-02 的快照要回指「冻结时用的哪一版模板」，没有版本号就无法证明「改模板不影响已冻结报告」 | 无版本号 → 只能靠时间戳猜 |

## 6. 遗留

| # | 事项 | 影响 | 归属 |
|---|---|---|---|
| 1 | 前端入口只放在报告页工具栏，未进左侧一级菜单 | 用户要先进「报告」才看得到 | 有意为之：模板是报告的子配置，不是独立工作台 |
| 2 | `GET /fields` 只返回 key，标题由前端语言包映射 | 新增白名单字段时前端要同步加一条文案，否则显示原始 key（不会崩） | 有意为之：后端不存文案，避免多语言数据进库 |
| 3 | 平台级模板本版不可编辑 | 想改内置模板只能新建项目模板 | 下一版若出现「管理员维护平台模板」需求，需先补权限资源 `REPORT` |
| 4 | 模板尚未参与导出（只存配置） | 现在改模板不影响任何导出结果 | **TPLX-02**（冻结时按模板渲染 + xlsx 分支） |
