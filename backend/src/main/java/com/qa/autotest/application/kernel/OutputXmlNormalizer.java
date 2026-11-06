package com.qa.autotest.application.kernel;

import com.qa.autotest.domain.kernel.CaseStatus;
import com.qa.autotest.domain.kernel.NormalizedCaseResult;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** RF output.xml → NormalizedRun（KRN-01）。只读 <test> 的 status，不重写 RF 判定。 */
@Component
public class OutputXmlNormalizer {

    public NormalizedRun fromOutputXml(Path output, String runId) {
        try (InputStream in = Files.newInputStream(output)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(in);
            NodeList tests = doc.getElementsByTagName("test");
            List<NormalizedCaseResult> cases = new ArrayList<>();
            for (int i = 0; i < tests.getLength(); i++) {
                Element test = (Element) tests.item(i);
                String name = test.getAttribute("name");
                NodeList statuses = test.getElementsByTagName("status");
                String status = statuses.getLength() == 0
                        ? "FAIL" : ((Element) statuses.item(0)).getAttribute("status");
                cases.add(new NormalizedCaseResult(name, name,
                        "PASS".equalsIgnoreCase(status) ? CaseStatus.PASS : CaseStatus.FAIL,
                        0.0, status));
            }
            return new NormalizedRun(RobotKernel.NAME, runId, cases);
        } catch (Exception e) {
            throw new IllegalStateException("output.xml 解析失败: " + output, e);
        }
    }
}
