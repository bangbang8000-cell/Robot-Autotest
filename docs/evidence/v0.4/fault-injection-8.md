# v0.4 执行编排 8 类故障注入证据

> 对应 WBS #32 / EXE-03。**本机无 Robot Framework / pabot / DUT，下文第 1~7 条是单测等价注入，第 8 条为真实 pabot 注入，必须到本地 WSL 实跑。**
> 全量单测输出见 `mvn-test.log`（`Tests run: 147, Failures: 0, Errors: 0, Skipped: 0`）。

| # | 故障注入 | 注入方式 | 代码/测试 | 本机结果 | 本地 WSL 实跑 |
|---:|---|---|---|---|---|
| 1 | pabot 不存在 | 假文件系统 `exists=false` | `ExecutionPrecheckTest.pabot_不存在报_PABOT_NOT_FOUND` | PASS（`PABOT_NOT_FOUND`） | 不需要 |
| 2 | pabot 版本不匹配 | 版本探针返回 `4.9.0`（要求 `5.x`） | `ExecutionPrecheckTest.版本前缀不匹配报_PABOT_VERSION` | PASS（`PABOT_VERSION`） | 不需要 |
| 3 | 产物根不可写 | 假文件系统 `writable=false` | `ExecutionPrecheckTest.产物目录不可写报_ARTIFACT_ROOT_NOT_WRITABLE` | PASS（`ARTIFACT_ROOT_NOT_WRITABLE`） | 不需要 |
| 4 | 磁盘空间不足 | 可用空间 1 GiB < 门槛 5 GiB | `ExecutionPrecheckTest.空间不足报_DISK_LOW` | PASS（`DISK_LOW`） | 不需要 |
| 5 | 端口数不足 | 请求 4 并发、可用端口 2 | `ExecutionPrecheckTest.端口不足报_PORT_SHORTAGE` | PASS（`PORT_SHORTAGE`） | 不需要 |
| 6 | 设备不可用 | 设备问题列表含「GPU-01 连接超时」 | `ExecutionPrecheckTest.设备不可用报_DEVICE_UNAVAILABLE` | PASS（`DEVICE_UNAVAILABLE`） | 不需要 |
| 7 | output.xml 缺失 / 过小 / 解析失败 / 0 用例 | 四份 fixture（缺失、125 B 非法、1069 B 0 用例、1530 B 2 用例） | `ArtifactVerifierTest`（5 条） | PASS（四种 reason 各自命中；正常样本成功） | 不需要 |
| 8 | 真实 pabot 非 0 退出 / 超时 / 被杀 | 真实 `pabot` + 真实子进程 | TC-v0.4-301/303/306 | **未实跑** | **待本地 WSL**：跑 `fixtures/bench/4suite-2cases` 并人为 `kill -9` 一个 shard，确认 `FAILED(interrupted)` 与产物留存 |

## 结论

- 7 / 8 条已用单测等价注入覆盖并通过；
- 第 8 条（真实 pabot 进程故障）**本机不具备条件，未执行**，不得据此宣称 EXE-03 实网通过；
- 本地 WSL 补跑后把第 8 条的 `未实跑` 改为 `PASS` 并附 `ps` / `output.xml` 路径，再由人把 WBS #32 置 `已完成`。
