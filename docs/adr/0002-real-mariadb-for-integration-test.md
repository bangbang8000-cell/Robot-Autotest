# ADR-0002 · 集成测试用真实 MariaDB 独立库，不用 H2 / Testcontainers

> 环境口径更新（2026-11-03）：149 已停用，现统一使用本地 WSL。当前环境实际使用 MySQL 8.0（`127.0.0.1:3306`，业务库 `robot_dev`）；本 ADR 保留当时「真实数据库独立库、不容器化」的选型结论，集成测试仍用本机独立库。

- **状态**：已采纳（**2026-11-04 修订**：实际数据库为 **MySQL 8.0**，MariaDB 转为待验项，见文末修订记录）
- **日期**：2026-11-02
- **决策人**：silence（G0 纪要 P5 相关技术裁定）｜**修订批准**：大师（2026-11-04）
- **约束范围**：v0.1 起所有集成测试

## 背景

- 规划初稿写的是 `Testcontainers(MariaDB)`；
- 基线报告 §4.2 实测 **本地 WSL 未安装 Docker**（`command -v docker` 为空），且规划 §3.2 明确不做容器化；
- 本地开发机（Windows）也没有可用的 Docker 环境可与内网互通。

## 选项

| 选项 | 优点 | 代价 | 结论 |
|---|---|---|---|
| ① 给本地 WSL 装 Docker，继续用 Testcontainers | 测试自带隔离，最标准 | 违反「不做容器化」；本地 WSL 是自用老机器，装 Docker 有风险 | 不选 |
| ② Testcontainers 连远程 Docker daemon | 复用容器方案 | 要暴露 daemon，安全与网络都别扭 | 不选 |
| ③ **真实数据库独立库**（本地 WSL 本机 `robot_it`，现为 MySQL 8.0） | 与生产同版本同语义，零额外组件 | 同库不能并行跑集成测试 | **选** |
| ④ H2 内存库 | 最快、最省事 | `DATETIME(3)`、`JSON`、`ON UPDATE` 语义与 MariaDB 不同，**测试会骗人** | 明确否决 |

## 决定

采用 **③**：
- CI 与集成测试统一连本地 WSL 本机 MySQL 8.0 的 **`robot_it`** 库，每轮 `DROP + CREATE + flyway migrate`；
- 本地开发用 **`robot_it_local`**（走 SSH 隧道），与 CI 库物理隔离；
- 库名必须以 `_it` 结尾，`SchemaIT` 会断言这一点，防止误清开发库。

## 后果

- 正面：迁移脚本在「真实库」上验证过才进 PR，不会出现「CI 过、真库崩」。
- 负面：集成测试必须串行（同一库互斥）；`mvn test` 默认跳过 `@Tag("it")`，CI 显式 `-Dgroups=it`。
- 复核触发条件：本地 WSL 迁到支持容器的新机器，或集成测试在单库上排队超过 10 分钟。

## 关联

- WBS：WS-05、FR-v0.1-19、TC-v0.1-020~028
- 文件：`backend/src/test/java/com/qa/autotest/db/SchemaIT.java`、`scripts/db-reset.sh`
- 依据：基线报告 §4.2（按最小依赖约定不装 Docker）

## 修订记录

| 日期 | 修订 | 批准 | 理由与影响 |
|---|---|---|---|
| 2026-11-04 | **数据库引擎由「MariaDB」改为「MySQL 8.0 已验证 + MariaDB 待验」** | 大师 | **事实**：2026-11-03 实测本机（WSL `Ubuntu-24.04`）运行的是 **MySQL 8.0**（`127.0.0.1:3306`），Flyway 已在其上迁到 **V7_1**（17 个迁移全部 applied，`docs/evidence/v1.0/flyway-migrate-v7_1.log` 首行 `Database: jdbc:mariadb://127.0.0.1/robot_dev (MySQL 8.0)` 即证）。**当时未装 MariaDB**，ADR 原文与实际环境不符。<br>**处理**：保留「真实数据库、独立库、不用 H2 / 不容器化」的选型结论不变（该结论是 ADR 的实质），仅把引擎从「MariaDB」放宽为「MySQL 8.0（已验证）」；**MariaDB 兼容性列为待验项**，进入 `ENV-03`。<br>**风险仍在，未关闭**：`uk_resource_lease_key` 并发语义、`DATETIME(3)` 精度、`ON UPDATE CURRENT_TIMESTAMP` 在两个引擎上的实现细节不完全相同；若后续要交付到 MariaDB 环境，须在 MariaDB 上重跑全量迁移 + `mvn -Pit test`。<br>**为什么不直接装 MariaDB**：验收已启动，装第二个库会阻塞 9 个包的验证；先把 MySQL 8.0 上的事实记录下来，把 MariaDB 作为收尾回归项更划算（大师 2026-11-04 决定）。 |
| 2026-11-03 | 环境口径更新：149 停用，统一本地 WSL（`Ubuntu-24.04`） | — | 记录在文首注记，不改决策 |
