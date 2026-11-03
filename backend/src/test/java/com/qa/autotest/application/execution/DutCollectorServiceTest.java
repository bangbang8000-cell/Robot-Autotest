package com.qa.autotest.application.execution;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.DutCollection.CommandOutput;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXE-08 采集链路回放测试（TC-v0.4-702~705 的本机等价用例）。
 *
 * <p>本机不连 DUT：三条命令由 {@link ReplaySshClient} 返回 {@code fixtures/dut-replay/} 下的真实形态输出。
 * 断言只覆盖「解析非空 + 失败分类 + 口令不落文本」，不代表实网通过（证据首行为 {@code [REPLAY] 非实网}）。
 */
class DutCollectorServiceTest {

    private static final String IFACE = "enp23s0np0";

    /** 测试占位口令：用于证明脱敏，绝不写真实凭据。 */
    private static final String SECRET = "dut-secret-probe-2026";

    private final ExecutionProperties props = ExecutionTestSupport.props();

    private DutCollectorService service(ReplaySshClient.FailureMode mode) {
        return new DutCollectorService(new ReplaySshClient(mode), props);
    }

    private static DutTarget target() {
        return new DutTarget("10.202.54.106", 22, "root", SECRET, null,
                "192.168.180.2", "evergreen");
    }

    @Test
    void 回放三条命令解析出地址与网卡计数且分类为ok() {
        DutOutcome outcome = service(ReplaySshClient.FailureMode.NONE)
                .collectOutcome(target(), IFACE);

        assertEquals(DutOutcome.KIND_OK, outcome.kind(), "全部 exit 0 时必须判 ok");
        assertEquals("", outcome.advice(), "成功时不该给建议");
        assertEquals("root@10.202.54.106 via evergreen@192.168.180.2",
                outcome.collection().targetLabel(), "目标标签只允许 user@host 与跳板");

        List<CommandOutput> outputs = outcome.collection().outputs();
        assertEquals(3, outputs.size(), "采集命令固定三条");

        assertEquals("ip -br addr", outputs.get(0).name());
        assertTrue(outputs.get(0).stdout().contains("10.202.54.106/24"), "ip -br addr 应含 DUT 地址");
        assertTrue(outputs.get(0).stdout().contains(IFACE), "ip -br addr 应含目标接口");

        assertEquals("ethtool -S " + IFACE, outputs.get(1).name());
        assertTrue(outputs.get(1).stdout().contains("rx_errors"), "ethtool -S 应含 rx_errors 计数");
        assertTrue(outputs.get(1).stdout().contains("rx_packets"), "ethtool -S 应含 rx_packets 计数");

        assertEquals("cat /proc/interrupts", outputs.get(2).name());
        long interruptLines = outputs.get(2).stdout().lines().filter(line -> !line.isBlank()).count();
        assertTrue(interruptLines >= 2, "interrupts 至少保留表头与一行相关行，实际 " + interruptLines);
        assertTrue(interruptLines <= 20, "interrupts 最多 20 行，实际 " + interruptLines);
        assertTrue(outputs.get(2).stdout().contains(IFACE), "interrupts 应保留与接口相关的行");

        for (CommandOutput output : outputs) {
            assertEquals(0, output.exitCode(), "回放模式三条命令都应 exit 0");
        }
        System.out.printf("dut-replay outputs=%d ipHasAddr=true ethtoolHasRxErrors=true "
                        + "interruptLines=%d kind=%s%n",
                outputs.size(), interruptLines, outcome.kind());
    }

    @Test
    void 不可达分类为unreachable并给路由建议() {
        DutOutcome outcome = service(ReplaySshClient.FailureMode.UNREACHABLE)
                .collectOutcome(target(), IFACE);
        assertEquals(DutOutcome.KIND_UNREACHABLE, outcome.kind());
        assertEquals(DutOutcome.ADVICE_UNREACHABLE, outcome.advice());
        assertEquals(3, outcome.collection().outputs().size(), "失败时也要带回每条命令的原始输出");
        System.out.printf("dut-failure mode=unreachable kind=%s advice=%s%n",
                outcome.kind(), outcome.advice());
    }

