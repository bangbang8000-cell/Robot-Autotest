# AGENTS.md — 工程总纲（骨架）

> 本文是 **DOC-01** 的骨架版本（G0 门禁产物）。当前只记录「已经成立的事实与操作方式」，
> 随 WS-02~05 落地持续补全。技术结论以基线报告为准，任务与门禁以规划为准。

## 1. 仓库定位

单仓（monorepo），三块可独立构建：`backend`（Spring Boot）、`frontend`（Vue 3 + Vite）、
`packages/editor`（衍生自 RoboScope v0.16.1，Apache-2.0）。执行内核为 Robot Framework 7.5 + pabot。

| 事实 | 值 |
|---|---|
| 主协作仓 | `git@github.com:huanchen811509-silence/Robot-Autotest.git` |
| 内网镜像 | `ssh://silence@10.202.54.201:22/volume1/git/repos/robot-autotest.git` |
| 分支保护 | `main` 受保护，改动走 PR |
| 进度真源 | `docs/plan/WBS-STATUS.md`（56 个任务包，四态） |
| 任务来源 | `docs/自动化测试平台中长期工作规划.md` v2.0 |
| 技术基线 | `docs/自动化测试平台调研与架构评估报告_v4.1_20261102.md` |

## 2. 目录与归属

| 目录 | 内容 | 归属 WBS | 当前状态 |
|---|---|---|---|
| `backend/` | Spring Boot 3 / Java 21 / Maven，包内分层 `api/application/domain/infrastructure/common` | WS-02 | 骨架可构建：`mvn -B test` 13 用例（ArchUnit R1~R5 + 无环）+ `/api/_ping` + 统一响应体 |
| `frontend/` | Vue 3 + Vite + TS + Pinia + i18n | WS-03 | 骨架可构建：6 路由 + 语言切换 + `api/http.ts` 封装 |
| `packages/editor/` | 编辑器衍生代码；`src/api/` 是唯一适配缝，**零 HTTP** | EDT-01~09 | 闭包已落地（40 上游文件 + 9 适配缝），可独立 `npm run build`（82 modules / gzip 81 kB），HTTP 扫描 0 命中；EDT-01 `待验` |
| `db/migration`、`db/down` | Flyway 正向脚本与手写回滚脚本（社区版无 undo） | WS-04 | `V1__baseline.sql`（38 表 / 7 域）+ `V1__baseline.down.sql`，**待 149 实跑验证** |
| `scripts/` | 构建、数据库、CI 与环境脚本 | WS-01 / WS-04 / WS-05 / WS-06 | `bootstrap` / `build` / `lint` / `test` / `ci-local` / `db-*` / `secret-scan` / `runner-install` / `env-check` |
| `.github/workflows/` | PR 流水线 | WS-05 | `pr.yml`：lint → test → build 串行，self-hosted `rf-lab`；**runner 待注册** |
| `docs/` | release / plan / process / adr / env / evidence | DOC-01~04 | 骨架已建，`docs/env/环境基线.md` 与 `docs/evidence/v0.1/` 已补 |

## 3. 工具链基线

| 工具 | 版本 | 说明 |
|---|---|---|
| Java | 21 LTS | 后端编译目标 |
| Maven | 3.9.x | 后端构建 |
| Node | 22.x | 前端与 `packages/editor` |
| npm | 10.x | npm workspaces |
| MariaDB | 11.x | 唯一业务库；集成测试用 149 本机独立库（ADR-0002） |
| Robot Framework | 7.5 | 149 `~/rf-stack/.venv`，pabot 5.2.2 已装 |
| make / bash | GNU make + bash | 所有构建入口统一走 Makefile |

## 4. 常用命令

