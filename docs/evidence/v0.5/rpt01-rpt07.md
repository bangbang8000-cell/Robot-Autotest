# RPT-01 / RPT-07 交付说明与证据（v0.5）

> 生成时间：2026-11-03
> 全量测试证据：`docs/evidence/v0.5/mvn-test-rpt01.log`

## 1. 改动文件清单

### 主代码

| 文件 | 说明 |
|---|---|
| `backend/src/main/java/com/qa/autotest/domain/report/MetadataGate.java` | 十项 metadata 门禁（纯 POJO，无框架） |
| `backend/src/main/java/com/qa/autotest/domain/report/GateResult.java` | 门禁结果（exportable / missing / tooLong / missingCsv） |
| `backend/src/main/java/com/qa/autotest/domain/report/ParsedReport.java` | 解析结果聚合 |
| `backend/src/main/java/com/qa/autotest/domain/report/SuiteNode.java` | 套件节点 |
| `backend/src/main/java/com/qa/autotest/domain/report/CaseNode.java` | 用例节点（含失败摘要与关键字） |
| `backend/src/main/java/com/qa/autotest/domain/report/KeywordNode.java` | 关键字节点（供 RPT-02） |
| `backend/src/main/java/com/qa/autotest/domain/report/ReportRecord.java` | 报告视图（详情与列表共用） |
| `backend/src/main/java/com/qa/autotest/application/report/OutputXmlParser.java` | StAX 事件流解析；metadata 递归合并 |
| `backend/src/main/java/com/qa/autotest/application/report/ReportIngestService.java` | 编排：定位 output.xml → 解析 → 门禁 → 同事务入库 |
| `backend/src/main/java/com/qa/autotest/application/report/ReportQueryService.java` | 详情 / 分页查询 |
| `backend/src/main/java/com/qa/autotest/application/repository/ReportRepository.java` | 报告仓储接口 |
| `backend/src/main/java/com/qa/autotest/infrastructure/ReportRepositoryJdbc.java` | 仓储 JDBC 实现（run / test_result / keyword_result） |
| `backend/src/main/java/com/qa/autotest/api/report/ReportController.java` | `/api/reports` 三个端点 |

### 数据库迁移

| 文件 | 说明 |
|---|---|
| `db/migration/V5_1__report_fields.sql` | run / test_result / keyword_result 报告域字段，逐列中文 COMMENT |
| `db/down/V5_1__report_fields.down.sql` | 逐列 `DROP COLUMN IF EXISTS`，可重入 |

### 测试与证据

| 文件 | 说明 |
|---|---|
| `backend/src/test/java/com/qa/autotest/domain/report/MetadataGateTest.java` | 9 条门禁用例 |
| `backend/src/test/java/com/qa/autotest/application/report/OutputXmlParserTest.java` | 7 条解析用例 |
| `backend/src/test/java/com/qa/autotest/application/report/ReportIngestServiceTest.java` | 4 条服务层用例（伪造 Repository） |
| `backend/src/test/java/com/qa/autotest/migration/MigrationFileTest.java` | **新增** 1 条 V5_1 校验方法（未改动既有断言） |
| `backend/src/test/resources/fixtures/output-xml/pabot-merged.xml` | 手写 pabot 合并 fixture |
| `docs/evidence/v0.5/mvn-test-rpt01.log` | 全量 `mvn -B test` 真实输出 |
| `docs/evidence/v0.5/rpt01-rpt07.md` | 本文件 |

## 2. API 行为

| 端点 | 说明 |
|---|---|
| `POST /api/reports/{runId}/parse?projectId=&outputXmlPath=` | 解析并入库；`outputXmlPath` 可选 |
| `GET /api/reports/{runId}?projectId=` | 详情：runId / projectId / state / exportable / metadataMissing / metadata / counts{pass,fail,skip} / startedAt / finishedAt |
| `GET /api/reports?projectId=&page=&size=` | 分页列表（含 exportable / metadataMissing / counts） |

三个端点均在控制器调用 `access.bindProject(projectId)`，再由应用服务按 `EXECUTION:READ` 判权。

### output.xml 定位顺序（不硬编码新目录）

1. 调用方显式传 `outputXmlPath`；
2. `run_artifact` 中 `kind=output_xml` 且文件存在的登记（v0.4 执行链路已登记）；
3. `run.artifacts_dir` 下的 `shard-0/output.xml` 或 `output.xml`；
4. 仍不存在则返回 400，提示显式传路径。

## 3. pabot-merged.xml fixture 结构

