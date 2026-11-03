[REPLAY] 非实网

# EXE-08 DUT 采集 · 回放证据（W6B / T6B-1 ~ T6B-7）

**日期** 2026-11-03 ｜ **环境** 本机 Windows（无 DUT 连通性） ｜ **WBS 状态** `待验`

> **本文件不证明实网通过。** 本机到不了 `10.202.54.106/107`，三条采集命令全部由
> `ReplaySshClient` 返回 `backend/src/test/resources/fixtures/dut-replay/` 下的真实形态样本。
> 实网结论只能由 149 现场跑出来（见文末「149 实网待办」）。

---

## 1. 三条采集命令

| # | 命令 | 用途 | 解析判据 |
|---|---|---|---|
| 1 | `ip -br addr` | 拿 DUT 的接口清单与地址，确认采集对象在预期网段 | 输出含 `10.202.54.106/24` 与目标接口名 |
| 2 | `ethtool -S <iface>` | 拿网卡计数（丢包 / 错误 / 收发量） | 输出含 `rx_packets`、`tx_packets`、`rx_errors`、`tx_errors`、`rx_discards` |
| 3 | `cat /proc/interrupts` | 看中断是否落在预期 CPU / 队列 | 只保留含 `iface` 的行，最多 20 行（含 CPU 表头） |

接口名先过白名单 `^[A-Za-z0-9_.:-]+$`，非法直接 `INVALID_ARGUMENT`，不会进入任何 SSH 命令拼接。

## 2. 回放样本来源与用途

| 样本 | 来源形态 | 用途 |
|---|---|---|
| `fixtures/dut-replay/ip-br-addr.out` | Ubuntu `ip -br addr` 六行输出（`eth0`/`enp23s0np0`/`docker0` 等） | 验证地址与接口能被解析；含非目标接口以验证不是「有啥都算过」 |
| `fixtures/dut-replay/ethtool-s.out` | mlx5 风格 `NIC statistics:` 计数段（14 行计数） | 验证 `rx_errors`/`rx_packets` 等关键计数存在 |
| `fixtures/dut-replay/proc-interrupts.out` | 8 列 CPU 表头 + `mlx5_comp` 与 `enp23s0np0-TxRx-*` 行 + 若干无关行 | 验证过滤后行数 ≤20 且只留与接口相关的行 |

样本里不含任何真实口令 / 凭据；`ReplaySshClient` 固定 `exit 0`、耗时 `1ms`，测试不联网。

## 3. 单测结果（真实执行）

```powershell
$env:JAVA_HOME="C:\Users\everg\.jdk\temurin-25\jdk-25.0.2"
& "C:\Users\everg\.maven\maven-3.9.16\bin\mvn.cmd" -B -f backend/pom.xml test `
  "-Djacoco.skip=true" "-Dtest=DutCollectorServiceTest,DutTargetRedactionTest"
