# ADR-0004 · CI runner 落在本地 WSL 裸进程

- **状态**：提议（脚本已就绪，**尚未在本地 WSL 上实际注册**）
- **日期**：2026-11-02（2026-11-03 环境口径更新：149 已停用，落点改为本地 WSL）
- **决策人**：大师（G0 纪要 P3：本地 WSL 裸进程自托管 runner）
- **约束范围**：CI（WS-05）及其后所有流水线

## 背景

- 托管 runner（GitHub 云）到不了 `10.202.54.x`（DUT 与测试机在内网），R13 风险；
- 本地 WSL 运行环境按最小依赖约定部署，不额外引入 Docker（若后续需要容器化，另行评估）；
- 内网镜像仓（NAS）有 Container Manager，可跑 runner 容器，但多一层依赖与运维。

## 选项

| 选项 | 优点 | 代价 | 结论 |
|---|---|---|---|
| ① **本地 WSL 裸进程 runner** | 零额外组件；与 RF / MySQL 同机，直连 DUT | CI 与开发、平台共用同一台电脑，重负载时争资源 | **提议采用** |
| ② 内网镜像仓（NAS）容器 runner | 与平台机隔离 | 需要容器运维；网络到本地 WSL 要额外放开 | 备选 |

## 决定

按 ① 落地：在本地 WSL 上以**非 root 用户 `evergreen`** 注册 GitHub Actions runner（目录 `~/actions-runner`），
标签 `self-hosted,linux,x64,rf-lab`，服务方式 `./svc.sh`（用户级）。

**边界**：本 ADR 只决定「装在哪」；runner 上的 JDK / Node / MySQL 8.0 版本由 WS-06 环境基线约束。

## 后果

- 正面：CI 能直接跑集成测试与后续 DUT 用例，不用暴露内网。
- 负面：CI 与平台后端共机，重负载时互抢 CPU/内存；需在 WS-06 写清优先级与并发上限。
- 复核触发条件：CI 单次运行导致平台执行超时，或本地 WSL 迁移/扩容。

## 关联

- WBS：WS-05、WS-06
- 文件：`scripts/runner-install.sh`、`.github/workflows/pr.yml`
- 待办：**注册令牌需要大师提供**，脚本已就绪但未执行（WBS-STATUS 记为「待人工环境验证」）
