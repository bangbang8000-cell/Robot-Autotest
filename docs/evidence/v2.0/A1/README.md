# A1 证据 · pytest 运行时落地

- **执行时间**：2026-11-06 20:13（GMT+8）
- **结论**：**通过**。`scripts/setup-pytest-venv.sh` 已落地，`~/venvs/pytest-kernel` 建立成功，白名单三插件全部注册，脚本幂等。

## 1. 缺口背景

`config/pytest-venv.yml` 写着 `venv.created_by: "scripts/setup-pytest-venv.sh"`，但该脚本**在仓库里不存在**，`~/venvs/pytest-kernel` 也从未建立。后果：`PytestKernel`（W3）与 `PytestImportAdapter`（W4）**从未在真实 pytest 上跑过**，TC-20/22/23/24/32/36/37/38 记 NOT-RUN 的根因是环境缺一步，不是「缺外部样本」。

## 2. 命令原文

```bash
cd ~/robot-autotest
bash scripts/setup-pytest-venv.sh          # 全新建
bash scripts/setup-pytest-venv.sh          # 再跑一次，验证幂等
```

## 3. 原始输出（第二次运行，幂等路径）

```
[1/5] 配置真源: /home/evergreen/robot-autotest/config/pytest-venv.yml
      venv 路径: /home/evergreen/venvs/pytest-kernel
      python  : >=3.10
      白名单  : pytest pytest-json-report pytest-xdist
[2/5] 解释器 python3 = 3.12.3
[3/5] venv 已存在，跳过创建（随后按白名单补齐）
[4/5] 安装白名单插件
[5/5] 自检
python            3.12.3
pytest            9.2.0
pytest-json-report1.5.0
pytest-xdist      3.8.0
[OK] pytest 运行时就绪: /home/evergreen/venvs/pytest-kernel
     跑法: /home/evergreen/venvs/pytest-kernel/bin/python -m pytest <路径> -q
```

## 4. 判据逐条核对

| 判据 | 要求 | 实测 | 结论 |
|---|---|---|---|
| 脚本存在且可执行 | `scripts/setup-pytest-venv.sh` | 已建立，`bash` 直跑退出码 0 | 通过 |
| venv 路径与 config 一致 | `~/venvs/pytest-kernel` | 一致，且与 `~/rf-stack/.venv` 物理隔离（不同目录、不同解释器） | 通过 |
| 只装白名单 | pytest / pytest-json-report / pytest-xdist | `pip list` 仅 12 行，无额外联网或执行宿主命令类插件 | 通过 |
| 插件真的注册 | `--json-report` 出现在 `pytest --help` | 出现（见 §5） | 通过 |
| 幂等 | 重复执行不报错、不重复安装 | 第二次走「已存在」分支，退出码 0 | 通过 |
| config 为唯一真源 | 脚本读 `plugins.allow`，不硬编码 | 脚本用内联 python3 解析 `config/pytest-venv.yml` 的 `venv.path` / `python` / `plugins.allow` | 通过 |

## 5. `--json-report` 注册证明

```
$ grep -i json --  <(pytest --help)
  --json-report         create JSON report
  --json-report-file=JSON_REPORT_FILE
  --json-report-omit=JSON_REPORT_OMIT [JSON_REPORT_OMIT ...]
```

## 6. 踩到的一个坑（写进脚本注释）

自检最初写成 `pytest --help | grep -q -- "--json-report"`，在 `set -o pipefail` 下**恒假**：`grep -q` 命中即退出关闭管道，pytest 写 stdout 时收到 SIGPIPE（退出 141），`pipefail` 让整条管道返回 141，于是「装了却报没装」。改为先取 `HELP_TEXT="$(pytest --help || true)"` 再 `case` 匹配。

## 7. 文件

| 文件 | 内容 |
|---|---|
| `setup.log` | 脚本完整输出（幂等路径） |
| `pip-list.log` | venv 内实际安装的 12 个包 |

## 8. 遗留

- `pytest-json-report` 1.5.0 与 pytest 9.2.0 **实测兼容**（`--json-report` 注册、JSON 产出正常），但该插件自 2022 年后无更新，升级 pytest 主版本时需重测本项。
- venv 不在仓库内，换机需重跑本脚本；`scripts/gates.sh` 收敛（B4）时建议加一条「`config/*.yml` 里 `created_by` 指向的脚本必须存在」的机检，防同类缺口复发。
