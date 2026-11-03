# CAS-01 双模用例模型评审纪要

**日期** 2026-11-02 ｜ **WBS** CAS-01 ｜ **门禁** G2 冻结窗口 ｜ **结论** 通过，模型冻结

**参加** 大师（主持）、silence（技术） ｜ **记录** AI ｜ **依据** v0.2 PRD FR-v0.2-16/17、
DEV-PLAN §2.2、基线报告 §11.2 / R3

---

## 1. 评审要解决的问题

| # | 问题 | 结论 |
|---|---|---|
| 1 | 台账与脚本是同一张表还是两张表 | **两张表**：`test_case`（台账）+ `robot_file`（脚本） |
| 2 | 脚本内容存文本还是结构化 AST | **只存文本**，`robot_file.content` 是唯一真源，不存 AST |
| 3 | 台账与脚本的绑定强度 | **弱绑定**，不建外键；用 `[Tags] case:<code>` 或 `case_requirement` 回链，允许 1:0..1 / 1:N |
| 4 | 编号唯一性范围 | **项目内唯一**（`uk_test_case_project_code`），跨项目可同名 |
| 5 | 版本管理是否用 Git | 版本用 `robot_file_version` 快照；Git 是异步另一件事，运行时**不依赖 Git 工作区** |

## 2. 冻结的模型（不可协商）

1. `robot_file.content` 是唯一真源，编辑器读写它、执行时落盘为临时 `.robot`。
2. 台账可以没有脚本；一个脚本可以挂多条台账（数据驱动）。
3. 版本层是快照表，不是 Git；每次保存生成 `robot_file_version`。
4. 台账字段枚举受控：`priority ∈ {P0,P1,P2,P3}`；
   `review_status ∈ {draft,pending,approved,rejected}`。
5. 编号格式 `^[A-Z]{2,4}-[A-Z]{2,4}-[0-9]{3}$`（如 `IM-FUN-001`），导入时校验，非法即报错。

## 3. 对 v0.2 计划的落地修订

| 项 | 原计划 | 评审后 |
|---|---|---|
| ADR 编号 | 双模模型写成 ADR-0004（与 CI runner 冲突） | 改 **ADR-0005**；租户隔离顺延 **ADR-0006** |
| 台账编号字段 | PRD 写 `external_id` | 沿用 V1 已建的 `case_code`（语义相同，避免改 V1 历史字节），对外文档标注 `external_id = case_code` |
| 脚本内容字段 | DEV-PLAN 写 `content NOT NULL` | 保留 V1 的 `content NULL`（新建文件允许先建空文件），`content_sha256` 允许空；保存时由应用层写入 |

## 4. 明确不做（本版）

- 不存 AST、不做脚本结构检索；
- 不做「一台账强制绑一脚本」；
- 不做 Git 同步；
- 不做脚本解析与语法高亮（v0.3）。

## 5. 风险与复核触发

| 风险 | 应对 |
|---|---|
| 跨表一致性（删台账不删脚本） | 本版只做独立 CRUD；绑定清理在 v0.3 EDT-09 统一处理 |
| 按结构检索诉求出现 | 新增独立索引表，不得把 AST 塞回 `robot_file` |
| 导入编号格式与历史 Excel 不符 | 先出「失败行清单」交大师确认，不猜值、不放宽规则 |

## 6. 签署

- 模型冻结：**通过**。G2 之后改模型须新建 ADR 并评审。
- 关联文档：`docs/adr/0005-dual-mode-case-model.md`、`docs/release/v0.2-ledger/DEV-PLAN.md`
