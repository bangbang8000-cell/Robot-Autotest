# ADR-0004 · CI runner 落在 149 裸进程

- **状态**：提议（脚本已就绪，**尚未在 149 上实际注册**）
- **日期**：2026-11-02
- **决策人**：大师（G0 纪要 P3：149 裸进程自托管 runner）
- **约束范围**：CI（WS-05）及其后所有流水线

## 背景

- 托管 runner（GitHub 云）到不了 `10.202.54.x`（DUT 与测试机在内网），R13 风险；
- 149 已实测**无 Docker**（基线报告 §4.2），容器化 runner 不可行；
- 群晖 `10.202.54.201` 有 Container Manager，可跑 runner 容器，但多一层依赖与运维。

## 选项

| 选项 | 优点 | 代价 | 结论 |
|---|---|---|---|
| ① **149 裸进程 runner** | 零额外组件；与 RF / MariaDB 同机，直连 DUT | 149 是自用老机，CI 与平台争资源 | **提议采用** |
| ② 群晖容器 runner | 与平台机隔离 | 需要容器运维；网络到 149 要额外放开 | 备选 |

## 决定

按 ① 落地：在 149 上以**非 root 用户 `evergreen`** 注册 GitHub Actions runner（目录 `~/actions-runner`），
标签 `self-hosted,linux,x64,rf-lab`，服务方式 `./svc.sh`（用户级）。

**边界**：本 ADR 只决定「装在哪」；runner 上的 JDK / Node / MariaDB 版本由 WS-06 环境基线约束。

## 后果

- 正面：CI 能直接跑集成测试与后续 DUT 用例，不用暴露内网。
- 负面：CI 与 RoboScope 平台共机，重负载时互抢 CPU/内存；需在 WS-06 写清优先级与并发上限。
- 复核触发条件：CI 单次运行导致平台执行超时，或 149 迁移/扩容。

## 关联

- WBS：WS-05、WS-06
- 文件：`scripts/runner-install.sh`、`.github/workflows/pr.yml`
- 待办：**注册令牌需要大师提供**，脚本已就绪但未执行（WBS-STATUS 记为「待人工环境验证」）
