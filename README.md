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
| `docs/自动化测试平台中长期工作规划_2026-2028.md` | **执行总纲**：10 个工作包 / 56 个任务包 / 506 人日，含阶段门禁 G0-G8、质量策略、变更治理、里程碑日历 | **开发与测试的唯一任务来源** |
| `docs/自动化测试平台调研与架构评估报告_v4.1_20261102.md` | **技术基线**：29 章，含架构原则、逻辑架构、工程结构、部署、安全、运维、质量、容量测算与证据附录 | 技术结论以此为准 |

两份文档分工：**报告回答「为什么这么设计」，规划回答「谁、什么时候、做什么、凭什么算做完」**。
排期、门禁、验收、变更、度量以规划为准；架构与技术选型以报告为准。

> 历史版本（v1/v2/v3 及 docx）已从工作区移除，需要追溯时从 git 历史取
> （`git show b6a4491:docs/`）。报告修订理由见 §0.4。

## 协作

多人协作规则见 [CONTRIBUTING.md](CONTRIBUTING.md)。`main` 分支受保护，
所有改动走 Pull Request，禁止直接推送。
