# G1 出口判据核验 —— 后端启动与干净环境构建（2026-11-04）

对应 `docs/自动化测试平台中长期工作规划.md:263` G1 出口三条：

| G1 判据 | 状态 | 证据 |
|---|---|---|
| 新机器 30 分钟内跑起空壳 | **达成（实测约 1 分钟）** | 本文 §一、§二 |
| CI 在 PR 上绿 | **未达成** | 需先注册 self-hosted runner（等同 PLN-03 的 GitHub token 依赖） |
| ArchUnit 能拦住一次故意的跨层调用 | **已满足（此前已存在）** | `LayerRulesMetaTest` 3 条负向 + 1 条控制组 |

## 零、先纠正一个前提

仓库里早就有 `LayerRulesMetaTest`，用故意违规的 fixture 包跑同一条规则，断言必须抛
`AssertionError` 且消息里带违规类名（`DomainUsingApi` / `ApiCallingInfra` / `JpaAnnotatedEntity`），
并额外用「干净领域对象」做控制组，防止规则把一切判红。

**所以 G1 第三条不需要重做**。这种时候应当报「已有证据」，而不是为了凑数再造一个测试。

## 一、干净目录构建（判据：30 分钟内）

从工作区复制一份（排除 `target` / `node_modules` / `.git` / `artifacts`）到 WSL 原生盘，
模拟「新机器拿到源码」：

```
rsync -a --exclude target --exclude node_modules --exclude .git --exclude artifacts \
      /mnt/e/workspace/自动化测试/Robot-Autotest/  ~/build/ra/     →  28 MB

cd ~/build/ra/backend && mvn -B -DskipTests clean package
  [INFO] Compiling 278 source files with javac [debug parameters release 21]
  [INFO] Compiling 72 source files with javac [debug parameters release 21]
  [INFO] BUILD SUCCESS
  [INFO] Total time: 11.916 s        WALL=13.63s
  产物：robot-autotest-backend-0.1.0-SNAPSHOT.jar = 47 MB（可执行胖 jar）

java -jar … --server.port=18081
  Tomcat started on port 18081 (http) with context path '/'
  Started RobotAutotestApplication in 4.544 seconds
  探活：/ → 404    /api/auth/me → 401
```

**从空目录到服务可访问 ≈ 20 秒**（13.6s 构建 + 4.5s 启动），远低于 30 分钟门槛。
注意 13.6s 的前提是 `~/.m2` 已有依赖；首次拉依赖的完整构建实测为 **3 分 51 秒**（见
`env02-build-test-20261104.log` / `mvn-compile-wsl.log`），仍在门槛内。

原始日志：`docs/evidence/v0.1/g1-jar-boot.log`

## 二、启动自检脚本

`scripts/boot-check.sh` 把上面的判定固化成命令。它只认日志里那行
`Started RobotAutotestApplication in X.X seconds` 作为通过判据 —— 端口能连上但这行没出现，
说明 Tomcat 起来了而应用上下文失败，这种情况必须判失败（不能看端口就宣布成功）。

```
bash scripts/boot-check.sh
  [ OK ] 启动成功，耗时 14s
  Started RobotAutotestApplication in 2.409 seconds
  HTTP 探活（401/403/404 属正常；5xx 视为失败）：
    /                404
    /api/auth/me     401
    /api/projects    401
  [PASS] 启动自检通过
```

## 三、这一步抓到的真缺陷：未匹配路径返回 500

**这是「从没起过 Spring 上下文」才会漏掉的一类问题** —— 450 条单测 + 6 条集成测试
全绿，但没人起过应用。

真实启动后 `GET /` 返回 **500「服务内部错误」**并打一条 ERROR 堆栈：

```
ERROR c.q.autotest.api.GlobalExceptionHandler : 未预期异常
org.springframework.web.servlet.resource.NoResourceFoundException: No static resource .
```

根因：Spring 6.1 起，没匹配到任何 handler 的请求会抛 `NoResourceFoundException`
（落到静态资源处理器），而 `GlobalExceptionHandler` 只对 405 做了专门映射，这个异常落进了
`@ExceptionHandler(Exception.class)` 兜底分支 → 500。

危害不是「多一条日志」：前端路由写错、用户输错 URL、扫描器探测都会伪装成服务端故障，
**真正的 500 会被淹掉**。

修法：`GlobalExceptionHandler` 增加 `NoResourceFoundException` / `NoHandlerFoundException`
→ 404，与既有的 405 分支同一口径（该分支原有注释就写着「不能落到兜底分支变成 500」）。
新增 `GlobalExceptionHandlerTest` 3 条：404 映射、405 不回退、兜底仍是 500 且不泄露异常细节。

**验证方式是真启动后 curl**，不是只跑单测：修复后 `/` → `404`（见 §一、§二）。

## 四、顺带摸清的两个环境坑（已写入环境基线）

1. **`/mnt/e`（9p → NTFS）上不能打包**：`spring-boot:repackage` 要把 jar 改名成
   `.jar.original`，9p 上直接失败
   `Unable to rename '…jar' to '…jar.original'`，之后该 jar 连 `rm`/`mv` 都报
   `Device or resource busy`。**打包要在 WSL 原生盘（如 `~/build`）做。**
2. **`/mnt/e` 上跑 `spring-boot:run` 极慢**：从类目录加载几千个 `.class` 走 9p，
   应用上下文初始化 **45 秒以上**（180 秒超时都没起完）；同一份代码在原生盘 **2.4 秒**，
   胖 jar **4.5 秒**。**日常启动验证用原生盘或 jar，别在 `/mnt/e` 上跑。**

## 五、仍未达成 / 未验

- **CI 在 PR 上绿**：runner 未注册，`WS-05` 仍受阻。这是 G1 唯一未达成的判据。
- 前端（Vite dev server）未纳入本次启动自检 —— 只验了后端「空壳」。
- 未做并发/多次启动的稳定性观察（单次启动成功不等于稳定，属 G7 范畴）。
