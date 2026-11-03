# ADR-0011 · 双仓与 Gitea 决策

- **状态**：已采纳
- **日期**：2026-11-03
- **决策人**：大师（v0.6 G6 门禁）
- **约束范围**：代码托管、协作评审、CI 触发与状态回写
- **取代**：无（与 ADR-0001 monorepo 并行：monorepo 是仓库内部结构，本 ADR 管仓库放在哪）

## 背景

仓库需要同时满足两件互相冲突的事：

1. **权威源必须在内网**：测试机在 `10.202.54.x` 网段，平台、用例、实验数据都在内网；
2. **协作与 CI 需要外网能力**：PR 评审、Actions、commit status 都依赖 GitHub。

因此问题不是「选 GitHub 还是群晖」，而是「权威源放哪、哪一侧承担评审与 CI」。

## 现状事实（2026-11-03 核实）

| 项 | 事实 |
|---|---|
| 群晖 `git server` | **只有 SSH 协议**；无 HTTP、无 Web UI、无 webhook、无细粒度权限。能 `git clone/push`，但不能做 PR 评审，也不能接收 CI 状态回写 |
| 群晖 Container Manager | Docker **24.0.2** 已安装，可跑容器（149 测试机上没有 Docker） |
| GitHub | 个人号仓库，协作者权限上限 write；已转 public 才有 branch protection |
| 测试机 149 | 与 `10.202.54.x` DUT 同网段；GitHub 托管 runner 无法访问该网段，**CI 必须用自托管 runner** |
| 平台 CI 接口 | `POST /api/ci/trigger`（PLN-03），项目 API token + `ci:trigger` scope；回写仅支持 GitHub commit status |

## 选项

| 选项 | 优点 | 代价 | 结论 |
|---|---|---|---|
| A. **群晖为权威源 + GitHub 协作与 CI** | 权威数据不出内网；复用 GitHub 的 PR / Actions / status；零额外部署 | 内网没有 Web 评审界面；纪律要求「涉密内容不得进 GitHub」；双仓需人工同步 | **采用（本版）** |
| B. 群晖上跑 Gitea 容器 | 内网 Web 评审、内网 CI、权限细粒度；数据完全不出内网 | 半天部署 + 长期维护（升级、备份、证书、账号）；CI runner 仍要自建 | 待触发条件满足时再切 |
| C. 全部迁 GitHub | 最省事 | 内网数据出网，违反保密要求 | 不选 |

## 决定

采用 **方案 A**：

- **群晖 SSH 仓库是唯一权威源**（`origin`）；GitHub 是协作与 CI 的镜像（`github`）。
- 日常开发：本地 → 群晖 push；需要评审/CI 时再 push 到 GitHub。
- 平台 CI 触发与回写只面向 GitHub；群晖侧**明确不支持状态回写**（PLN-03 返回 `not_supported`，见 `docs/CI接入指南.md`）。

## A 方案的代价与纪律

| 代价 | 纪律 / 缓解 |
|---|---|
| 内网没有 PR 界面，评审只能在 GitHub 做 | 评审期间代码在 GitHub 可见，**涉密内容（真实口令、客户信息、内网拓扑细节、未脱敏日志）不得进入 GitHub**；提交前用 `git diff` 自查 |
| 双仓可能漂移 | 每次发布前以群晖为准做一次 `git fetch` 对比；GitHub 侧只作为镜像，冲突以群晖为准 |
| GitHub 仓库 public 才能开 branch protection | 接受 public 带来的可见性；敏感分支不推 GitHub |
| 群晖无 webhook / 无 HTTP API | 群晖侧只做存档；任何「群晖也能回写状态」的说法都是错误的 |
| CI 需要自托管 runner | runner 落在 149（ADR-0004），必须能访问 `10.202.54.x` 与平台 `:8081` |

> 这不是「暂时凑合」。它是有明确触发条件的决策；条件一旦出现，按下面的步骤切 B，不要在原方案上打补丁。

## 切到方案 B 的触发条件与步骤

**任一条件成立即切换**：

1. 出现「不得出内网」的代码或数据，且必须在仓库内做评审；
2. 需要内网 PR 流程、内网 issue/CI，或 GitHub 不可用成为发布阻塞；
3. 协作者数量或权限要求超出 GitHub 个人号 write 上限。

**切换步骤（概要，约半天）**：

1. 群晖 Container Manager 拉 `gitea/gitea` 镜像，挂载持久化目录（配置、数据、仓库），映射 HTTP 端口；
2. 初始化管理员，创建组织/仓库，把现有 SSH 仓库以 mirror 方式导入；
3. 配置 Gitea Actions runner（或复用 149 上的 runner），把 `.github/workflows` 迁为 `.gitea/workflows`；
4. 平台侧：`ci_trigger.repo` 改为 Gitea 仓库标识；**回写仍不支持**（Gitea 有 status API，但本版未实现，需新增适配器后再启用）；
5. 群晖 SSH 仓库降级为备份，`origin` 指向 Gitea；保留 30 天双写期；
6. 更新本 ADR 状态为「已废弃（被 ADR-NNNN 取代）」，另立新 ADR 记录切换结果。

## 后果

- 正面：权威源在内网；评审与 CI 复用成熟平台；本版无需新增部署。
- 负面：双仓同步靠纪律而非工具；GitHub 侧可见性受 public 限制。
- 复核触发条件：上述三条任一出现，或 GitHub 个人号权限成为阻塞时。

## 关联

- WBS：PLN-03、PLN-04
- 文件：`docs/CI接入指南.md`、`db/migration/V6_3__ci_trigger.sql`、
  `backend/src/main/java/com/qa/autotest/application/ci/`
- 依据：`docs/release/v0.6-automation/DEV-PLAN.md` §4、§5；ADR-0004（runner 落点）
