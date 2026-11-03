package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.application.repository.RunArtifactRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.application.repository.TestResultWriter;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W2 最小闭环：create → precheck → 建目录 → RUNNING → pabot → 校验 → 终态。 */
class RunExecutionServiceTest {

    private static final long PROJECT = 7L;

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-03T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", PROJECT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void 成功闭环进入_FINISHED_并登记产物() throws Exception {
        Fixture fixture = new Fixture("ok.xml", true);
        RunRecord run = fixture.service.execute(command());

        assertEquals(RunState.FINISHED, run.state());
        assertEquals(2, run.totalTests());
        assertEquals(2, run.passedTests());
        assertTrue(fixture.artifactKinds.contains("output_xml"));
        assertTrue(fixture.artifactKinds.contains("stdout_log"));
        assertTrue(fixture.artifactKinds.contains("stderr_log"));
        assertTrue(fixture.resultVerdicts.contains("PASS"));
    }

    @Test
    void 空产物进入_FAILED_并标记_no_tests_executed() throws Exception {
        Fixture fixture = new Fixture("empty.xml", true);
        RunRecord run = fixture.service.execute(command());

        assertEquals(RunState.FAILED, run.state());
        assertEquals("no_tests_executed", run.failureReason());
        assertEquals("INFRA", run.failureClass());
        assertTrue(fixture.resultVerdicts.contains("FAIL"));
    }

    @Test
    void 预检失败在_RUNNING_之前终止() throws Exception {
        Fixture fixture = new Fixture("ok.xml", false);
        RunRecord run = fixture.service.execute(command());

        assertEquals(RunState.FAILED, run.state());
        assertEquals("precheck_pabot_not_found", run.failureReason());
        assertTrue(fixture.artifactKinds.isEmpty());
        assertTrue(fixture.launcher.launchCount == 0);
    }

    private RunService.CreateRunCommand command() {
        return new RunService.CreateRunCommand(PROJECT, null,
                List.of("suites/a.robot", "suites/b.robot"), 2, null, List.of(),
                "req-1", 5, 600, null);
    }

    private final class Fixture {

        private final FakeRunRepository repo = new FakeRunRepository();
        private final FakeArtifacts artifacts = new FakeArtifacts();
        private final FakeResults results = new FakeResults();
        private final FakeLauncher launcher;
        private final List<String> artifactKinds = artifacts.kinds;
        private final List<String> resultVerdicts = results.verdicts;
        private final RunExecutionService service;

        private Fixture(String fixtureName, boolean pabotExists) throws Exception {
            this.launcher = new FakeLauncher(Path.of(getClass().getClassLoader()
                    .getResource("fixtures/output-xml/" + fixtureName).toURI()));
            ExecutionProperties props = new ExecutionProperties();
            props.setVenvBin(tmp.resolve("venv").resolve("bin").toString());
            props.setWorkspaceRoot(tmp.resolve("ws").toString());
            props.setArtifactsRoot(tmp.resolve("artifacts").toString());

            FakeFs fs = new FakeFs();
            fs.exists = pabotExists;

            RunService runs = new RunService(repo,
                    new AccessControlService(adminUsers(), emptyProjects()),
                    new AuditService(noopAudit()), new RunIdGenerator(), props, CLOCK);
            ExecutionPrecheck precheck = new ExecutionPrecheck(fs, path -> Optional.of("5.2.2"));
            PabotOrchestrator orchestrator = new PabotOrchestrator(launcher,
                    new ArtifactVerifier(new OutputXmlProbe()),
                    new ProcessRegistry(), new RunLogService(CLOCK), CLOCK);
            this.service = new RunExecutionService(runs, props, precheck,
                    new PabotCommandBuilder(), orchestrator, artifacts, results,
                    new FailureClassifier(FailureRuleSet.defaults()), new RetryPolicy(), CLOCK);
        }
    }

    /** 复制真实产物形态的样本到 pabot 的 outputdir，模拟一次进程执行。 */
    private final class FakeLauncher implements ProcessLauncher {

        private final Path sourceXml;
        private int launchCount;

        private FakeLauncher(Path sourceXml) {
            this.sourceXml = sourceXml;
        }

        @Override
        public ExecutionHandle launch(PabotCommandBuilder.PabotCommand command) {
            launchCount++;
            try {
                Files.createDirectories(command.outputDir());
                Files.copy(sourceXml, command.outputDir().resolve("output.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return new FakeHandle();
        }
    }

    private static final class FakeHandle implements ProcessLauncher.ExecutionHandle {

        @Override
        public long pid() {
            return 4242L;
        }

        @Override
        public int waitFor(Duration timeout) {
            return 0;
        }

        @Override
        public InputStream stdout() {
            return new ByteArrayInputStream("pabot line\n".getBytes());
        }

        @Override
        public InputStream stderr() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Process process() {
            return null;
        }
    }

    private static final class FakeFs implements FileSystemProbe {

        private boolean exists = true;

        @Override
        public boolean exists(Path path) {
            return exists;
        }

        @Override
        public boolean executable(Path path) {
            return true;
        }

        @Override
        public boolean writable(Path path) {
            return true;
        }

        @Override
        public long usableSpace(Path path) {
            return Long.MAX_VALUE;
        }
    }

    private static final class FakeRunRepository implements RunRepository {

        private final Map<String, RunRecord> byRunId = new LinkedHashMap<>();
        private long seq;

        @Override
        public RunRecord insert(RunRecord draft, String jobNo, String runRequestId, Long operatorId) {
            long id = ++seq;
            RunRecord stored = new RunRecord(id, draft.runId(), draft.projectId(),
                    draft.testSetId(), draft.state(), draft.priority(), draft.concurrency(),
                    draft.attemptNo(), 0, 0, 0, null, null, null, null,
                    draft.enqueuedAt(), null, null);
            byRunId.put(stored.runId(), stored);
            return stored;
        }

        @Override
        public Optional<RunRecord> findByRunId(long projectId, String runId) {
            RunRecord stored = byRunId.get(runId);
            return stored != null && stored.projectId() == projectId
                    ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public Optional<RunRecord> findByRequestId(long projectId, String requestId) {
            return byRunId.values().stream()
                    .filter(r -> r.projectId() == projectId).findFirst();
        }

        @Override
        public int updateState(String runId, RunState from, RunState to,
                               Instant startedAt, Instant finishedAt,
                               String failureClass, String failureReason, String failureMessage) {
            RunRecord current = byRunId.get(runId);
            if (current == null || current.state() != from) {
                return 0;
            }
            byRunId.put(runId, new RunRecord(current.id(), current.runId(), current.projectId(),
                    current.testSetId(), to, current.priority(), current.concurrency(),
                    current.attemptNo(), current.totalTests(), current.passedTests(),
                    current.failedTests(), failureClass, failureReason, failureMessage,
                    current.artifactsDir(), current.enqueuedAt(),
                    startedAt == null ? current.startedAt() : startedAt,
                    finishedAt == null ? current.finishedAt() : finishedAt));
            return 1;
        }

        @Override
        public int updateCounts(String runId, int total, int passed, int failed, String artifactsDir) {
            RunRecord current = byRunId.get(runId);
            if (current == null) {
                return 0;
            }
            byRunId.put(runId, new RunRecord(current.id(), current.runId(), current.projectId(),
                    current.testSetId(), current.state(), current.priority(), current.concurrency(),
                    current.attemptNo(), total, passed, failed, current.failureClass(),
                    current.failureReason(), current.failureMessage(), artifactsDir,
                    current.enqueuedAt(), current.startedAt(), current.finishedAt()));
            return 1;
        }

        @Override
        public List<RunRecord> findActive() {
            return byRunId.values().stream()
                    .filter(r -> r.state().active()).toList();
        }

        @Override
        public PageResult<RunRecord> page(long projectId, RunState state, int page, int size) {
            List<RunRecord> items = byRunId.values().stream()
                    .filter(r -> r.projectId() == projectId)
                    .filter(r -> state == null || r.state() == state).toList();
            return new PageResult<>(items, items.size(), page, size);
        }

        @Override
        public int markAttempt(String runId, int attemptNo, String retryOf) {
            return 0;
        }
    }

    private static final class FakeArtifacts implements RunArtifactRepository {

        private final List<String> kinds = new ArrayList<>();

        @Override
        public void upsert(long projectId, long runDbId, String kind, String path,
                           String sha256, long sizeBytes, int shardNo, String note) {
            kinds.add(kind);
        }

        @Override
        public List<RunArtifact> list(long projectId, long runDbId) {
            return List.of();
        }
    }

    private static final class FakeResults implements TestResultWriter {

        private final List<String> verdicts = new ArrayList<>();

        @Override
        public void write(long projectId, long runDbId, String caseKey, String verdict,
                          long durationMs, String failureMsg, int roundNo) {
            verdicts.add(verdict);
        }
    }

    private static UserRepository adminUsers() {
        return proxy(UserRepository.class, (proxy, method, args) ->
                "roleCodesOf".equals(method.getName()) ? List.of("admin") : defaultValue(method.getReturnType()));
    }

    private static ProjectRepository emptyProjects() {
        return proxy(ProjectRepository.class, (proxy, method, args) ->
                defaultValue(method.getReturnType()));
    }

    private static AuditRepository noopAudit() {
        return proxy(AuditRepository.class, (proxy, method, args) ->
                defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        return null;
    }
}
