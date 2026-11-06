# CAS-04 补记：文件工作区 6 端点的接口层验证（2026-11-06）

## 1. 为什么补

WBS #19 的「仍未验」只有一条：**6 端点集成测试待跑**。`FileWorkspaceService` 有
`FilePathTest`（路径规范化与穿越拦截），但接口层从未断言过「路由 + 参数透传 + 业务异常 → HTTP 状态码」。
一旦 `@RequestParam` / `@RequestPart` 的名字被改动，或 `GlobalExceptionHandler` 的映射变化，
服务层单测仍全绿、前端却拿不到数据。

## 2. 做法

新增 `backend/src/test/java/com/qa/autotest/api/caseasset/FileControllerTest.java`，
`MockMvcBuilders.standaloneSetup(new FileController(files)).setControllerAdvice(new GlobalExceptionHandler())`，
7 条用例覆盖 6 个端点：

| 用例 | 覆盖端点 | 断言 |
|---|---|---|
| 树与读取 | `GET /files`、`GET /files/content` | 目录标记 `directory` 与 `size` 原样返回；`path` 查询参数原样透传给 `read`；`currentVersion` 回显 |
| 版本列表 | `GET /files/versions` | 版本倒序与 `comment` 原样返回 |
| 保存透传 | `PUT /files` | `path` / `content` / `comment` 三个 body 字段**逐个**透传（少任一字段都会在 verify 里暴露） |
| 上传按 UTF-8 解码 | `POST /files/upload` | multipart 字节按 UTF-8 解码后走**同一条** `save` 通道（含中文断言不出现乱码） |
| 移动与删除 | `POST /files/move`、`DELETE /files` | `fromPath` / `toPath` 两端都透传；删除只传 `path` |
| 404 / 400 | `GET /files/content`、`PUT /files` | 文件不存在 → `404 NOT_FOUND`；路径非法 → `400 INVALID_ARGUMENT` 且消息原样 |
| 403 | `DELETE /files` | 无权限 → `403 FORBIDDEN` |

上传那条是刻意加的：`upload` 不是独立存储路径，而是复用 `save`。若哪天有人给上传另开一条写库分支，
「上传后可立即在版本列表看到」这条隐式契约就会静默失效，只测状态码抓不到。

## 3. 门禁

```
$ cd backend && mvn -o test -Dtest=FileControllerTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量门禁（同目录 `*-20261106.log`）：

| 门禁 | 读数 |
|---|---|
| `mvn -o test` | 573 / 573 |
| `vitest run` | 28 文件 / 200 用例 |
| `vue-tsc --noEmit` | 0 错误 |
| `vite build` | 成功（8.32s） |
| `subset_font.py` | 1029 字 / 178762 B gzip，未超预算 |
| `lint.sh` | PASS |
| `check_release_docs.py` | FAIL 0 / WARN 0 |
| `check_wbs_consistency.py` | PASS |

原始输出见同目录日志。

## 4. 仍未验（人工）

- 编辑器 UI 属 v0.3，本次不涉及。
- 真实账号在**线上 Spring Security 过滤器链**里的 403：本测试用 standalone 环境，
  只证明「业务异常 → 状态码」这一跳，过滤器链口径归 `HRD-01`。
