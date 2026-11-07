# 证据 · WS-02 后端静态检查（Checkstyle）接入

**日期** 2026-11-06 ｜ **对应** WBS #2 `WS-02`「后端静态检查（checkstyle）未接入」缺口
**性质** 真实接入 + 真实阻断，不是「只报告不拦」的假门禁
**授权** 大师 11-06「更新规划与三份计划后同步展开工作」；P1 可自动验证的技术包

---

## 1. 改动清单

| 文件 | 改动 |
|---|---|
| `backend/config/checkstyle.xml` | **新增**最小可用规则集：`NewlineAtEndOfFile` / `FileTabCharacter` / 禁止 `System.out.println` / `AvoidStarImport` / `RedundantImport` / `UpperEll` / `ArrayTypeStyle` / `EmptyStatement` / `EqualsHashCode`；`severity=error` |
| `backend/pom.xml` | 新增 `maven-checkstyle-plugin` **3.6.0**，`failOnViolation=true`、`failsOnError=true`、`violationSeverity=error`、`includeTestSourceDirectory=false` |
| `scripts/lint.sh` | 后端块在 compile 前插入 `mvn -B -q checkstyle:check`，日志落 `artifacts/mvn-checkstyle.log`；头注释与块内注释同步改写（原写「checkstyle 未接入」） |

**规则集为什么从「存量零违规」起步**：接入前实测 429 个 `.java`，星号导入 0、制表符 0、
`src/main` 的 `System.out.println` 0（6 处全在 `src/test`，按 `includeTestSourceDirectory=false` 不检查）。
先保证「规则一开就是绿的且会拦」，后续按 DOC-02 逐条扩展时必须**先跑全量扫描、清完存量再开新规则**，
不允许「先开规则再慢慢修」把门禁弄成长期红灯。

---

## 2. 实测读数

| 项 | 命令 | 结果 |
|---|---|---|
| 存量扫描 | `find backend/src -name "*.java" \| wc -l` | **429** 个 Java 文件 |
| 星号导入 | `grep -rl "^import .*\*;" backend/src --include="*.java" \| wc -l` | **0** |
| 制表符 | `grep -rlP "\t" backend/src --include="*.java" \| wc -l` | **0** |
| `System.out.println` | `grep -rl "System.out.println" backend/src --include="*.java"` | **6 个，全部在 `src/test`**（5 个 IT + 1 个 Test），生产代码 0 |
| 首次实跑 | `cd backend && mvn -B checkstyle:check` | **0 Checkstyle violations / BUILD SUCCESS / 14.582 s**（Checkstyle 9.3，插件与依赖从阿里云镜像下载） |

### 元验证（守门必须「注入真违规必红」）

```bash
cd backend
printf 'package com.qa.autotest;\nimport java.util.*;\npublic class CheckstyleProbe {}\n' \
  > src/main/java/com/qa/autotest/CheckstyleProbe.java
mvn -B -q checkstyle:check     # 必须 FAIL
rm src/main/java/com/qa/autotest/CheckstyleProbe.java
mvn -B -q checkstyle:check     # 必须 PASS
```

| 步 | 实测输出 |
|---|---|
| 注入星号导入 | `EXPECTED_FAIL`；`[ERROR] There is 1 error reported by Checkstyle 9.3 with config/checkstyle.xml ruleset` |
| 移除探针 | `PROBE_REMOVED` |
| 复原复跑 | `CLEAN_PASS` |

**结论**：`failOnViolation=true` 生效，`lint.sh` 里的这一步是**真阻断**，不是占位。

---

## 3. 刻意不做的事

| 项 | 理由 |
|---|---|
| **不改 WBS #2 状态、不动五数**（仍 14 待验 / 75 已完成） | 项目踩过「只改汇总不改单包行」「分组表与汇总漂移」的坑（WBS 修订记录 UX-04b(8)）。本轮只在 #2 行备注里写「技术缺口已收口」，状态留到 **P1 批量 AI 验收**时与其它技术包一起改，一次改齐四处 |
| 不扫 `src/test` | 6 处 `System.out.println` 是 IT / 单测的诊断输出，扫了会制造无意义红灯；规则集注释里已写明 |
| 不开更多规则 | 429 个存量文件未经全量扫描前开新规则 = 把门禁弄红；DOC-02 后续按「先清存量再开规则」推进 |
| 不改任何 FR / TC / G8 判据 | 本包是静态检查接入，不涉产品行为 |

---

## 4. 全量门禁

| 项 | 实测读数 |
|---|---|
| 八步 + 2 项专属 | **10/10 PASS，FAILS=0**（`gates.sh` 全批重跑，含改动后的 `lint.sh`） |
| `06-lint` | **PASS，11 s**（此前 8 s；增量即 Checkstyle 首次编译与审计） |
| 后端单测 | `mvn test` **631/631**，Failures 0 / Errors 0 |
| 前端 | `vitest` **28 文件 / 206 用例**全过；`type_check` / `frontend_build` PASS |
| 版本文档门禁 | 8 个版本 **FAIL 0 / WARN 0** |
| WBS 自洽 | **96 包 = 0 / 0 / 14 / 7 / 75**（本包**未改状态与五数**，见 §3） |
| Markdown 表格 | 本轮新文档 7 份 / 79 块 / 548 行列数一致 |
| `git diff --check` | 无空白错误 |

> 门禁日志在本机 `docs/evidence/gates/logs/`（`.gitignore` 忽略，不入库），
> 复跑 `gates.sh` 可重建；Checkstyle 自身日志在 `artifacts/mvn-checkstyle.log`。

---

## 5. 复跑命令

```bash
cd ~/robot-autotest
bash scripts/lint.sh                                    # 含 checkstyle 真实阻断
cd backend && mvn -B checkstyle:check                    # 单独复跑
cd ~/robot-autotest && bash scripts/gates.sh
```
