# ENV-01 ~ ENV-05 环境证据

**时间**：2026-11-04 00:45 ~ 01:05（Asia/Shanghai）
**对应**：`docs/plan/WBS-STATUS.md` #57~61；`docs/env/环境基线.md` §1/§2/§4/§7/§9
**原则**：本目录只放**可复现命令的原始输出**。数字都能用文中命令重跑出来，不接受「我记得是」。

## 逐项

| 包 | 判据 | 复现命令 | 结果 | 证据 |
|---|---|---|---|---|
| **ENV-01** | `robot` / `pabot` / SSHLibrary 三项可用，路径固定 | `bash scripts/env-setup-rf.sh` | RF **7.5.1** / pabot **5.2.2** / SSHLibrary **3.8.0**，venv 在 `~/rf-stack/.venv`，退出码 0 | `env-setup-rf.log` |
| **ENV-02** | 新 shell 直接 `mvn -v` / `node -v` 成功；文档无「问人才知道」的路径 | `bash scripts/env-probes.sh` + `mvn -DskipTests compile` | WSL：JDK 21.0.12 / Maven **3.9.16** / Node 26.8.2；Windows：JDK 23.0.2 / Maven 3.9.16（已入用户 PATH）；**WSL 首次编译 BUILD SUCCESS，278 源文件，3:51** | `env-probes-wsl.log`、`env-probes-win.log`、`mvn-compile-wsl.log` |
| **ENV-03** | `SELECT VERSION()` 与文档一致；ADR-0002 修订落地；`robot_it` 能真跑集成测试 | `bash scripts/db-audit.sh` + `DB_NAME=robot_it mvn -B -Pit test` | **8.0.46-0ubuntu0.24.04.4**；`robot_dev`/`robot_it` 均到 **V7_1 / 44 张业务表 / 17 条迁移**；`db-audit` **7 项全 PASS**；`SchemaIT` **5/5 通过**（35.36s） | `env02-build-test-20261104.log` §二；库结构见环境基线 §4 |
| **ENV-04** | `fixtures/README.md` 列明来源与脱敏方式；台账可被 CAS-03 读取 | `python tools/anonymize_ledger.py <源> -o …` + `python tools/check_import_header.py …` | 脱敏侧**通过**：226 行 / 14 列 / 编号连续唯一，782 处 IP 全落 `10.0.0.0/16`，账户残留 0；表头侧**补别名后 6/14 `[PASS]`**（原 1/14 FAIL）。**代价已定案**：4 列命令类信息静默丢弃、优先级全落 P2 | `env04-anonymize.log`、`env04-header-check.log` |
| **ENV-05** | `bash scripts/env-probes.sh` 出表、红项显式、退出码可判定 | `bash scripts/env-probes.sh` | WSL 硬项**全绿**（DUT 106/107 双通），退出码 0；Windows 侧仅 `mvn` 因会话 PATH 快照报红，退出码 1 | `env-probes-wsl.log`、`env-probes-win.log` |

## 构建首次通过（ENV-02 的硬证据）

WSL 里从零拉依赖到编译通过：

```
[INFO] Compiling 278 source files with javac [debug parameters release 21] to target/classes
[INFO] BUILD SUCCESS
[INFO] Total time:  03:51 min
```

依赖走阿里云镜像（`~/.m2/settings.xml`，只改下载源）。复跑用 `mvn -B -o -DskipTests compile`，42 秒（增量）。

## 本次暴露并修掉的四个真问题

写在这里是因为它们会再犯：

1. **`~/.bashrc` 管不到非交互 shell**。`bash -lc`、CI、pabot 子进程读不到它，会**静默用系统自带的 Maven 3.8.7**，与验证过的 3.9 不一致且无报错。修法：PATH 同时写 `/etc/profile.d/robot-autotest.sh`。
2. **venv 半成品**。建 venv 时 `ensurepip` 缺失会留下「有 python、没 pip」的目录，脚本只看 `bin/python` 在不在就会复用，接着报 `No module named pip`（看着像依赖问题）。修法：用 `python -m pip --version` 判定完整性，不完整就删了重建。
3. **`robot --version` 退出码是 251**（输出正常）。配 `set -o pipefail` 会让 `set -e` 在管道处直接退出，连后面几行都不打印。修法：一律按输出内容判定，不靠退出码。
4. **`pabot --version` 是 6 行组合输出**，最后一行是 RF 版本。`tail -1` 会取成 RF。修法：`grep -m1 '^Version'`。

