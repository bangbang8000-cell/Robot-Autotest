# db

| 目录 | 内容 | 规则 |
|---|---|---|
| `migration/` | Flyway 正向脚本 `V1__baseline.sql` … | **只增不改**，已执行脚本不得改字节 |
| `down/` | 手写回滚 `V1__baseline.down.sql` | Flyway 社区版无 undo，回滚需自备 |

- 归属：**WS-04**（schema v1，38 表分域）。
- 迁移评审必须同时看 up 与 down；CI 校验空库 `up → down → up` 成功（ADR-0003）。
- 唯一业务库为 MariaDB；集成测试用 149 本机独立库 `robot_it`，不用 H2（ADR-0002）。

## 1. 38 张表的分域（v0.1 冻结）

| 域 | 表数 | 表名 |
|---|---:|---|
| 项目 | 3 | `project`、`project_setting`、`project_label` |
| 身份 | 5 | `app_user`、`role`、`user_role`、`project_member`、`api_token` |
| 资产 | 7 | `device`、`device_port`、`link`、`credential`、`device_check`、`env_profile`、`topology_node` |
| 用例 | 7 | `test_case`、`test_case_step`、`test_set`、`test_set_item`、`robot_file`、`robot_file_version`、`case_requirement` |
| 执行 | 8 | `execution_job`、`execution_queue`、`resource_lease`、`execution_event`、`run`、`test_result`、`keyword_result`、`run_artifact` |
| 计划 | 3 | `schedule`、`schedule_dependency`、`schedule_trigger_log` |
| 治理 | 5 | `audit_log`、`notification`、`system_setting`、`retention_policy`、`export_record` |

表名取自基线报告 §1027~1033 的枚举，**一表不改名**。

## 2. 命名与字段规范

| 项 | 规则 | 理由 |
|---|---|---|
| 表名 | `snake_case`，**按域前缀命名**（`app_` / `project_` / `device_` / `test_` / `execution_` / `schedule_` / 治理域无前缀） | 38 张表必须有分组线索 |
| 主键 | 统一 `id BIGINT AUTO_INCREMENT` | 便于与外部 ID（RUN_ID）分离 |
| 业务唯一键 | `uk_<表>_<语义>` | 约束命名可读，报错能定位 |
| 时间 | `DATETIME(3)` | 毫秒精度，避免同秒并列 |
| 审计四列 | 每表必须有 `created_at` / `created_by` / `updated_at` / `updated_by` | `TC-v0.1-023` 硬检项 |
| 外键 | 只加在强关系上，弱引用只加索引 | 避免迁移地狱 |
| 字符集 / 引擎 | `utf8mb4` / `InnoDB` | 中文用例标题必需 |

### 2.1 两处口径澄清（评审时重点看）

这两条在 PRD/基线报告里表述有张力，本版按下面落地，若评审否决请改并同步 PRD：

1. **表名用单数**。PRD FR-v0.1-14 写「表 `snake_case` 复数」，但同文 §4 的例子与基线报告
   §1027~1033 的 38 表枚举**全是单数**（`app_user`、`device`、`test_case`）。既然「38 张表」
   由那份枚举定义，本版以枚举为准，采用**单数**。若确实要复数，改的是 `V1` 的 38 个表名 + down 脚本。
2. **`resource_lease` 的主键仍是 `id`**，业务互斥靠 `uk_resource_lease_key(resource_key)` 唯一约束。
   FR-v0.1-15 写「`resource_key` 主键」，但同一份 FR 又要求「主键统一 `id BIGINT`」。
   两种写法在数据库层的**可观察行为完全一致**（重复插入同样报 `Duplicate entry`），
   取 `id + uk_` 可以同时满足命名规范与互斥语义。

## 3. 常用命令

```bash
make db-migrate   # flyway migrate（V1 建 38 表）
make db-undo      # 按版本倒序执行 db/down/*.down.sql（回滚到空库）
make db-reset     # DROP + CREATE 测试库后重新 migrate（CI 与集成测试前必跑）
make db-audit     # 38 表规范体检：审计四列 / 主键 / 时间类型 / 字符集
```

数据库连接参数全部来自环境变量 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`；
仓库里不落任何明文口令（FR-v0.1-07）。

## 4. 允许缺字段的表

`execution_job`、`execution_queue`、`resource_lease`、`run`、`test_result`、`keyword_result`、
`run_artifact`、`schedule`、`schedule_trigger_log`、`retention_policy`、`export_record`
在 v0.1 只建骨架（主键 + 域内关键唯一键），字段由 v0.4 / v0.5 / v0.6 用**新增迁移**补齐。
