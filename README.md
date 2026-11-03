# Robot-Autotest 自动化测试平台

面向 GPU、智能网卡与基础设施的一体化自动化测试平台：把「设备台账 → 用例编写 → 并发执行 → 报告交付 → 定时/CI 自动化」收在同一个系统里，并用 metadata 门禁保证每一份对外报告都能追溯。

> 当前阶段：**v1.0 能力已就绪，进入迭代开发**。后续以小型试点项目持续验证，按实际使用反馈迭代优化。

## 能做什么

| 能力 | 说明 |
|---|---|
| 资产台账 | 设备、端口、链路（DUT-LINK-HOST）、环境档案与技术域标签；凭据加密存储，运行时注入不落盘 |
| 用例管理 | 台账 + 脚本双模；用例/用例集 CRUD、批量导入、能力索引；三视图编辑器（文本 / 表单 / 流程）内容不漂移 |
| 执行引擎 | 状态机、资源租约、4 并发不打架；实时日志、取消与超时、失败分类与重试；pabot 编排 |
| 报告中心 | 解析 `output.xml`，递归采集 metadata；十项门禁；用例树、原始日志分页、跨运行对比、趋势与通过率；PDF / Word / CSV 导出与一次性打印链接 |
| 自动化 | 执行计划（cron + 时区固定）、依赖 DAG（环检测 / 前置失败阻断）、CI 触发（幂等 + 限流 + 状态回写） |
| 安全与治理 | 登录与令牌、RBAC 权限矩阵、项目级租户隔离、审计日志（只增不改、脱敏）、产物保留策略 |

## 适用场景

- GPU / 智能网卡性能与稳定性回归，多机多卡并发执行。
- 基础设施巡检：连通性、GPU 健康、存储与磁盘挂载。
- 需要对外交付报告的测试：报告自带设备、链路、软件版本等可追溯信息。
- 需要「提交即跑、定时跑」的持续测试流水线。

## 技术架构

- **后端**：Spring Boot 3 + Java 21，分层 `api / application / domain / infrastructure / common`，ArchUnit 约束依赖方向。
- **数据库**：MySQL 8.0 / MariaDB，Flyway 版本化迁移，手写 down 脚本。
- **前端**：Vue 3 + Vite + TypeScript + Pinia + vue-i18n。
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

打开 `http://localhost:5173`，使用内置管理员账号登录；**生产部署前必须修改默认口令**。

## 目录结构

```
backend/          Spring Boot 服务端（api / application / domain / infrastructure / common）
frontend/         Vue 3 前端
packages/editor/  Robot Framework 用例编辑器（三视图）
db/               Flyway 迁移与 down 脚本
scripts/          运维与稳定性脚本（回滚、诊断）
docs/             设计、运维、CI 接入与版本文档
```

## 文档

| 文档 | 用途 |
|---|---|
| [CONTRIBUTING.md](CONTRIBUTING.md) | 分支模型、提交规范、PR 与评审规则 |
| [docs/OPS-MANUAL.md](docs/OPS-MANUAL.md) | 服务启停、日志、健康检查、备份恢复、常见故障处置 |
| [docs/ROLLBACK.md](docs/ROLLBACK.md) | 版本回滚步骤与风险 |
| [docs/CI接入指南.md](docs/CI接入指南.md) | CI 触发接口与流水线接入示例 |
| [docs/adr/](docs/adr/) | 架构决策记录 |

## 路线图

1. 搭建小型试点测试项目，覆盖「建项目 → 编用例 → 并发执行 → 出报告 → 定时/CI」全链路。
2. 按试点反馈迭代：优先修可用性问题，再补能力缺口。
3. 每个迭代产出可复现的验证记录，能力不倒退。

## 许可

第三方组件许可与归属见 [packages/editor/NOTICE.md](packages/editor/NOTICE.md)（编辑器部分源自 RoboScope，Apache-2.0）。
