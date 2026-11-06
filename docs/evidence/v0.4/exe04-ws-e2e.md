# EXE-04 实时日志推送 · 真实 WebSocket 端到端

## 这一份补的是什么缺口

EXE-04 原来置 `待验`，理由写得很清楚：**「真实 WebSocket 端到端、断线重连与 2000 行上限
需在本地浏览器抽验；`log-latency.txt` 只覆盖服务端 flush，不含网络传输」**。

`LogRingBufferTest` / `LogBatcherTest` / `RunLogServiceTest` 全是纯单测，
没有一个真正起 HTTP 服务、真正握手 WebSocket、真正断线重连。本 E2E 补的就是这一段。

## 怎么跑

```bash
cd ~/robot-autotest/backend
set -a; . ~/.config/robot-autotest/env; set +a
export DB_NAME="${TEST_DB_NAME:-robot_it}"
mvn -Dtest=RunLogWebSocketE2ETest test
```

用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 起**真实 Tomcat**（日志里能看到
`Tomcat started on port 45046`），客户端用 JDK 自带 `java.net.http.WebSocket` —— 不引额外依赖，
也不 mock 协议层。测试类带 `@EnabledIfEnvironmentVariable(named="DB_PASSWORD", matches=".+")`，
没有库口令时自动跳过而不是假绿。

## 结果

**4 / 4 通过**（`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`，11.18 s）。
原始报告 `exe04-ws-e2e.log`（surefire 摘要）与 `exe04-ws-e2e.xml`（逐用例耗时）。

| 用例 | 断言 |
|---|---|
| `incrementalPushArrives` | 订阅后发布的新行能推到客户端；单帧 ≤ 50 行（`LogBatcher` 上限） |
| `reconnectReplaysRecent200` | 断线期间产生的行，重连后从环形缓冲补发最近 200 行 |
| `reconnectAfterClose` | 关闭后重新握手能接上，不残留旧会话 |
| `ringBufferKeepsLast2000` | 环形缓冲只保留最近 2000 行，更早的被淘汰 |

## 本轮修掉的两个真实缺陷

### 1）日志推送依赖「执行调度」开关

`@Scheduled` 的 flush 方法所在的配置类上没有 `@EnableScheduling`，只在执行调度的配置里才开。
后果：**只要没起执行调度，日志就永远不 flush**，前端表现为「跑起来了但一条日志都不出」，
而单测直接调 `flush()` 所以全绿。

修法：新增 `infrastructure/RunLogFlushConfig.java`，单独 `@EnableScheduling`，
让日志推送不再寄生在执行调度上。

### 2）补发与增量重叠，同一行推两遍

订阅时先把环形缓冲里的历史补发一遍；如果此时 batcher 里还压着上次没 flush 的行，
下一轮 flush 会**再把它们推一遍**。前端看到重复行，行号跳变。

修法：给每个订阅者记水位 `lastSeq`，flush 时只推 `seq > lastSeq` 的行；
补发也走同一个 `acceptAll`，统一更新水位。

**踩到的坑**：修完 `RunLogServiceTest` 变红，断言是 `received.contains("line-1")` ——
它要求的正是「补发窗口之外的行也要再推一遍」，也就是被修掉的那个 bug。
**旧断言在固化错误行为。** 改成「新行必达 / 不得重复 / 窗口外不得回流」三条。
改完又发现单次 flush 不够：`pending` 是 FIFO，250 行积压、每轮只取 50 行，
新行要排到第 6 轮才轮到 —— 改成最多循环 12 轮 flush。

## 边界说明（不夸大）

- 客户端用的是 JDK `WebSocket`，**不是浏览器**。协议层一致，但浏览器的
  连接池复用、`EventSource`/`WebSocket` 在页签休眠时的行为没有被覆盖。
- 只做了 200 行补发与 2000 行环形缓冲的上限验证，**没有做长时间高频压力**
  （那条仍是 HRD-03 试点迭代的范围）。
- 限流是「每帧 ≤ 50 行 / 100ms 一轮」的服务端实现，没有验证多实例下的全局速率。
