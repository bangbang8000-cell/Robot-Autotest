import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.kernel.OutputXmlNormalizer;
import com.qa.autotest.application.kernel.RobotKernel;
import com.qa.autotest.domain.kernel.CaseStatus;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedCaseResult;
import com.qa.autotest.domain.kernel.NormalizedRun;

import java.nio.file.Path;
import java.util.List;

/**
 * A3 证据辅助：直接调用生产代码里的 RobotKernel.command / OutputXmlNormalizer，
 * 把「主控参照命令」与「真实 output.xml 解析结果」落盘。
 *
 * build <artifactsRoot> <runId> <displayName> <processes> <suitePath...>
 * parse <output.xml> <runId>
 */
public final class CmdDump {

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "build".equals(args[0])) {
            build(args);
        } else if (args.length >= 3 && "parse".equals(args[0])) {
            parse(args);
        } else {
            System.err.println("usage: CmdDump build <artifactsRoot> <runId> <displayName> <processes> <suitePath...>");
            System.err.println("       CmdDump parse <output.xml> <runId>");
            System.exit(2);
        }
    }

    private static void build(String[] args) {
        Path artifacts = Path.of(args[1]);
        String runId = args[2];
        String display = args[3];
        int processes = Integer.parseInt(args[4]);
        List<String> suites = List.of(args).subList(5, args.length);
        ExecutionProperties props = new ExecutionProperties();
        RobotKernel kernel = new RobotKernel(props, new OutputXmlNormalizer());
        KernelRequest request = new KernelRequest(1L, runId, 1, processes, suites, List.of(), "", display);
        List<String> argv = kernel.command(request, artifacts);
        StringBuilder json = new StringBuilder();
        json.append("{\"runId\":").append(quote(runId)).append(",\"argv\":[");
        for (int i = 0; i < argv.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(argv.get(i)));
        }
        json.append("]}");
        System.out.println(json);
    }

    private static void parse(String[] args) {
        NormalizedRun run = new OutputXmlNormalizer().fromOutputXml(Path.of(args[1]), args[2]);
        System.out.println("kernel=" + run.kernel());
        System.out.println("total=" + run.total());
        System.out.println("failures=" + run.failures());
        for (NormalizedCaseResult c : run.cases()) {
            System.out.println("  " + c.status() + "\t" + c.name() + "\t" + c.message());
        }
        if (run.cases().isEmpty()) {
            System.exit(3);
        }
        long passed = run.cases().stream().filter(c -> c.status() == CaseStatus.PASS).count();
        System.out.println("passed=" + passed);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
