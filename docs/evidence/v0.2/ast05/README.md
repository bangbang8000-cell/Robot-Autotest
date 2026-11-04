# AST-05 资产共享池 · 验收证据

日期：2026-11-04　执行环境：WSL2 Ubuntu-24.04 / MySQL 8.0.46 / JDK 21
关联：PRD FR-v0.2-50、FR-v0.2-52　DEV-PLAN §10.8　TEST-PLAN §13.9　WBS #69

## 1. 交付物

| 文件 | 说明 |
|---|---|
| `db/migration/V7_3__asset_pool.sql` | 三表加 `owner_scope`、`project_id` 可空；`project_asset` 引用表；存量回填；平台级设备序列号唯一 |
| `db/down/V7_3__asset_pool.down.sql` | 结构回滚；先按引用把平台资产还回项目，再收紧 `project_id` |
| `backend/.../infrastructure/AssetScopeSql.java` | 唯一读口径常量（私有 OR 平台共享且已引用） |
| `backend/.../application/repository/ProjectAssetRepository.java` + `infrastructure/ProjectAssetRepositoryJdbc.java` | 归属查询、共享、引用、解除引用 |
| `backend/.../application/asset/ProjectAssetService.java`、`api/asset/AssetShareController.java` | 3 个接口 + 权限与审计 |
| `backend/.../infrastructure/{Device,Link,Credential,Overview}RepositoryJdbc.java` | 读口径改为拼接 `AssetScopeSql` |
| `backend/.../application/asset/{Device,Link,Credential}Service.java` | 共享资产对引用方只读（403） |
| `backend/.../db/SchemaIT.java` | 表数 45→46，新增 TC-AST-05-01/02，db-audit 期望同步 |
| `scripts/db-audit.sh` | 业务表期望 45→46 |

## 2. 升级前后资产不变（robot_dev 实跑）

升级前：3 设备 / 2 链路 / 2 凭据；升级后：**3 / 2 / 2**，id 集合一致（1~3、1~2、1~2）。
`project_asset` 回填 7 条（3 DEVICE + 2 LINK + 2 CREDENTIAL），全部 `owner_scope=PROJECT`，
无任何「自动转平台」记录。明细见 `db-check.txt`。

## 3. 迁移回滚（robot_it 从空库实测）

1. `db-reset`：空库 → V7_3 全部迁移成功（46 表）。
2. 造一条共享设备（`owner_scope=PLATFORM`、`project_id=NULL`，附 1 条引用）后执行 `V7_3__asset_pool.down.sql`：
   - 设备 `project_id` 由引用记录还原为该引用项目；
   - `project_asset` 删除；三表 `owner_scope`、`platform_serial` 删除；
   - 唯一键 `uk_device_serial` 删除；
   - `project_id` 收紧为 NOT NULL 成功。
3. 再次 `db-reset` → 回到 V7_3，SchemaIT 7/7 通过。

> 回滚限制（写入脚本头注释）：一条平台资产被多项目引用时只能还回最小 project_id；
> 零引用的平台资产会让最后一步 NOT NULL 失败中止——宁可回滚失败，也不悄悄改归属。

## 4. 接口验收（robot_dev，admin 会话令牌）

| # | 动作 | 结果 |
|---|---|---|
| 1 | `GET /api/auth/me` | 200 |
| 2 | `GET /api/projects/1/devices` | 200，3 台 |
| 3 | `POST /api/projects` 建 `AST05-B` | 200，id=2 |
| 4 | `POST /api/assets/device/3/share`（小写类型也认） | 200 |
| 5 | `GET /api/projects/1/devices` | 200，仍 3 台（视图不断） |
| 6 | `GET /api/projects/2/devices` | 200，0 台（未引用不可见） |
| 7 | `POST /api/projects/2/assets/DEVICE/3` | 200 |
| 8 | `GET /api/projects/2/devices` | 200，1 台（leaf-switch-01） |
| 9 | `PUT /api/projects/2/devices/3` | **403**「平台共享资产只能由归属项目修改」 |
| 10 | `DELETE /api/projects/2/assets/DEVICE/3` | 200，只删引用 |
| 11 | `GET /api/projects/2/devices` | 200，回到 0 台 |
| 12 | 重新引用（留作界面走查） | 200 |

## 5. 界面走查

| 截图 | 场景 | 读数 |
|---|---|---|
| `ast05-a-assets.png` | demo（项目 1）资产页 | 3 台设备（gpu-node-01/02、leaf-switch-01），与升级前一致 |
| `ast05-b-assets.png` | AST05-B 资产页 | 1 台，且只有被引用的 leaf-switch-01 |
| `ast05-b-overview.png` | AST05-B 总览 | 资产卡「设备 1」，与资产页一致 |

页面操作说明：AST-05 只动读口径与 API，前端资产页无需改动；切换顶部项目下拉即可看到差异。

## 6. 守门结果

| 检查 | 结果 |
|---|---|
| `mvn test` | 466/466 通过 |
| `SchemaIT`（robot_it 空库） | 7/7 通过（含 TC-AST-05-01/02） |
| `OverviewRepositoryIT` | 通过（总览资产计数与资产页同口径） |
| `scripts/db-audit.sh` | PASS（46 表、审计四列、主键、时间类型、字符集） |
| `scripts/lint.sh` | **未通过，但与本包无关**：`secret-scan` 命中仓库既有 3 处变量引用（`scripts/db-common.sh:31`、`rollback.sh:44`、`stability-24h.sh:46`），属正则把 `"${DB_PASSWORD:-}"` 当明文，非本包引入；后端编译与 ESLint 部分均已单独通过 |

## 7. 演示数据（留在 robot_dev 供复查）

- 项目 `AST05-B`（id=2，code `AST05-B`）、成员无（admin 全局可见）；
- 设备 3 `leaf-switch-01` 已共享到平台，项目 1 仍可见，项目 2 引用它；
- 走查账号 `ast05`（仅本地），口令不落盘，需要时向执行者索取。

## 8. 留给 PRJ-03 的边界

1. 共享资产的「解除引用保护」（被引用>0 时拒绝转私有/删除）尚未做；
2. 转私有（PLATFORM → PROJECT）未做；
3. 删除私有资产/项目时清理 `project_asset` 冗余引用未做（project 删除已用 FK CASCADE）。
