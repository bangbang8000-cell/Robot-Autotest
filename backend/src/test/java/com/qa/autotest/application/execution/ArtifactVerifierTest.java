package com.qa.autotest.application.execution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 产物四条件：退出码不可信，只有「存在 + size>1024 + 可解析 + totalTests>0」才算通过。 */
class ArtifactVerifierTest {

    private final ArtifactVerifier verifier = new ArtifactVerifier(new OutputXmlProbe());

    @TempDir
    Path tmp;

    private Path fixture(String name) throws Exception {
        return Path.of(getClass().getClassLoader()
                .getResource("fixtures/output-xml/" + name).toURI());
    }

    @Test
    void 文件不存在返回_missing() {
        ArtifactVerifier.VerifyResult result = verifier.verify(tmp.resolve("none.xml"));
        assertFalse(result.success());
        assertEquals("missing", result.reason());
        assertEquals(0, result.sizeBytes());
    }

    @Test
    void 小于等于_1024_字节返回_too_small() throws Exception {
        Path small = tmp.resolve("small.xml");
        Files.writeString(small, "<robot/>", StandardCharsets.UTF_8);
        ArtifactVerifier.VerifyResult result = verifier.verify(small);
        assertFalse(result.success());
        assertEquals("too_small", result.reason());
    }

    @Test
    void 非法XML返回_parse_failed() throws Exception {
        Path broken = tmp.resolve("broken.xml");
        // 超过 1024 字节且标签不闭合，确保命中「可解析」这一条而不是 too_small。
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><robot>");
        for (int i = 0; i < 100; i++) {
            sb.append("<!-- padding line ").append(i).append(" -->");
        }
        sb.append("<suite></robot>");
        Files.writeString(broken, sb.toString(), StandardCharsets.UTF_8);
        ArtifactVerifier.VerifyResult result = verifier.verify(broken);
        assertFalse(result.success());
        assertEquals("parse_failed", result.reason());
    }

    @Test
    void 没有_test_节点返回_no_tests_executed() throws Exception {
        ArtifactVerifier.VerifyResult result = verifier.verify(fixture("empty.xml"));
        assertFalse(result.success());
        assertEquals("no_tests_executed", result.reason());
        assertTrue(result.sizeBytes() > 1024);
    }

    @Test
    void 正常产物返回_ok_且统计测试数() throws Exception {
        ArtifactVerifier.VerifyResult result = verifier.verify(fixture("ok.xml"));
        assertTrue(result.success());
        assertEquals("ok", result.reason());
        assertEquals(2, result.totalTests());
        assertTrue(result.sizeBytes() > 1024);
    }
}
