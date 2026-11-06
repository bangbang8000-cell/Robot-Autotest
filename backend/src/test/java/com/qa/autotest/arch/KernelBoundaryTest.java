package com.qa.autotest.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** KRN-01 SPI 边界：domain.kernel 必须是纯领域层，不反向依赖任何上层或框架。 */
class KernelBoundaryTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.qa.autotest");

    @Test
    void domainKernelDoesNotDependOnUpperLayersOrFrameworks() {
        // 用精确的本包前缀，不写 ..api.. / ..application.. 这类宽匹配：
        // 否则 org.junit.jupiter.api 会被 ..api.. 命中，把测试类也误判成违规。
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.kernel..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.qa.autotest.api..",
                        "com.qa.autotest.application..",
                        "com.qa.autotest.infrastructure..",
                        "org.springframework..", "com.fasterxml.jackson..")
                .because("KRN-01 的 SPI 必须能脱离容器与本项目上层单测");
        rule.check(CLASSES);
    }

}
