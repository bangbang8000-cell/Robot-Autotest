import com.qa.autotest.application.execution.ProcessTreeKiller;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A7 pytest 进程树探针：用产品同一个 ProcessTreeKiller 终止真实 pytest 进程树。
 * 用法：KillProbe <deadlineSec> <outDir> <argv...>
 */
public final class KillProbe {

    public static void main(String[] args) throws Exception {
        int deadlineSec = Integer.parseInt(args[0]);
        String outDir = args[1];
        List<String> cmd = Arrays.asList(args).subList(2, args.length);
        // 产品 argv 首项是裸名 pytest；Java ProcessBuilder 不按改造后的 PATH 解析裸名，
        // 故经 bash exec 承接（exec 替换自身，pid 即 pytest 本身），保持 argv 与产品一致。
        List<String> wrapped = new ArrayList<>(List.of("bash", "-c", "exec \"$@\"", "a7-probe"));
        wrapped.addAll(cmd);

        ProcessBuilder pb = new ProcessBuilder(wrapped).redirectErrorStream(true);
        File log = new File(outDir, "probe-process.log");
        pb.redirectOutput(log);
        String home = System.getProperty("user.home");
        pb.environment().put("PATH",
                home + "/venvs/pytest-kernel/bin:" + home + "/rf-stack/.venv/bin:"
                        + pb.environment().getOrDefault("PATH", "/usr/bin:/bin"));
        Process p = pb.start();
        long t0 = System.nanoTime();
        Thread.sleep(deadlineSec * 1000L);

        List<ProcessHandle> descendants = p.descendants().toList();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"cmd\": ").append(jsonList(cmd)).append(",\n");
        sb.append("  \"pid\": ").append(p.pid()).append(",\n");
        sb.append("  \"deadline_sec\": ").append(deadlineSec).append(",\n");
        sb.append("  \"descendants_before_kill\": [");
        boolean first = true;
        for (ProcessHandle h : descendants) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("{\"pid\": ").append(h.pid())
                    .append(", \"cmd\": ").append(jsonStr(h.info().commandLine().orElse("")))
                    .append("}");
        }
        sb.append("],\n");

        ProcessTreeKiller.KillOutcome outcome =
                new ProcessTreeKiller().kill(p, Duration.ofSeconds(5));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        Thread.sleep(2000L);

        List<Long> residual = new ArrayList<>();
        for (ProcessHandle h : descendants) {
            if (h.isAlive()) {
                residual.add(h.pid());
            }
        }
        sb.append("  \"kill\": {\"terminated\": ").append(outcome.terminated())
                .append(", \"forced\": ").append(outcome.forced())
                .append(", \"waited_ms\": ").append(outcome.waitedMs()).append("},\n");
        sb.append("  \"parent_alive_after_2s\": ").append(p.isAlive()).append(",\n");
        sb.append("  \"residual_descendants\": ").append(residual).append(",\n");
        sb.append("  \"elapsed_ms\": ").append(elapsedMs).append("\n");
        sb.append("}");
        System.out.println(sb);
    }

    private static String jsonList(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(jsonStr(values.get(i)));
        }
        return sb.append("]").toString();
    }

    private static String jsonStr(String v) {
        return "\"" + (v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }
}