```bash
make bootstrap     # 校验目录与工具链，创建运行时目录（幂等）
make env-check     # 环境基线体检：JDK / Maven / Node / 时区 / DB 变量 / RF venv / 产物根
make lint          # 密文扫描 + 前端 eslint + 后端编译（checkstyle 待 DOC-02）
make test          # 后端单测 + ArchUnit；有 DB_PASSWORD 时追加 @Tag("it") 集成测试
make build         # 后端 jar + 前端 dist
make ci-local      # 本地按 lint → test → build 串行跑三关，日志落 artifacts/ci-local.log
make clean         # 清理构建产物

# 数据库（需 DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD，仓库不落口令）
make db-migrate    # flyway migrate（V1 建 38 表）
make db-undo       # 按版本倒序执行 db/down/*.down.sql
make db-reset      # DROP + CREATE 测试库后重跑 migrate（只允许 *_it 库）
make db-audit      # 38 表规范体检
make secret-scan   # 扫描硬编码口令

make runner-install  # 149 安装自托管 runner（只装不注册，token 需人工取）
```

**跑集成测试**：`mvn -Pit test`（profile `it` 把 surefire 的 `groups` 设为 `it`、清空排除项）。
默认 `mvn test` 会跳过 `@Tag("it")`，本机无库时正常。

## 5. 状态与门禁纪律

1. 状态只有四态：`未开始` → `进行中` → `待验` → `已完成`。
2. **AI 只能改前两态**；`已完成` 必须由人改，且要贴可复现命令或证据路径。
3. 一个任务包一个 PR；一次最多并行两个包。
4. 任务必须挂 WBS 编号（分支名 `feat/ws-01-monorepo`，PR 标题 `[WS-01] ...`）。
5. 不改测试来迁就实现；不顺手重构范围外代码；不改范围 / 不降 KPI / 不降门禁。

## 6. 当前状态（2026-11-02）

- G0 立项门：**有条件通过**，纪要 `docs/plan/G0-评审纪要-20261102.md`。
- v0.1 地基已全部落到 `待验`：WS-01~06 + DOC-01 / DOC-04（进度真源 `docs/plan/WBS-STATUS.md`）。
- v0.2 台账版后端与前端页面已落地并自检通过：IAM-01~04、AST-01~04、CAS-01~05、DOC-03 → `待验`；
  DOC-02 → `进行中`（editor 一份待 v0.3）。
- 已自检通过（本机 Windows）：后端 `mvn -B test` **36 用例**、前端 `npm run build` / `npm run lint`、`secret-scan`。
  证据在 `docs/evidence/v0.1/` 与 `docs/evidence/v0.2/`。
- **待人工在 149 验证**：`db-reset` / `db-undo-v2` / `db-migrate` / `db-audit` / `mvn -Pit test`（无库与口令）；
  IAM-05 的 20 条端到端越权用例；CAS-03 用真实 461 条 Excel 实跑 preview/commit 与重导幂等；
  `CRED_MASTER_KEY` 注入与磁盘/日志无明文扫描；runner 注册与 PR 三绿。
- 已知偏差：后端 checkstyle 未接入（DOC-02 补）；前端无单测框架（只 `vue-tsc --noEmit`）；覆盖率只出报告不设红线。
- 下一批：v0.3 `docs/release/v0.3-editor/`（编辑器依赖闭包移植 → 保存链路）。
- v0.3 进展：EDT-01 依赖闭包已抽到 `packages/editor`（上游 v0.16.1 闭包实测 47 文件 / 18,278 行，
  本包 40 个上游文件 + 9 个适配缝），`npm run build` exit 0、包内 HTTP 0 命中 → `待验`；
  证据 `docs/evidence/v0.3/`。EDT-02 起按 `docs/release/v0.3-editor/DEV-PLAN.md` 的 W2 顺序推进。

## 7. 延伸阅读

- 流程铁律：`docs/process/版本开发流程.md`（七道环节，只认这份开工顺序）
- 环境基线：`docs/env/环境基线.md`（版本、venv、时区、密钥注入、产物根）
- 版本地图：`docs/release/README.md`（v0.1→v1.0 七版能力阶梯）
- 协作规则：`CONTRIBUTING.md`
