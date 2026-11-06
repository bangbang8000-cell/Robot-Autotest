package com.qa.autotest.application.node;

import java.util.List;

/**
 * 主控 → 节点的任务载荷（NODX-03）。
 *
 * <p>随 POST {agent_base_url}/api/agent/run 的 body 发送，头带
 * {@code X-Task-Signature}（HMAC-SHA256，key = token_hash，见 {@link TaskSigner}）。
 * 字段与 {@code RunService.CreateRunCommand} 的执行相关子集一一对应——
 * 节点侧用**同一套 PabotCommandBuilder / KRN** 按这些参数生成命令，
 * 这是「同参数命令 diff 为空」判据的对齐点。
 *
 * @param masterResultUrl 主控回传端点（节点跑完后 POST output.xml 到这里）
 * @param masterBaseUrl   主控基址（agent 心跳用，= masterResultUrl 去掉 /api/agent/result）
 */
public record NodeTask(String runId,
                       long projectId,
                       List<String> suitePaths,
                       int concurrency,
                       Long environmentId,
                       List<String> deviceKeys,
                       Integer timeoutSeconds,
                       Integer testTimeoutSeconds,
                       String includeTag,
                       List<String> testNames,
                       String masterResultUrl,
                       String masterBaseUrl) {
}
