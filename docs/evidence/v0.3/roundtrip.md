# EDT-06 往返保真报告（L1 dryrun + L2 AST）

- 语料根目录：`corpus`
- 序列化产物：`serialized`
- 样本数：**51**
- L1 `robot --dryrun`：原文/产物各跑一次，成功 **98** 次、失败 **0** 次、跳过 **2** 个（无用例文件）
- L2 AST 归一化比对：一致 **51** 个、不一致 **0** 个
- 注释 token（允许丢失，DEV-PLAN §10 ≤5%）：原文 **27** / 产物 **27**
- 总判定：**PASS**

| 样本 | L1 原文 | L1 序列化产物 | L2 AST | 备注 |
|---|---|---|---|---|
| `legacy/legacy-01_TN-IMP-014.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-02_TN-IMP-020.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-03_TN-IMP-025.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-04_TN-IMP-057.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-05_TN-IMP-069.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-06_TN-IMP-089.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-07_TN-IMP-095.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-08_TN-IMP-096.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-09_TN-IMP-101.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-10_TN-IMP-118.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-11_TN-IMP-119.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-12_TN-IMP-123.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-13_TN-IMP-136.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-14_TN-IMP-142.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-15_TN-IMP-159.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-16_TN-IMP-167.robot` | PASS | PASS | PASS |  |
| `legacy/legacy-17_TN-IMP-176.robot` | PASS | PASS | PASS |  |
| `prod/resources/common.robot` | SKIP(无用例) | SKIP(无用例) | PASS | 资源文件，L1 不适用 |
| `prod/suites/01_connectivity.robot` | PASS | PASS | PASS |  |
| `prod/suites/02_gpu_health.robot` | PASS | PASS | PASS |  |
| `prod/suites/03_storage_health.robot` | PASS | PASS | PASS |  |
| `prod/suites/04_r750_health.robot` | PASS | PASS | PASS |  |
| `prod/suites/05_resource_report.robot` | PASS | PASS | PASS |  |
| `prod/suites/perf/PERF-IPERF-200G-001.robot` | PASS | PASS | PASS |  |
| `prod/suites/perf/PERF-IPERF-200G-002.robot` | PASS | PASS | PASS |  |
| `prod/suites/regression_suite.robot` | PASS | PASS | PASS |  |
| `prod/suites/roce/RDMA-PERF-200G.robot` | PASS | PASS | PASS |  |
| `prod/suites/smoke_suite.robot` | PASS | PASS | PASS |  |
| `prod/test.robot` | PASS | PASS | PASS |  |
| `prod/workspace/10_control_flow.robot` | PASS | PASS | PASS |  |
| `syntax/01_for_in_enumerate.robot` | PASS | PASS | PASS |  |
| `syntax/02_if_else_if_else.robot` | PASS | PASS | PASS |  |
| `syntax/03_try_except_finally.robot` | PASS | PASS | PASS |  |
| `syntax/04_while_limit.robot` | PASS | PASS | PASS |  |
| `syntax/05_nested_variable.robot` | PASS | PASS | PASS |  |
| `syntax/06_escaped_variable.robot` | PASS | PASS | PASS |  |
| `syntax/07_continuation.robot` | PASS | PASS | PASS |  |
| `syntax/08_template_table.robot` | PASS | PASS | PASS |  |
| `syntax/09_setup_teardown_timeout.robot` | PASS | PASS | PASS |  |
| `syntax/10_chinese_keywords.robot` | PASS | PASS | PASS |  |
| `syntax/11_comments_dense.robot` | PASS | PASS | PASS |  |
| `syntax/12_variables_list_dict.robot` | PASS | PASS | PASS |  |
| `syntax/13_library_with_name.robot` | PASS | PASS | PASS |  |
| `syntax/14_resource_import.robot` | PASS | PASS | PASS |  |
| `syntax/15_old_style_run_keyword_if.robot` | PASS | PASS | PASS |  |
| `syntax/16_very_long_line.robot` | PASS | PASS | PASS |  |
| `syntax/17_tab_and_space.robot` | PASS | PASS | PASS |  |
| `syntax/18_no_trailing_newline.robot` | PASS | PASS | PASS |  |
| `syntax/19_crlf.robot` | PASS | PASS | PASS |  |
| `syntax/20_emoji_fullwidth.robot` | PASS | PASS | PASS |  |
| `syntax/syntax_helper.resource` | SKIP(无用例) | SKIP(无用例) | PASS | 资源文件，L1 不适用 |
