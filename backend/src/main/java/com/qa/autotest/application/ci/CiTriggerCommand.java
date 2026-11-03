package com.qa.autotest.application.ci;

/**
 * CI 触发命令（POST /api/ci/trigger 的已校验入参）。
 *
 * @param build 为空时由服务端取 commitSha 前 8 位
 */
public record CiTriggerCommand(long planId,
                               String commitSha,
                               String ref,
                               String build,
                               String repo) {
}
