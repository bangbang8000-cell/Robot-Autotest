# Robot-Autotest

自动化测试平台主仓库。技术内核为 Robot Framework + robotdashboard + RoboScope，
平台本体采用 Spring Boot + MariaDB + Vue 3。

> 当前阶段：立项评估。代码尚未开写，仓库内主体是评估与架构文档，见 `docs/`。

## 仓库与远程

本仓库双远程，同一份历史：

| 远程名 | 地址 | 用途 |
|---|---|---|
| `github` | `git@github.com:huanchen811509-silence/Robot-Autotest.git` | 主协作仓：PR 评审、Actions、议题 |
| `origin` | `ssh://silence@10.202.54.201:22/volume1/git/repos/robot-autotest.git` | 内网镜像（群晖 DS918+，仅 SSH 协议） |

> GitHub 仓为 **public**。这是为了启用免费的服务端分支保护（私有库需 GitHub Pro）。
> 代价是文档中的内网地址（`10.202.54.x`、`192.168.110.x`）与登录账号名对外可见。
> 若后续回归私有，需同步把 `main` 保护降级为本地 `pre-push` 钩子，见 CONTRIBUTING §6。

首次克隆：

```bash
git clone git@github.com:huanchen811509-silence/Robot-Autotest.git
cd Robot-Autotest
git remote add synology ssh://silence@10.202.54.201:22/volume1/git/repos/robot-autotest.git
```

内网镜像推送必须显式指定密钥（群晖只认部署密钥，不认账号口令）：

```bash
GIT_SSH_COMMAND="ssh -i ~/.ssh/syno_robot_autotest -o BatchMode=yes" git push synology main
```

## 文档索引

| 文件 | 内容 | 定位 |
|---|---|---|
| `docs/process/版本开发流程.md` | **流程铁律**：每个大版本七道环节（需求调研 → 评估报告 → PRD → 开发计划 → 测试计划 → 测试执行 → 文档回写）与六份产物的准入门槛 | **开工顺序只认这份** |
| `docs/自动化测试平台中长期工作规划.md` | **执行总纲**：10 个工作包 / 56 个任务包，含 AI 推进机制、批次门禁 G0-G8、质量策略、变更治理 | **任务从哪来** |
| `docs/plan/WBS-STATUS.md` | **进度真源**：56 个任务包的四态状态表（未开始/进行中/待验/已完成） | **看进度只认这张表** |
| `docs/release/README.md` | **版本地图**：7 个主线版本 v0.1→v1.0 的划分依据、版本号规则、能力阶梯、兼容性策略 | **决定「这版包含什么」** |
| `docs/release/v*/` | **每版六份产物**：①`00-RESEARCH.md` ②`01-ASSESSMENT.md` ③`PRD.md` ④`DEV-PLAN.md` ⑤`TEST-PLAN.md` ⑥`05-TEST-REPORT.md` | 按流程**逐份产出**，不一次性铺 |
| `tools/check_release_docs.py` | **流程机检**：产物齐全 / FR→TC 覆盖 / TC 有结果 / 评估报告与回写是否做 | 发版前与 PR 时跑 |
| `docs/自动化测试平台调研与架构评估报告_v4.1_20261102.md` | **技术基线**：29 章，含架构原则、逻辑架构、工程结构、部署、安全、运维、质量、容量测算与证据附录 | 技术结论以此为准 |

五层分工：**流程铁律回答「按什么顺序做」，报告回答「为什么这么设计」，规划回答「下一件做什么」，版本表回答「这版包含什么」，状态表回答「现在到哪了」**。

> **每个大版本的固定动作（大师 2026-11-02 定规，永久有效）**：
> **先调研需求写评估报告 → 再写 PRD → 开发计划 → 测试计划 → 写完再测试 → 测完回写文档。**
> 缺任一环，该版本不算完成，不得对外称已交付。机检：`python tools/check_release_docs.py`。

### 版本一览

| 版本 | 代号 | 门禁 | 这一版能做到 |
|---|---|---|---|
| v0.1 | 地基 | G1 | 拉下代码 30 分钟跑起空壳，CI 能拦住跨层调用 |
| v0.2 | 台账 | G2 | 能登录、建项目、录设备、导 461 条用例、按技术域筛 |
| v0.3 | 编辑器 | G3 | 用例三视图编辑，改完还能跑（往返保真） |
| v0.4 | 执行 | G4 | 4 条并发跑不打架，日志实时，取消停得住 |
| v0.5 | 报告 | G5 | metadata 十项齐全，PDF 能直接发客户 |
| v0.6 | 自动化 | G6 | 定时跑、提交就跑，结果自动回写 |
| v1.0 | 生产就绪 | G7→G8 | 权限可审、出事能回滚、有人能守 |

7 个版本 = 7 道门禁，56 个任务包一个不落。每版的能力**不倒退**：上一版的验收证据会固化成 `REG-*` 回归集，跑不过不许发版（`docs/release/README.md` §4）。

> **进度口径**：不按日期、不按人月。**进度 = 已通过门禁的任务包数 / 56**。
> 规划中出现的「参考人日/人月」只是相对体量，不构成承诺、不用于考核。
> 推进主体为 AI 智能体，人类守四道闸门（决策 / 评审 / 授权 / 门禁）——见规划 §7。

> 历史版本（v1/v2/v3 及 docx）已从工作区移除，需要追溯时从 git 历史取
> （`git show b6a4491:docs/`）。报告修订理由见 §0.4。

## 协作

多人协作规则见 [CONTRIBUTING.md](CONTRIBUTING.md)。`main` 分支受保护，
所有改动走 Pull Request，禁止直接推送。
