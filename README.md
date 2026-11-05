# AIDC自动化测试 · AIDC AutoTest

> 产品对外名称：中文 **AIDC自动化测试**，英文 **AIDC AutoTest**。
> 仓库名 `Robot-Autotest`、Maven `artifactId`、npm `name`、Java 包名与数据库名为**工程标识，保持不变**（改名只作用于显示层，见 `docs/plan/多项目与体验基线-评估与设计-20261104.md` §5.5）。

面向 GPU、智能网卡与基础设施的一体化自动化测试平台：把「设备台账 → 用例编写 → 并发执行 → 报告交付 → 定时/CI 自动化」收在同一个系统里，并用 metadata 门禁保证每一份对外报告都能追溯。

> 当前阶段（2026-11-05）：**v1.0 功能面已铺满，进入验收**。95 个任务包中 0 未开始 / 0 进行中 / 82 待验 / 13 已完成，
> 按 **G1 → G2** 成组验收（不按编号逐包审），验收后以小型试点项目跑通全链路、按反馈迭代。
> 开发期遵守 [ADR-0013](docs/adr/ADR-0013-执行安全分级与开发期禁跑令.md)：不在真实 DUT / 交换机执行台账用例。

## 能做什么

| 能力 | 说明 |
|---|---|
| 资产台账 | 设备、端口、链路（DUT-LINK-HOST）、环境档案与技术域标签；凭据加密存储，运行时注入不落盘 |
| 用例管理 | 台账 + 脚本双模；用例/用例集 CRUD、批量导入、能力索引；三视图编辑器（文本 / 表单 / 流程）内容不漂移 |
| 执行引擎 | 状态机、项目 / 设备维度调度、资源租约与强制释放、4 并发不打架；实时日志、取消与超时、失败分类与重试；pabot 编排、工作区落盘、全量日志下载 |
| 报告中心 | 解析 `output.xml`，递归采集 metadata；十项门禁；用例树、原始日志分页、跨运行对比、趋势与通过率；PDF / Word / CSV 导出、一次性打印链接与打印页 |
| 体验与主题 | 中文 / English 双语切换、亮暗主题 + 强调色、表格密度两档；首帧主题不闪，暗色对比度经门禁实测 |
| 自动化 | 执行计划（cron + 时区固定）、依赖 DAG（环检测 / 前置失败阻断）、CI 触发（幂等 + 限流 + 状态回写） |
| 安全与治理 | 登录与令牌、RBAC 权限矩阵、项目级租户隔离、审计日志（只增不改、脱敏）、产物保留策略 |

## 适用场景

- GPU / 智能网卡性能与稳定性回归，多机多卡并发执行。
- 基础设施巡检：连通性、GPU 健康、存储与磁盘挂载。
- 需要对外交付报告的测试：报告自带设备、链路、软件版本等可追溯信息。
- 需要「提交即跑、定时跑」的持续测试流水线。

## 技术架构

- **后端**：Spring Boot 3 + Java 21，分层 `api / application / domain / infrastructure / common`，ArchUnit 约束依赖方向。
- **数据库**：MySQL 8.0，Flyway 版本化迁移（当前 20 个迁移至 `V7_4`），手写 down 脚本；dev / it 双库。
- **前端**：Vue 3 + Vite + TypeScript + Pinia + vue-i18n + naive-ui，Vitest 单测；主题走「localStorage + 服务端偏好 + `data-theme/data-accent/data-density`」三层，naive-ui 主题由语义 token 单向派生。
- **测试内核**：Robot Framework 7.5，兼容 pabot 并发与 robotdashboard 看板。

## 快速开始

前置：JDK 21、Node.js 22、MySQL 8.0（本地或 WSL 均可）。

```bash
# 1. 初始化数据库（库名/账号按需修改）
mysql -uroot -p -e "CREATE DATABASE robot_dev CHARACTER SET utf8mb4;"

# 2. 启动后端（首次启动前先执行 Flyway 迁移与种子数据）
cd backend && mvn -DskipTests package
java -jar target/robot-autotest-backend-0.1.0-SNAPSHOT.jar

# 3. 启动前端
cd frontend && npm ci && npm run dev
```

门禁与集成测试（在仓库根目录）：

```bash
bash scripts/lint.sh        # 密钥扫描 + eslint + 后端编译 + 颜色/对比度/i18n/token/体积/WBS 一致性等检查
bash artifacts/run_it.sh    # 集成测试，连 robot_it 库（内部读取 ~/.config/robot-autotest/env）
```

打开 `http://localhost:5173`，使用内置管理员账号登录；**生产部署前必须修改默认口令**。

## 目录结构

```
backend/          Spring Boot 服务端（api / application / domain / infrastructure / common）
frontend/         Vue 3 前端
packages/editor/  Robot Framework 用例编辑器（三视图）
db/               Flyway 迁移与 down 脚本
scripts/          门禁（lint / 一致性 / 安全扫描）与运维脚本
tools/            设计 token、对比度、i18n、WBS 一致性等检查工具
artifacts/        门禁日志、集成测试入口（run_it.sh）与过程产物
docs/             设计、计划、版本、证据文档
```

> 内网开发以 WSL2 Ubuntu 为准：权威工作副本在 `~/robot-autotest`（ext4），
> 不要在 Windows 侧或 9P 挂载路径上构建（实测慢一个量级）。

## 文档

| 文档 | 用途 |
|---|---|
| [docs/plan/WBS-STATUS.md](docs/plan/WBS-STATUS.md) | **进度唯一真源**：95 个任务包的五态、证据与阻塞 |
| [docs/release/README.md](docs/release/README.md) | 版本地图与每版交付口径 |
| [docs/process/版本开发流程.md](docs/process/版本开发流程.md) | 六份版本产物与准入门槛 |
| [docs/design/美术体系-开发与测试计划-20261104.md](docs/design/美术体系-开发与测试计划-20261104.md) | 设计 token、双语 / 密度 / 暗色判据与实测 |
| [docs/evidence/](docs/evidence/) | 各包验收证据（门禁日志、截图、读数） |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 分支模型、提交规范、PR 与评审规则 |
| [docs/OPS-MANUAL.md](docs/OPS-MANUAL.md) | 服务启停、日志、健康检查、备份恢复、常见故障处置 |
| [docs/ROLLBACK.md](docs/ROLLBACK.md) | 版本回滚步骤与风险 |
| [docs/CI接入指南.md](docs/CI接入指南.md) | CI 触发接口与流水线接入示例 |
| [docs/adr/](docs/adr/) | 架构决策记录 |

## 路线图

1. **验收**：按 G1 → G2 成组审完 82 个待验包，逐个贴出可复现命令或证据路径后才置 `已完成`。
2. **试点**：搭建小型试点测试项目，覆盖「建项目 → 编用例 → 并发执行 → 出报告 → 定时/CI」全链路。
3. **迭代**：按试点反馈优先修可用性问题，再补能力缺口；每个迭代产出可复现的验证记录，能力不倒退。

## 许可

第三方组件许可与归属见 [packages/editor/NOTICE.md](packages/editor/NOTICE.md)（编辑器部分源自 RoboScope，Apache-2.0）。
