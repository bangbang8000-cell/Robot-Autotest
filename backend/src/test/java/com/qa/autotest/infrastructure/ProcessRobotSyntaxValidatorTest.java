package com.qa.autotest.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.editor.RobotSyntaxValidator.SyntaxReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF 语法校验适配（EDT-09 / FR-v0.3-23）：只测输出解读与环境缺省，不依赖本机装 RF。 */
class ProcessRobotSyntaxValidatorTest {

    @Test
    @DisplayName("从 RF 输出里抠出行号与错误摘要")
    void 解析失败行号() {
        String output = """
                [ ERROR ] Error in file '/tmp/editor-syntax.robot' on line 5: Non-existing setting 'Foo'.
                Try --help for usage information.
                """;

        SyntaxReport report = ProcessRobotSyntaxValidator.parseFailure(output);

        assertTrue(report.checked());
        assertFalse(report.ok());
        assertEquals(5, report.line());
        assertTrue(report.message().contains("Non-existing setting"));
    }

    @Test
    @DisplayName("没有行号时行号为 0，但仍判失败")
    void 无行号也失败() {
        SyntaxReport report = ProcessRobotSyntaxValidator.parseFailure("[ ERROR ] Parsing failed");

        assertFalse(report.ok());
        assertEquals(0, report.line());
    }

    @Test
    @DisplayName("空输出判失败并给出兜底摘要")
    void 空输出() {
        SyntaxReport report = ProcessRobotSyntaxValidator.parseFailure("  ");

        assertFalse(report.ok());
        assertTrue(report.message().contains("无输出"));
    }

    @Test
    @DisplayName("空内容直接通过，不启动进程")
    void 空内容通过() {
        ProcessRobotSyntaxValidator validator =
                new ProcessRobotSyntaxValidator("python3 -m robot", 10);

        assertTrue(validator.check("   ").ok());
        assertTrue(validator.check(null).ok());
    }

    @Test
    @DisplayName("命令不存在时标记为未校验，不阻断保存")
    void 环境缺RF() {
        ProcessRobotSyntaxValidator validator =
                new ProcessRobotSyntaxValidator("no-such-rf-binary-xyz", 10);

        SyntaxReport report = validator.check("*** Test Cases ***\nT\n    Log    1");

        assertFalse(report.checked(), "没装 RF 时必须如实标注未校验");
        assertTrue(report.ok(), "未校验不得当成校验失败");
    }
}
