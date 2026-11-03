# EDT-08 · 许可合规发布检查单（人签）

**文档编号** RA-REL-2026-003-EDT08 ｜ **门禁** G3 ｜ **级别** C（AI 交付，人工签署）
**核对时间** 2026-11-03 09:12:24 ｜ **证据** `docs/evidence/v0.3/edt08-license-check.log`

> **AI 不判定「合规通过」**。本单只记录实测结果；第 5 栏由签署人填写。

---

## 1. 四件事（+ 品牌）

| # | 事项 | 判定标准 | 实测结果 | 结论 |
|---|---|---|---|---|
| 1 | 本项目许可 | 根 `LICENSE` 存在 | Apache License 2.0 全文，sha256 `807A8F9B…314D09` | ✅ |
| 2 | 上游许可原文 | 根 `LICENSE-roboscope` 与包内文件字节一致 | 三份文件 sha256 **完全相同** | ✅ |
| 3 | 衍生声明 | 根 `NOTICE` 含来源/版本、许可、修改清单、商标四要素 | 四要素各命中 2~3 处 | ✅ |
| 4 | 文件头 | 每个改写文件含衍生声明 + `Modified by Robot-Autotest` | **20 个文件**逐个枚举（见证据日志第 3 段） | ✅ |
| 5 | 品牌 | 产品面（含 `dist/`）扫描 0 命中（白名单之外） | 产品面命中 71 处，全部为白名单放行；违规 **0** | ✅ |

### 1.1 复现命令

```bash
# 1+2 许可与校验和
sha256sum LICENSE LICENSE-roboscope packages/editor/LICENSE-roboscope
# 3 衍生声明四要素
grep -c "viadee/roboscope v0.16.1" NOTICE
# 4 文件头数量（期望 20）
grep -rl "Modified by Robot-Autotest" packages/editor/src | wc -l
# 5 品牌扫描（产品面 0 命中）
bash scripts/brand-scan.sh
```

### 1.2 白名单说明

`brand-allow.txt` 只放行两类，均为法定义务或语法事实：

| 放行类型 | 例子 | 理由 |
|---|---|---|
| Apache-2.0 §4(b) 归属声明 | 文件头 `衍生自 viadee/roboscope v0.16.1（Apache-2.0）` | 许可要求保留，删掉即违约 |
| Robot Framework 库名 | `Library    RoboScopeHeal` | 上游库的真实名称，改写会让用户脚本执行失败 |

---

## 2. 已知偏差与说明

| 项 | 说明 |
|---|---|
| 根许可选型 | 采用 Apache-2.0（与上游同许可，义务最少）。若签署人改选其他许可，只需替换根 `LICENSE`，其余交付物不受影响 |
| `packages/editor/NOTICE.md` | 保留为分包详细声明；根 `NOTICE` 为仓库级声明并指向它（两份内容不重复，互相引用） |
| 品牌扫描口径 | 「0 命中」指**产品面**（源码 + 构建产物）；归属面（`docs/`、`LICENSE*`、`NOTICE`、`UPSTREAM.md`）按 §4(b) 保留并逐条登记 |
| `dist/*.map` | sourcemap 内嵌源码文本，其中含归属声明与库名，已随白名单放行；对外发布时可选择不随附 sourcemap |

---

## 3. 签署

| 项 | 内容 |
|---|---|
| 许可选型是否认可（Apache-2.0） | ☐ 认可　☐ 改为：__________ |
| 白名单两条理由是否认可 | ☐ 认可　☐ 异议：__________ |
| 是否同意对外发布 | ☐ 同意　☐ 不同意 |
| 签署人 | ____________________ |
| 日期 | ____________________ |

> 签署后请把 `docs/plan/WBS-STATUS.md` 第 28 行由 `待验` 改为 `已完成`，并在证据列补本单路径。