    @Test
    void 认证失败分类为auth_failed并给凭据建议() {
        DutOutcome outcome = service(ReplaySshClient.FailureMode.AUTH_FAILED)
                .collectOutcome(target(), IFACE);
        assertEquals(DutOutcome.KIND_AUTH_FAILED, outcome.kind());
        assertEquals(DutOutcome.ADVICE_AUTH_FAILED, outcome.advice());
        System.out.printf("dut-failure mode=auth_failed kind=%s advice=%s%n",
                outcome.kind(), outcome.advice());
    }

    @Test
    void 缺工具分类为cmd_not_found并给安装建议() {
        DutOutcome outcome = service(ReplaySshClient.FailureMode.CMD_NOT_FOUND)
                .collectOutcome(target(), IFACE);
        assertEquals(DutOutcome.KIND_CMD_NOT_FOUND, outcome.kind());
        assertEquals(DutOutcome.ADVICE_CMD_NOT_FOUND, outcome.advice());
        System.out.printf("dut-failure mode=cmd_not_found kind=%s advice=%s%n",
                outcome.kind(), outcome.advice());
    }

    @Test
    void 其余非零退出码分类为command_failed并提示看stderr() {
        SshClient failing = (target, command, connectTimeout, commandTimeout) -> {
            Duration elapsed = Duration.ofMillis(1);
            if (command.startsWith("ip -br addr")) {
                return new SshClient.CommandResult(0, "eth0 UP 10.202.54.106/24", "", elapsed);
            }
            return new SshClient.CommandResult(1, "", "ethtool: ioctl error", elapsed);
        };
        DutOutcome outcome = new DutCollectorService(failing, props).collectOutcome(target(), IFACE);
        assertEquals(DutOutcome.KIND_COMMAND_FAILED, outcome.kind());
        assertEquals(DutOutcome.ADVICE_COMMAND_FAILED, outcome.advice());
        System.out.printf("dut-failure mode=command_failed kind=%s advice=%s%n",
                outcome.kind(), outcome.advice());
    }

    @Test
    void 非法接口名抛参数错误且不执行任何命令() {
        DutCollectorService service = service(ReplaySshClient.FailureMode.NONE);

        BusinessException withSemicolon = assertThrows(BusinessException.class,
                () -> service.collectOutcome(target(), "eth0;rm -rf /"));
        assertEquals(ErrorCode.INVALID_ARGUMENT, withSemicolon.errorCode());

        BusinessException blank = assertThrows(BusinessException.class,
                () -> service.collectOutcome(target(), " "));
        assertEquals(ErrorCode.INVALID_ARGUMENT, blank.errorCode());

        BusinessException nullIface = assertThrows(BusinessException.class,
                () -> service.collectOutcome(target(), null));
        assertEquals(ErrorCode.INVALID_ARGUMENT, nullIface.errorCode());
    }

    @Test
    void 目标标签与采集结论均不含口令() {
        DutTarget target = target();
        DutOutcome outcome = service(ReplaySshClient.FailureMode.NONE)
                .collectOutcome(target, IFACE);

        String rendered = target.toString() + "\n" + outcome.toString();
        int hits = rendered.contains(SECRET) ? 1 : 0;
        assertEquals(0, hits, "DutTarget/DutOutcome 的文本形态不得含口令");
        assertFalse(rendered.contains(SECRET), "口令不得出现在脱敏后的任何文本里");
        assertTrue(target.toString().contains("root@10.202.54.106"), "脱敏文本应保留 user@host");

        for (CommandOutput output : outcome.collection().outputs()) {
            assertFalse(output.stdout().contains(SECRET), "命令输出不得含口令");
            assertFalse(output.stderr().contains(SECRET), "命令诊断不得含口令");
        }
        System.out.printf("password-scan secretHits=%d targetLabel=%s%n",
                hits, outcome.collection().targetLabel());
    }
}
