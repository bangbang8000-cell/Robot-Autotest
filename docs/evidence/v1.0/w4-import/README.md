# W4 / IMP-01..05 导入与沙箱 · 交付证据（2026-11-06）

## 实现

| 包 | 交付 |
|---|---|
| IMP-01 | `db/migration/V8_2__import_sandbox.sql`：`import_batch` / `case_source`，**用例不带设备外键**；平台/项目可见性 |
| IMP-02 | `ImportSandbox.isSafeRelativePath()` + zip 限额（1000 项 / 64 MiB 总 / 8 MiB 单文件 / 12 层）+ `normalize().startsWith(target)` 防 zip slip + 只读落盘 + SHA-256 |
| IMP-03 | `PytestImportAdapter`：`pytest --collect-only -q` / `python3 -m compileall -q` 只出预检命令，本机不跑外部工程执行链 |
| IMP-04 | `RobotImportAdapter`：`robot --dryrun --output NONE --report NONE --log NONE` |
| IMP-05 | `PreflightService`：预检报告 + 敏感扫描 + 重导入 diff；`ImportController` 三个端点 |

## 实测

- `ImportSandboxTest`：12 条不安全路径逐条拒绝（`../`、绝对路径、Windows 盘符、`~`、`//`、NUL、控制字符、过深路径）；zip slip 整批拒绝；正常 zip 只读落盘并出校验和。
- 前端 `ImportPreflightView.vue` + 路由 `/imports` + 命令面板入口；`imports.spec.ts` 纯逻辑单测。
- 迁移 `V8_2` 实跑并核表，日志 `docs/evidence/v1.0/w2-w7-preconditions/migration-v8_2-v8_4.log`。

## 边界（不冒充现场）

- C2/C3 真实工程只登记为**本机预检级样本**（`hermes-agent/tests`、`fixtures/roundtrip/legacy`）；
  G8 第 4 条「真实工程现场确认」仍待，本机 TC 记 `PARTIAL`。
- `--collect-only` / `--dryrun` 只是构造出的预检命令，本机**未**执行完整外部工程执行链。
- G4 未判过、G8 不申请。

## C9 新表登记

`import_batch`、`case_source`（`scripts/rollback.sh` 全库 `mysqldump` 自动纳入备份）。