```
Pabot Merged（根套件：CASE_ID / QA_PROJECT / OPERATOR=qa）
├─ Shard-1（RUN_ID / LINK）
│   ├─ 测试：带宽达标（PASS，Run Iperf）
│   └─ Sub-Env（第 3 层：DUT / SW_BUILD）
│        └─ 测试：环境自检（PASS，Collect Env）
├─ Shard-2（OPERATOR=ops 覆盖父级；CASE_KIND）
│   └─ 测试：丢包判定（FAIL，Check Packet Loss，含 FAIL 消息）
└─ Shard-3（旧 <metadata><item> 格式：BASELINE / STARTED_AT）
     └─ 测试：外部依赖跳过（SKIP）
```

- 三层嵌套：`Pabot Merged → Shard-1 → Sub-Env`，用于验证递归 metadata 合并。
- 覆盖：根套件 `OPERATOR=qa`，Shard-2 改为 `ops`，合并结果为 `ops`；根节点自身仍保留 `qa`（供来源追溯）。
- 旧格式：Shard-3 使用 `<metadata><item name="..." value="..."/></metadata>`。
- 计数：PASS=2、FAIL=1、SKIP=1，合计 4。

## 4. 期望值与实测值

| 校验点 | 期望 | 实测 |
|---|---|---|
| metadata 合并键数 | 10 | 10 |
| DUT 来源 | 第三层 Sub-Env | `GPU-01` |
| OPERATOR 覆盖 | `ops` | `ops` |
| PASS / FAIL / SKIP | 2 / 1 / 1 | 2 / 1 / 1 |
| 完整 metadata → exportable | true | true |
| 空 metadata → missing | 十项全缺 | 十项全缺 |
| 破损 XML | IllegalArgumentException | 抛出且消息含「output.xml 解析失败」 |
| 重复解析 test_result 行数 | 4（不翻倍） | 4 |
| MigrationFileTest V5_1 | 通过 | 通过 |

## 5. 全量测试原样摘录

```
[INFO] Results:

[INFO] Tests run: 168, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

新增用例分项（同样来自日志）：

```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in com.qa.autotest.application.report.OutputXmlParserTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.qa.autotest.application.report.ReportIngestServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in com.qa.autotest.domain.report.MetadataGateTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.qa.autotest.migration.MigrationFileTest
```

## 6. 关键设计说明与既有事实核对

1. **`run.started_at` 不重复新增**：V1 基线已有该列，V5_1 只复用；`metadata.STARTED_AT` 解析后写回该列（为 null 时保留原值）。
2. **`metadata_json` 为新增列**：DEV-PLAN §2.2 只拆列了 8 个键，`CASE_ID` / `QA_PROJECT` 无独立列；为满足 `GET /api/reports/{runId}` 回传「十项键值」，增加 `run.metadata_json TEXT` 存完整键值，拆列只作查询索引用途。
3. **`operator` 与既有 `operator_name` 并存**：`operator` 对应 `metadata.OPERATOR`（≤40 字符），`operator_name` 保留原执行人账号语义。
4. **`test_result` / `keyword_result` 未登记 TenantGuard**：任务书假设二者在名单内，实际 `TenantGuard.TENANT_TABLES` 只登记了 `run`；且这两张表 V1 基线没有 `project_id` 列，无法满足 `requireProjectScope`。因此本包不改 TenantGuard，子表只在已通过 `run` 项目校验后按 `run_id` 写入（与既有 `TestResultWriterJdbc` 口径一致），该说明已写入 `ReportRepositoryJdbc` 类注释。
5. **超长值截断**：门禁判定超长后 `exportable=false`；写 `VARCHAR(40)` 列前按 40 截断，完整值仍保留在 `metadata_json`，避免 MariaDB 严格模式报「Data too long」。

## 7. 未覆盖项 / 风险

- **真实 pabot 产物未在本机生成**：fixture 为手写合法 XML，覆盖 RF 7.5 `<meta>` 与旧 `<metadata><item>` 两种格式；真实 pabot 合并文件的额外节点（如 `<statistics>` 之外的自定义标签）未验证。
- **V5_1 未在真实 MariaDB 执行**：本仓库测试不启动数据库，迁移只做了文件级校验（字段名、down 脚本可重入）；Flyway 实际执行需在部署环境验证 `DROP COLUMN IF EXISTS` 的 MariaDB 版本支持。
- **API 未做 MockMvc 测试**：本包按任务要求只覆盖领域 / 解析 / 服务层；控制器仅经编译与 ArchUnit 分层校验。
- **Jacoco 在 JDK 25 下告警**：日志出现 `IllegalClassFormatException`（JaCoCo 0.8.12 对部分 JDK 内部类插桩失败），不影响测试结果与 `BUILD SUCCESS`。
