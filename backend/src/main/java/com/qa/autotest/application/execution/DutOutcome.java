package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.DutCollection;

/**
 * DUT 采集结论（EXE-08）。
 *
 * <p>{@code kind} 取值固定为五类常量；失败时 {@code advice} 给出可执行的下一步建议，
 * 成功时为空串。{@code collection} 保留三条命令的原始输出，供报告与排障使用。
 */
public record DutOutcome(String kind, String advice, DutCollection collection) {

    public static final String KIND_OK = "ok";
    public static final String KIND_UNREACHABLE = "unreachable";
    public static final String KIND_AUTH_FAILED = "auth_failed";
    public static final String KIND_CMD_NOT_FOUND = "cmd_not_found";
    public static final String KIND_COMMAND_FAILED = "command_failed";

    public static final String ADVICE_UNREACHABLE = "检查 180 段路由与跳板机";
    public static final String ADVICE_AUTH_FAILED = "检查凭据或公钥";
    public static final String ADVICE_CMD_NOT_FOUND = "确认 DUT 系统含该工具";
    public static final String ADVICE_COMMAND_FAILED = "查看 stderr 原文";
}
