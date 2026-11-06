package com.qa.autotest.application.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.common.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiPanelTest {

    private record Fixture(AiPanelService service, NoopAuditRepository audit) {
    }

    private static Fixture fixture(boolean cliAvailable) {
        AiCliProbe probe = new AiCliProbe() {
            @Override public boolean available() { return cliAvailable; }
            @Override public Map<String, Object> probe() {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("anyAvailable", cliAvailable);
                m.put("mode", cliAvailable ? "NORMAL" : "DEGRADED");
                m.put("maxLevel", cliAvailable ? "L3" : "L1");
                return m;
            }
        };
        NoopAuditRepository audit = new NoopAuditRepository();
        AiPanelService service = new AiPanelService(probe, new ContextPacker(),
                new AiProcessManager(), new AuditService(audit, Runnable::run));
        return new Fixture(service, audit);
    }

    private static AiPanelService panel(boolean cliAvailable) {
        return fixture(cliAvailable).service();
    }

    private static Map<String, String> context() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("runId", "RUN-1");
        m.put("dut", "10.202.54.106");
        m.put("command", "ssh -o root@10.202.54.106 password=abc");
        m.put("log", "x".repeat(500));
        return m;
    }

    @Test
    void contextIsScrubbedAndTruncated() {
        ContextPacker packer = new ContextPacker();
        String packed = packer.pack(context(), 200);
        assertFalse(packed.contains("10.202.54.106"));
        assertFalse(packed.contains("password=abc"));
        assertTrue(packed.length() <= 200);
        assertTrue(packed.contains("[TRUNCATED]"));
        assertFalse(packer.containsSensitive(packed));
    }

    @Test
    void noCliDegradesWithoutBlocking() {
        AiPanelService service = panel(false);
        var state = service.state(1L);
        assertEquals("DEGRADED", state.get("mode"));
        assertEquals(true, state.get("executeRequiresConfirmation"));
        var suggest = service.suggest(1L, context());
        assertTrue(suggest.degraded());
        assertEquals("L1", suggest.level());
        assertTrue(suggest.audited());
    }

    @Test
    void executeRequiresSecondConfirmation() {
        AiPanelService service = panel(true);
        long session = service.open(1L, 2L);
        assertThrows(BusinessException.class,
                () -> service.execute(session, "run-1", false));
        var ok = service.execute(session, "run-1", true);
        assertFalse(ok.degraded());
        assertEquals("L3", ok.level());
    }

    @Test
    void terminateWorksEvenWithoutCli() {
        AiPanelService service = panel(false);
        long session = service.open(1L, 2L);
        var result = service.terminate(session, null);
        assertEquals("TERMINATE", result.action());
        assertThrows(BusinessException.class, () -> service.execute(session, "x", true));
    }

    @Test
    void everyActionWritesAudit() {
        Fixture f = fixture(false);
        long session = f.service().open(1L, 2L);
        f.service().suggest(session, context());
        f.service().evaluate(session, 1, 0);
        f.service().terminate(session, null);
        assertTrue(f.audit().actions().containsAll(List.of(
                "AI_SESSION_OPEN", "AI_SUGGEST", "AI_EVALUATE", "AI_TERMINATE")));
    }

    @Test
    void processManagerBuildsNonInteractiveCommand() {
        AiProcessManager manager = new AiProcessManager();
        var argv = manager.buildCommand("claude", "/tmp/prompt.txt");
        assertEquals("claude", argv.get(0));
        assertTrue(argv.contains("--print"));
        assertTrue(argv.contains("/tmp/prompt.txt"));
        assertEquals("UTF-8", manager.encoding());
        assertTrue(manager.timeoutSeconds() > 0);
    }
}
