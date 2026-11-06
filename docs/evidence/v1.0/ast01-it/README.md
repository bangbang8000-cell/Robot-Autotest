# AST-01 补记：设备与端口台账的真实库集成测试（2026-11-06）

## 1. 为什么补

WBS #12 的「仍未验」只有一条：真实库集成测试待跑。`DeviceServiceTest` 全部 mock 仓储，
只能证明「服务层照着 if 走」，证明不了：

- `AssetScopeSql` 的读口径（**私有 OR 平台共享且已被本项目引用**）在真表上筛出来的确实是那几条；
- `serialExists` / `portNameExists` 里的 `id <> ?`（排除自身）在真表上成立 —— 这条最容易写错，
  写错的表现是「编辑自己时莫名 409」；
- 平台共享资产的删除闸门（FR-v0.2-51）真的拦住，而且 409 之后行还在。

## 2. 怎么跑

```bash
set -a; . ~/.config/robot-autotest/env; set +a
export DB_NAME=robot_it
cd backend && mvn -o -Pit test -Dtest=DeviceAssetIT
```

两个必要的坑（都踩过）：

1. **必须带 `-Pit`**。默认 profile 的 `excluded.groups=it` 会把 `@Tag("it")` 整个过滤掉；
   只写 `-Dtest=DeviceAssetIT` 会得到「Tests run: 0 / BUILD SUCCESS」——看着通过，实际一条没跑。
2. **库名必须以 `_it` 结尾**，测试类第一行就断言 `DB_NAME.endsWith("_it")`，
   防止哪天环境变量漏设、把 `robot_dev` 当集成库清表。
3. JDBC 前缀是 `jdbc:mariadb://`（驱动是 MariaDB Java client），写 `jdbc:mysql://` 会报
   `No suitable driver found`，看着像「驱动没引」，实际是前缀不对。

## 3. 覆盖（5 条）

| 用例 | 断言要点 |
|---|---|
| 台账往返与筛选 | 创建后字段原样回读；关键字按名称/IP/序列号命中且只命中一条；`domainTag` 筛选；`status=0` 为 0 条；`domains()` 去重且按字典序 |
| 序列号唯一 | 同项目重复序列号在**创建**与**更新**两条路径上都 409；改自己的序列号（值不变）不误报；不同项目同序列号互不影响（序列号是项目内唯一，不是全局唯一） |
| 端口增删改查 | 增后 `ports` 有 1 条；同名再加 409 且消息含「端口名已存在」；改名/改速原样回读；删后为空 |
| 平台共享资产不可硬删 | `shareToPlatform` + `addReference` 后仍能 `get`；`delete` 落 409 且消息含「平台共享资产」；409 之后 `device` 行仍在、`project_id` 为 NULL |
| 非成员 403 | 非项目成员 `page` / `get` 均 403，消息固定「无权访问该项目」（不泄露项目是否存在） |

### 一条真实的模型行为（值得记住）

`shareToPlatform` **只**把行的 `owner_scope` 改成 `PLATFORM`、`project_id` 置空，
**不会**自动给原项目补一条 `project_asset` 引用。所以共享之后、加引用之前，原项目自己也看不到它，
`delete` 会先落 `NOT_FOUND` 而不是 `CONFLICT`。第一次跑就撞在这里（见 §5）。
要在「被引用」状态下验 409，必须显式 `addReference(...)`。

## 4. 数据清理

`@AfterEach` 按 `device_port → project_asset → device → audit_log → project_member → project → user_role → app_user`
的顺序删本次造的数据；每个用例用 UUID 前 8 位做用户名/项目号后缀，互不干扰。

## 5. 实测

```
$ mvn -o -Pit test -Dtest=DeviceAssetIT
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.930 s
BUILD SUCCESS
```

第一次跑是 1 失败：`平台共享资产不可硬删` 期望 `CONFLICT` 实际 `NOT_FOUND`，
原因即 §3 末尾那条模型行为。修正为「共享 + 加引用」后通过。

## 6. 仍未验（人工）

- 前端台账页的真实操作走查（批量导入、端口表格编辑）属 UX-04b。
- 多跳拓扑不在本包（见 AST-02 备注）。
