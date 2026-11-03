# EXE-03 故障注入记录（2026-11-04）

**目的**：验 `pabot` 分片被杀时，平台能否如实上报中断。原始输出：`exe03-fault-injection.log`。
**用例**：`fixtures/bench/4suite-2cases`（8 条，每条 `Sleep 6s`，不碰设备）。
**方式**：4 进程启动，5 秒后对某个 `bin/robot -A …/robot_argfile.txt` 进程 `kill -9`。

## 一、结果

| 观测项 | 实测 |
|---|---|
| 被杀进程 | `bin/robot -A pabot_results/1/robot_argfile.txt`（确认是分片，不是 pabotlib） |
| pabot 退出码 | **252**（非 0，中断被上报） |
| `output.xml` 用例数 | **6**（应为 8） |
| 6 条的统计 | `{'PASS': 6}` —— 没有任何 FAIL |
| `pabot_results/` 分片目录 | 0 / 1 / 2 / 3 仍在 |

**退出码这一关过了**：分片被杀 → 非 0，不会把中断报成成功。

## 二、但发现一个更危险的问题：产物校验会放行「部分丢失」

`ArtifactVerifier`（`application/execution/ArtifactVerifier.java:34`）的判据是：

```java
if (probeResult.totalTests() <= 0) {
    return new VerifyResult(false, "no_tests_executed", 0, size);
}
return new VerifyResult(true, "ok", probeResult.totalTests(), size);
```

即「存在 + size>1024 + 可解析 + **totalTests>0**」。

本次被杀掉 2 条用例后，`totalTests = 6 > 0` → **返回 `ok`**。

也就是说：**如果调用方只看 `VerifyResult.success`，这次执行会被判为成功，6 条全绿，而那 2 条用例从未执行。** 这比「报失败」危险——失败会被人查，消失不会。

现有单测（`ArtifactVerifierTest` 5 条）覆盖的是「文件缺失 / 空文件 / 不可解析 / 0 用例 / 正常」，**没有「期望 N 条但只有 N-2 条」这一格**，所以这个洞一直没被发现。

### 根因

校验器**不知道「应该跑多少条」**。它的入参只有 `Path outputXml`，没有期望值。
`PabotOrchestrator:87` 也只调 `verifier.verify(outputXml)`，同样没有期望用例数的来源。

### 修法方向（需接口变更，未实施）

1. 执行请求里带上 `expectedCaseCount`（来自本次选中的用例集），一路传到 `ArtifactVerifier.verify(outputXml, expectedCount)`；
2. 判据加一条：`totalTests < expectedCount` → `reason="partial_results"`，并报出差额；
3. 另可交叉校验 `pabot_results/` 的分片目录数与启动进程数是否一致（分片消失比用例消失更早可见）；
4. 补单测 3 条：少 1 条 / 少全部 / 多出（重复执行）三种情形。

**为什么不现在改**：这是 v0.4 执行域的接口变更（用例选择 → 执行请求 → 编排器 → 校验器），要一并补测试与回归；且当前 PR 流程尚未恢复（G1 新增项）。留到 F2 的 EXE 返工一起做，先记录在案。

## 三、这次注入自己踩的坑（值得记）

第一版脚本用 `pgrep -P $PABOT_PID | head -1` 找分片，结果抓到的是 **`pabotlib`（远程库服务）**，杀掉后用例照跑、退出码 0 —— 差点被误读成「产品把中断报成了成功」。

`pabot` 的子进程里，`pabotlib` 往往排在最前。**要杀分片必须认命令行里的 `bin/robot`**，不能按「第几个子进程」猜：

```bash
ps -eo pid,ppid,args | awk -v p="$PABOT_PID" '$2==p && /bin\/robot/ {print $1; exit}'
```

修完重跑才拿到 252 / 6 条的真实结果。**结论：故障注入脚本自己也要自证「杀对了目标」，否则验的是错的东西。**
