# 两台服务器轻量冒烟（开发期验程序用）

一条命令看两台机、一条链路的基本状态：**健康检查 → ping → iperf3 → perftest**。

## 这是什么、不是什么

| | |
|---|---|
| **是** | 开发期验证「平台能不能把一条用例从头跑到尾、并留下证据」的**轻量用例** |
| **不是** | 产品验收、性能基线、稳定性压测。**不设吞吐/带宽通过阈值**，只记录现状 |
| **级别** | 全部动作属 **L0（只读）/ L1（可逆）**，见 `docs/adr/ADR-0013`。**不碰 L2** |

安全边界（脚本里写死，不靠自觉）：

- **不改配置**：不动 MTU、不动 offload、不动 ring、不动队列、**不碰交换机**
- **不装软件**：远端缺 `iperf3` / `ib_write_bw` 只记 **SKIP**，绝不自动 `apt install`
- **不碰凭据**：`DUT_USER` / `DUT_PASS` 从环境变量读，仓库里不留明文
- 唯一副作用：临时起 iperf3 / ib_write_bw 服务端进程，`-1` 一次即退 + `trap` 兜底 kill

## 两个入口

### ① 命令行版（最快，验链路）

```bash
# 在 WSL / Linux 下跑（Windows Git Bash 缺 sshpass）
export DUT_USER=xxx DUT_PASS=yyy

./scripts/smoke-2node.sh \
  --a 10.202.54.106 --b 10.202.54.107 \
  --nic-a ens17np0 --nic-b ens17np0
```

常用参数：

| 参数 | 作用 |
|---|---|
| `--skip-tput` | 跳过 iperf3（只做健康检查 + ping） |
| `--skip-rdma` | 跳过 perftest |
| `--duration 30` | iperf3 时长（默认 10s） |
| `--rdma-dev mlx5_1` | perftest 设备名（默认 `mlx5_0`） |
| `--json out.json` | 额外输出机器可读结果 |
| `--dry-run` | 只打印计划，**不连任何设备** |

退出码：`0` 全过（含 SKIP）｜`1` 有 FAIL ｜`2` 参数或环境错误。

### ② 平台版（进执行域，带 metadata）

```bash
export DUT_A=10.202.54.106 DUT_B=10.202.54.107
export NIC_A=ens17np0 NIC_B=ens17np0 DUT_USER=xxx DUT_PASS=yyy

robot fixtures/smoke/2node-smoke.robot
```

6 条用例，每条一个阶段，失败能定位到阶段：

| 用例 | 阶段 | 级别 |
|---|---|---|
| `TC-01` / `TC-02` | A/B 端健康检查（OS/内核/负载/内存/磁盘/网卡 link+速率/驱动固件/时钟） | L0 |
| `TC-03` | 双向 ping（各 20 包，判据丢包 <1%） | L0 |
| `TC-04` / `TC-05` | iperf3 TCP 单流 / 8 流 | L1 |
| `TC-06` | perftest `ib_write_bw` | L1 |

metadata 按项目硬规矩给全 10 项（`CASE_ID`/`RUN_ID`/`QA_PROJECT`/`DUT`/`LINK`/`SW_BUILD`/`BASELINE`/`OPERATOR`/`STARTED_AT`/`CASE_KIND`），`RUN_ID` 与 `STARTED_AT` 在 `Suite Setup` 里按运行时刻生成。

## 前置条件

| 依赖 | 位置 | 缺了会怎样 |
|---|---|---|
| `sshpass` | 本机（WSL 有，Windows Git Bash 无） | 报错退出，给出改用 WSL 的提示 |
| `iperf3` | **两台 DUT** | 记 SKIP，不判失败 |
| `ib_write_bw`（perftest） | **两台 DUT** | 记 SKIP，不判失败 |
| RDMA 设备 `mlx5_0` | 两台 DUT | `--rdma-dev` 覆盖；不对则 TC-06/脚本 S4 FAIL |

装 iperf3 / perftest **属于环境准备，由人决定**（`ENV-01`），不在这两个脚本的职责内。

## 输出长什么样

```
== S1 基础健康检查（只读） ==
  [ok]   S1 A(10.202.54.106) 主机 GPU01 | Ubuntu 22.04.5 LTS | kernel 5.15.0-91 | up 3 days
  [info]   A cpu=64核 load=0.8/0.6/0.5 mem=12% disk=45%
  [ok]   S1 A 网卡 ens17np0 link=up speed=400000Mb/s
  [info]   A ens17np0 driver=mlx5_core fw=28.39.1002

== S3 iperf3 打流（TCP，10s） ==
  [info]   TCP 1 进程/流 → 39.30 Gbps
  [info]   TCP 8 进程/流 → 187.90 Gbps
```

## 已知限制

1. **iperf3 服务端要 `-1` 重启**：每组流数前都重启一次服务端，否则第二组连不上（已在脚本与套件里处理）。
2. **perftest 解析不能 `grep 'BW average'`**：那行是**表头**，数字在下一行。已改成取数据行第 4 列 / 正则匹配数据行。
3. **`iperf3 -J` 是多行 JSON**：不能按行解析，必须整段交给 `json.load`。
4. **RoCE 场景可能需要 `--gid-index`**：本脚本未透传，环境不同时用 `-v RDMA_DEV` + 手工补参数；perftest 失败时先看 `/tmp/perftest-srv.log`。
5. **健康检查不卡门槛**：取不到值只记 `?` 或 SKIP。它的用途是"看清现状"，不是"拦人"。

## 校验状态（2026-11-04）

| 项 | 结果 | 方式 |
|---|---|---|
| `bash -n scripts/smoke-2node.sh` | 通过 | 语法 |
| `--dry-run` / 参数校验 / 缺 DUT_USER | 通过 | 实跑（不连设备） |
| iperf3 JSON 双格式解析 | `187.90` / `42.00` | 造样本 + `json.load` |
| ping 丢包与 RTT 解析 | `loss=0` `rtt=0.096` | WSL 实 ping |
| perftest 数据行解析 | `187.90` | 造真实格式样本 |
| `robot --dryrun` 套件 | **6 tests, 6 passed** | RF 7.5.1 + SSHLibrary |

**未做的**：没有在真实 DUT 上跑过一次（DUT 凭据未提供、禁跑令期间不擅自连设备）。真跑前先 `--dry-run`，再 `--skip-tput --skip-rdma` 只验健康检查与 ping。
