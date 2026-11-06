# CAS-02 补记：用例集接口的 403 / 404 / 409 口径（2026-11-06）

## 1. 为什么补

WBS #17 的「仍未验」里留了一条：**后端 case-sets 的 HTTP 层 403 / 404 / 409 集成测试**。
此前 `TestSetService` 有单测，但接口层从未断言过「业务异常 → HTTP 状态码」这一跳，
一旦 `GlobalExceptionHandler` 的映射被改动，单测仍全绿、线上却变成 500。

## 2. 做法

新增 `backend/src/test/java/com/qa/autotest/api/caseasset/TestSetControllerTest.java`，
用 `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler())`：
走的是**线上同一个**异常处理器，不是另写一份映射规则。

| 用例 | 断言 |
|---|---|
| 列表：默认参数与字段 | `size=500` 被夹到 200（`Math.min(size, 200)`）；`setCode` / `itemCount` / `total` 原样返回 |
| 403 / 404 / 409 三个口径 | `create` 越权 → `403` + `FORBIDDEN`；`get` 不存在 → `404` + `NOT_FOUND`；`setCode` 重复 → `409` + `CONFLICT`（消息原样带回） |
| 成员与排序透传 | `addItem` → `(7, 3, 41)`；`removeItem` → `(7, 3, 41)`；`reorder` 捕获到的 `caseIds` 顺序与请求**完全一致**（`[41,42,43]`，不排序、不去重）；`delete` → `(7, 3)` |
| 成员 404 | 用例不在本项目 → `404` + `NOT_FOUND` |

排序那条是刻意加的：`reorder` 的语义就是「用户拖出来的顺序」，被重排等于静默改结果，
只断言「调用过」抓不到这种错。

## 3. 门禁

```
$ cd backend && mvn -o test -Dtest=TestSetControllerTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

原始输出见同目录 `backend-test-testsetcontroller-20261106.log`。

全量门禁（同目录 `*-20261106.log`）：

| 门禁 | 读数 |
|---|---|
| `mvn -o test` | **566 / 566**（新增后由 562 增至 566） |
| `vitest run` | 28 文件 / **200 用例** |
| `vue-tsc --noEmit` | 0 错误 |
| `vite build` | 成功（10.57s） |
| `subset_font.py` | 1029 字 / 178786 B gzip，未超预算 |
| `lint.sh` | PASS |
| `check_release_docs.py` | 7 版本 / FAIL 0 / WARN 0 |
| `check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |

## 4. 仍未验（人工）

- 浏览器四态截图与深色主题人工走查 → 归 `UX-04b`。
- 真实账号（非管理员）在**线上 Spring Security 链路**里的 403：本测试用 standalone 环境，
  只证明「业务异常 → 状态码」这一跳；过滤器链与租户拦截的 403 由 `HRD-01` 的真实 HTTP 全链路覆盖。