另：`scripts/*.sh` 在工作区可能是 CRLF（`.gitattributes` 已规定 `eol=lf`，入库时会转），但**在 WSL 里直接跑会报 `$'\r'`**。本次已把新加脚本转 LF；`fixtures/roundtrip/` 下的 CRLF 是往返测试语料，**故意保留不动**。

## 原「未就绪的两项」—— 2026-11-04 01:20 已全部解除

**一、数据库口令**：大师授权重置，已执行。原哈希备份于 `~/.robot-auth-backup.txt`，`password_expired` 已清。`robot_it` 迁到 **V7_1 / 44 表**，`SchemaIT` **5/5 通过**。→ `WS-04` / `WS-05` / `CAS-03` 解锁。

**二、台账口径**：大师定 **「补别名，接受命令列丢失」**，已落地。

- `HEADER_ALIASES` 补入「测试编号 → caseCode / 测试用例 → title / 一级分类 → category / 测试类型 → category / 预置条件 → precondition」，对账 **1/14 → 6/14 `[PASS]`**。
- **接受的代价（定案，不再作为返工项）**：① 测试步骤 / A端命令 / B端命令 / 交换机配置 **4 列无处存放，导入时静默丢弃**；② 台账无优先级列 → 226 条全部落 `P2`；③ 「执行顺序」故意不映射 priority（映射后 `Priority.from` 抛「非法优先级」，226 行全判错）。
- 真正支持带命令的台账要扩用例模型，属产品设计变更，须另走评估 → PRD。

**这一个发现值得单独说**：它解释了为什么「461 条导入」从 2026-11-02 起一直挂着 `待验` —— 真实数据从来没跑过，而唯一到手的数据恰好读不了。

## 本次抓到的三个「验证在说谎」（比缺功能更危险）

| 缺陷 | 症状 | 修法 |
|---|---|---|
| **`mvn -Pit test` 空跑** | `Tests run: 0 / BUILD SUCCESS`。`SchemaIT` 不符合 surefire 默认 `*Test` 命名，**从未被扫描过** —— 「用 -Pit 跑集成测试」一直是空话 | pom 加 `**/*IT.java` include；现真跑 5/5 |
| **`ArtifactVerifier` 假绿** | 只判 `totalTests > 0`；8 条丢 2 条时 6>0 仍返回 `ok`，部分丢失被放行 | `verify(path, expectedCaseCount)` + `checkShards(dir)`；单测 5→11 |
| **`check_import_header` 假红** | 正则跨不过 `//` 注释，夹注释的 `alias("caseCode", …, "测试编号")` 整条被丢弃，别名统计错报 30/8（正确 36/9）→「caseCode 认不出来」是假结论 | 先剥注释再解析 + 调用点计数自检；用「部分可解析」样例验证自检会拦（`2 处调用只认出 1 条`） |

## 测试与构建复跑（2026-11-04 01:35）

- 全量单测：`mvn -B test` → **`Tests run: 450, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS**
- 集成测试：`DB_NAME=robot_it mvn -B -Pit test` → **`SchemaIT` 5/5，BUILD SUCCESS**
- 顺带修掉 2 条**平台不可移植**用例（`PdfRendererTest.timeoutIsKilled`、`ProcessTreeKillerTest` 硬编码 `cmd` / `.cmd`，在 Linux 必红；产品实现本身跨平台，只是测试造场景的方式不可移植）。修法：按平台生成 `.cmd` / 带 shebang 的 `.sh`（chmod 700），**不跳过** —— 否则「超时杀浏览器」在 Linux 上永远没验过。
