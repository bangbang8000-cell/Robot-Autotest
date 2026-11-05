# RPT-01 / RPT-02 补口证据：真实 pabot 产物 + 万行 XML

- 日期：2026-11-05
- 范围：v0.5 #38（RPT-01 output.xml 解析）/ #39（RPT-02 运行详情）
- 状态：**待验**（不新增包号，两行仍记 #38 / #39）
- 环境：WSL2 Ubuntu-24.04，`~/rf-stack/.venv`，RF 7.5.1 / pabot 5.2.2 / JDK 21

## 1. 为什么补这一包

RPT-01 的阻塞列写的是「真实 pabot 合并产物未生成，fixture 为手写」，
RPT-02 写的是「万行 XML 与真 pabot 产物未在本机验证」。两件事本机都能做，只是之前没做。
做完立刻暴露了一个手写 fixture 永远测不出的缺陷（见 §3）。

## 2. 真实 pabot 合并产物（RPT-01）

```bash
cd ~/robot-autotest
pabot --processes 2 --outputdir /tmp/rpt01-real --output output.xml \
      --log NONE --report NONE fixtures/bench/4suite-2cases
rebot --output /tmp/rpt01-real/merged-meta.xml --log NONE --report NONE \
      --metadata RUN_ID:RPT01-REAL-20261105 --metadata DUT:bench-4suite \
      --metadata LINK:local-wsl /tmp/rpt01-real/output.xml
```

- 产物：`172` 行 / `8` 用例（4 套件 × 2），pabot 2 进程耗时约 25s，8/8 PASS
- 真实 metadata 形态（RF 7.5.1）：`<meta name="RUN_ID">RPT01-REAL-20261105</meta>`，
  值在**元素文本**里，不是 `value` 属性
- 左侧日志：`pabot-p2.log`（pabot 控制台）、`rebot-meta.log`（rebot 合并控制台）
- 说明：pabot 自己不接受 `--metadata`，metadata 由 `rebot` 合并期注入；
  pabot 合并产物里还有它自己的 `Pabot Version` / `Pabot Info` 两个键，一并参与解析。

## 3. 顺带修掉的缺陷（本包主要产出）

`OutputXmlParser` 原来只在 `START_ELEMENT meta` 时读 `value` 属性：

```java
putMetadata(rootMetadata, attribute(reader, "name"), attribute(reader, "value"));
```

RF 7.5.1 写的是 `<meta name="X">值</meta>`，没有 `value` 属性 → **所有真实产物的 metadata 值都是空串**，
而手写 fixture 用的是 `<meta name="X" value="Y"/>`，所以单测一直是绿的。

修复（`application/report/OutputXmlParser.java`）：
- `START_ELEMENT meta` 时同时记录 `name` 与 `value` 属性，并准备 `StringBuilder` 收元素文本；
- `CHARACTERS` 时把文本追加进去；
- `END_ELEMENT meta` 时取值：**`value` 属性优先，缺省用元素文本**——两种写法都认。

回归用例：`OutputXmlParserTest#rf75的meta标签值取元素文本`（fixture `fixtures/output-xml/rf75-meta.xml`），
断言 `RUN_ID` / `DUT` 有值、含 `|` 与 `[]` 的值不被截断、子套件同名键覆盖父级。

## 4. 万行 / 百万字节级解析（RPT-02）

用真实产物按套件复制放大（脚本 `gen_scale.py`，只改 id / name，元素形态保持原样）：

| 文件 | 行数 | 体积 | 套件 | 用例 | 关键字 | 消息 | 解析耗时 | 堆占用 |
|---|---|---|---|---|---|---|---|---|
| `merged-meta.xml`（真实） | 172 | 9 KB | 5 | 8 | 16 | 16 | 47 ms | 56 MB |
| `output.xml`（×400） | 14,428 | 790 KB | 401 | 800 | 1,600 | 1,600 | 122 ms | 62 MB |
| `output.xml`（×3000） | 108,028 | 5.9 MB | 3,001 | 6,000 | 12,000 | 12,000 | 273 ms | 47 MB |

- 解析走真实 `OutputXmlParser`（StAX 流式），探针 `ParseProbe.java` 只做遍历计数，不入库
- 无 `StackOverflowError`（套件递归深度 2 层，用例递归走关键字树）
- 结论：单次解析 5.9 MB / 108k 行走 **273 ms**，10 万行级产物不需要分片或异步解析；
  瓶颈在下载/存储，不在解析

日志：`parse-real.log`、`parse-10k.log`（前两段为本节两行大文件）。

## 5. 门禁（日志见同目录）

| 门禁 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **542** 通过（541 → +1） | `backend-test.log` |
| 后端集成 | `DB_NAME=robot_it mvn -B -Pit test` | **29** 通过 | `backend-it.log` |
| 规范 | `bash scripts/lint.sh` | PASS | `lint.log` |
| 前端 | 本包未改前端 | 不重跑（前值 165 / tsc 0 / build 0） | — |

## 6. 已知缺口

1. **8081 仍是旧 jar**：该端口有进程占用，`mvn package` 无法覆盖产物，线上页面暂不可用；
   需要先停旧进程再打包（未擅自停，怕踩到大师正在用的实例）。
2. **pabot 并发 2 的产物只覆盖 4 套件**：真实项目的多机、多 shard 产物结构更复杂，
   合并后的 `<suite>` 层级需在真实项目上再确认一次。
3. **metadata 覆盖优先级**：现在是「子套件覆盖父级」；真实项目若同名键在根与子套件都写，
   以哪个为准仍需在用例规范里写明（当前按子套件）。
