package com.qa.autotest.application.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.domain.kernel.CaseStatus;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PytestKernelTest {

    private final PytestKernel kernel = new PytestKernel(new ObjectMapper());

    @Test
    void jsonReportMapsAllSixStatuses(@TempDir Path dir) throws Exception {
        String json = """
                {"tests":[
                  {"nodeid":"t.py::test_p[1]","outcome":"passed","duration":0.1},
                  {"nodeid":"t.py::test_p[2]","outcome":"passed","duration":0.2},
                  {"nodeid":"t.py::test_f","outcome":"failed","duration":0.3},
                  {"nodeid":"t.py::test_s","outcome":"skipped"},
                  {"nodeid":"t.py::test_x","outcome":"xfailed"},
                  {"nodeid":"t.py::test_xp","outcome":"xpassed"},
                  {"nodeid":"t.py::test_e","outcome":"error"}
                ]}""";
        Files.writeString(dir.resolve("pytest-report.json"), json);
        NormalizedRun run = kernel.normalize(dir, "R1");
        assertEquals(7, run.total());
        assertEquals(2, run.counts().get("PASS"));
        assertEquals(1, run.counts().get("XFAIL"));
        assertEquals(1, run.counts().get("ERROR"));
        assertEquals(3, run.failures());
        assertTrue(run.cases().get(0).nodeId().contains("[1]"));
    }

    @Test
    void junitFallbackDistinguishesFailureErrorAndSkipped(@TempDir Path dir) throws Exception {
        String xml = """
                <testsuite name="s" tests="4">
                  <testcase classname="t" name="ok" time="0.1"/>
                  <testcase classname="t" name="bad" time="0.2"><failure message="boom"/></testcase>
                  <testcase classname="t" name="err"><error message="setup"/></testcase>
                  <testcase classname="t" name="skip"><skipped/></testcase>
                </testsuite>""";
        Files.writeString(dir.resolve("junit.xml"), xml);
        NormalizedRun run = kernel.normalize(dir, "R2");
        assertEquals(4, run.total());
        assertEquals(CaseStatus.PASS, run.cases().get(0).status());
        assertEquals(CaseStatus.FAIL, run.cases().get(1).status());
        assertEquals(CaseStatus.ERROR, run.cases().get(2).status());
        assertEquals(CaseStatus.SKIP, run.cases().get(3).status());
    }

    @Test
    void missingArtifactsRejected(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> kernel.normalize(dir, "R3"));
    }

    @Test
    void commandUsesBothReportChannels() {
        KernelRequest request = new KernelRequest(1L, "R4", 2, 4,
                List.of("tests/"), List.of("tests/test_a.py::test_ok"), null, "preview");
        List<String> argv = kernel.command(request, Path.of("artifacts"));
        assertEquals("pytest", argv.get(0));
        assertTrue(argv.stream().anyMatch(a -> a.startsWith("--json-report-file=")));
        assertTrue(argv.stream().anyMatch(a -> a.startsWith("--junitxml=")));
        assertTrue(argv.contains("tests/test_a.py::test_ok"));
    }
}
