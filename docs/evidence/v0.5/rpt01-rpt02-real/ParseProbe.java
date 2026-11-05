import com.qa.autotest.application.report.OutputXmlParser;
import com.qa.autotest.domain.report.CaseNode;
import com.qa.autotest.domain.report.KeywordNode;
import com.qa.autotest.domain.report.ParsedReport;
import com.qa.autotest.domain.report.SuiteNode;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性探针：用真实 OutputXmlParser 解析给定 output.xml，打印结构与耗时。
 * 只用于证据采集，不入库。
 */
public final class ParseProbe {

    private static int suites;
    private static int cases;
    private static int keywords;
    private static int messages;

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            Path file = Path.of(arg);
            long size = Files.size(file);
            long lines = Files.readAllLines(file).size();
            suites = 0;
            cases = 0;
            keywords = 0;
            messages = 0;
            long t0 = System.nanoTime();
            ParsedReport parsed = new OutputXmlParser().parse(file);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            walk(parsed.root());
            long heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            System.out.println("[probe] file=" + file);
            System.out.println("[probe] bytes=" + size + " lines=" + lines);
            System.out.println("[probe] suites=" + suites + " cases=" + cases
                    + " keywords=" + keywords + " messages=" + messages);
            System.out.println("[probe] pass=" + parsed.passCount() + " fail=" + parsed.failCount()
                    + " skip=" + parsed.skipCount() + " total=" + parsed.totalCount());
            System.out.println("[probe] metadata=" + parsed.metadata());
            System.out.println("[probe] elapsed=" + ms + "ms heapUsed=" + heapMb + "MB");
            System.out.println();
        }
    }

    private static void walk(SuiteNode suite) {
        suites++;
        cases += suite.cases().size();
        for (CaseNode c : suite.cases()) {
            for (KeywordNode k : c.keywords()) {
                walkKeyword(k);
            }
        }
        for (SuiteNode child : suite.suites()) {
            walk(child);
        }
    }

    private static void walkKeyword(KeywordNode keyword) {
        keywords++;
        messages += keyword.messages().size();
        for (KeywordNode child : keyword.children()) {
            walkKeyword(child);
        }
    }
}