```

| 轮次 | 命令范围 | 结果（2026-11-03 11:48） |
|---|---|---|
| 定向 | `DutCollectorServiceTest` | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` |
| 定向 | `DutTargetRedactionTest` | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` |
| 定向合计 | 上述两类 | `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` ｜ `BUILD SUCCESS` |
| 全量 | `mvn -B -f backend/pom.xml test` | `Tests run: 147, Failures: 0, Errors: 0, Skipped: 0` ｜ `BUILD SUCCESS` |

回放运行期实测（`DutCollectorServiceTest` 标准输出原文）：

```text
dut-replay outputs=3 ipHasAddr=true ethtoolHasRxErrors=true interruptLines=9 kind=ok
dut-failure mode=unreachable kind=unreachable advice=检查 180 段路由与跳板机
dut-failure mode=auth_failed kind=auth_failed advice=检查凭据或公钥
dut-failure mode=cmd_not_found kind=cmd_not_found advice=确认 DUT 系统含该工具
dut-failure mode=command_failed kind=command_failed advice=查看 stderr 原文
password-scan secretHits=0 targetLabel=root@10.202.54.106 via evergreen@192.168.180.2
```

> 控制台按 GBK 显示，中文建议文案在上述 mvn 原始输出里为乱码；断言比对的是 UTF-8 字符串，
> 测试全绿说明文案逐字匹配。本文按源码真实文案引用。

覆盖的用例（TC-v0.4-702~705 的本机等价）：

| 断言 | 判据 |
|---|---|
| 三条命令都返回且 exit 0 | `outputs=3`，逐条 `exitCode==0` |
| `ip -br addr` 解析出地址 | stdout 含 `10.202.54.106/24` 与 `enp23s0np0` |
| `ethtool -S` 解析出计数 | stdout 含 `rx_packets`、`rx_errors` |
| `interrupts` 截断生效 | 过滤后 9 行（≤20），且含接口名 |
| 三类失败分类 | `unreachable` / `auth_failed` / `cmd_not_found` 的 kind 与 advice 逐字一致 |
| 其余非 0 退出 | `command_failed` → advice「查看 stderr 原文」 |
| 非法接口名 | `eth0;rm -rf /`、空白、`null` 三种输入均抛 `INVALID_ARGUMENT` |
| 口令不落文本 | `DutTarget.toString()` 与 `DutOutcome.toString()` 均不含占位口令 |

## 4. 口令扫描

扫描对象与结果（PowerShell `Select-String -SimpleMatch`，2026-11-03 实测）：

```powershell
$p='dut-secret-probe-2026'
Get-ChildItem -Path 'backend\src\main\java' -Recurse -File | Select-String -Pattern $p -SimpleMatch
Get-ChildItem -Path 'backend\target\surefire-reports' -Recurse -File | Select-String -Pattern $p -SimpleMatch
Get-ChildItem -Path 'backend\src\test\resources\fixtures\dut-replay' -Recurse -File | Select-String -Pattern $p -SimpleMatch
```

| 扫描对象 | 说明 | 命中 |
|---|---|---|
| `backend/src/main/java` | 生产代码（含 `SystemSshClient` 的 argv 组装与环境变量注入） | **0** |
| `backend/target/surefire-reports` | 单测运行时产出（含本次 `dut-*` 打印行） | **0** |
| `backend/src/test/resources/fixtures/dut-replay` | 回放样本 | **0** |
| **对照组** `backend/src/test/java` | 占位值本身（扫描有效性证明，不计入结论） | 2 |

**结论：生产代码、运行时输出、回放样本三处 0 命中。**

补充说明：

- 占位值 `dut-secret-probe-2026` 只作为测试探针存在于 `DutCollectorServiceTest` /
  `DutTargetRedactionTest` 两个测试类；它必须出现，否则「口令不落文本」无从断言。
  因此测试源码不列为扫描对象，而是作为对照组（2 命中），证明扫描命令确实在工作。
- 口令的真实注入路径只有一处：`SystemSshClient` 里 `builder.environment().put("SSHPASS", ...)`，
  argv 中只有 `sshpass -e`，不含明文。`DutTarget.toString()` 只输出 `user@host[:port]`（+ 跳板
  + `key:yes` 标记），不含口令，也不含私钥完整路径。

## 5. 149 实网待办（未执行）

**前置条件**

1. DUT 恢复：`10.202.54.106` / `10.202.54.107` 从 149 可达（当前机房侧掉线，见 AGENTS.md §5.5）。
2. 180 段路由与跳板机 `192.168.180.2` 可用（跳板账号 `evergreen`）。
3. 口令模式需 `sshpass` 存在：`command -v sshpass` 有输出；否则只能走公钥模式，预检直接拒绝口令模式。
4. 目标机系统含 `iproute2`（`ip`）、`ethtool`、`/proc/interrupts`。

**ssh 命令模板（不含口令）**

```bash
# 公钥模式
ssh -i ~/.ssh/id_ed25519 -o ConnectTimeout=5 -o ServerAliveInterval=10 -o BatchMode=yes \
    -p 22 -J evergreen@192.168.180.2 root@10.202.54.106 'ip -br addr'
ssh -i ~/.ssh/id_ed25519 -o ConnectTimeout=5 -o ServerAliveInterval=10 -o BatchMode=yes \
    -p 22 -J evergreen@192.168.180.2 root@10.202.54.106 'ethtool -S enp23s0np0'
ssh -i ~/.ssh/id_ed25519 -o ConnectTimeout=5 -o ServerAliveInterval=10 -o BatchMode=yes \
    -p 22 -J evergreen@192.168.180.2 root@10.202.54.106 'cat /proc/interrupts'

# 口令模式：口令只经环境变量，命令行走 sshpass -e（下方 <口令> 为占位，不落盘）
SSHPASS='<口令>' sshpass -e ssh -o ConnectTimeout=5 -o ServerAliveInterval=10 \
    -p 22 -J evergreen@192.168.180.2 root@10.202.54.106 'ip -br addr'
```

**平台入口**

```bash
POST /api/execution/dut/collect
{
  "projectId": 1, "host": "10.202.54.106", "port": 22, "user": "root",
  "credentialId": 12,                        # 非空则服务层解密，忽略 password
  "password": null, "privateKeyPath": null,
  "jumpHost": "192.168.180.2", "jumpUser": "evergreen",
  "iface": "enp23s0np0"
}
```

**验收判据（全部满足才算实网通过）**

1. 三条命令 `exitCode == 0`；
2. `ip -br addr` 输出含 DUT 实际地址（如 `10.202.54.106/24`）；
3. `ethtool -S <iface>` 输出含 `rx_errors` 与 `rx_packets`；
4. `/proc/interrupts` 过滤后 ≤20 行且含该接口名；
5. 返回体 `kind == "ok"`，且响应、日志、`DutCollection.targetLabel` 中均无口令；
6. 把本次输出另存为新样本前，先确认不含口令与内网敏感信息。

**当前状态**：以上 6 条一条都未在实网执行，EXE-08 保持 `待验`，不得写「实网通过」。
