package com.qa.autotest.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 分层依赖规则（基线报告 §12.2 R1~R5）。规则本身放在这里，供两个测试共用：
 * <ul>
 *   <li>{@link LayerRulesTest} —— 对真实代码跑，必须绿；</li>
 *   <li>{@link LayerRulesMetaTest} —— 对故意违规的 fixture 跑，必须红。</li>
 * </ul>
 *
 * <p>只有「正常代码能过」不算验证过分层规则，两半都成立才算（TEST-PLAN TC-v0.1-010~014）。
 */
public final class LayerRules {

    private LayerRules() {
    }

    /** R1：domain 不得依赖 api / application / infrastructure。 */
    public static final ArchRule R1_DOMAIN_NOT_DEPEND_ON_UPPER_LAYERS =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..api..", "..application..", "..infrastructure..")
                    .because("domain 是纯领域模型，上层变化不得反向污染它（R1）");

    /** R2：domain 不得依赖框架（Spring / JPA / Bean Validation / Jackson）。 */
    public static final ArchRule R2_DOMAIN_NOT_DEPEND_ON_FRAMEWORKS =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.validation..",
                            "com.fasterxml.jackson..")
                    .because("domain 必须能脱离容器与数据库单测（R2）");

    /** R3：api 不得直接依赖 infrastructure。 */
    public static final ArchRule R3_API_NOT_DEPEND_ON_INFRASTRUCTURE =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because("接口层只能经 application 用例访问数据，不得绕过（R3）");

    /** R4：application 不得反向依赖 api。 */
    public static final ArchRule R4_APPLICATION_NOT_DEPEND_ON_API =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..api..")
                    .because("用例编排不知道 HTTP 的存在（R4）");

    /** R5：common 是叶子层，不得依赖任何业务层。 */
    public static final ArchRule R5_COMMON_IS_LEAF =
            noClasses()
                    .that().resideInAPackage("..common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..api..", "..application..", "..domain..", "..infrastructure..")
                    .because("公共层被所有层依赖，反向依赖会成环（R5）");

    /** 顶层包切片不得成环。 */
    public static final ArchRule NO_PACKAGE_CYCLES =
            slices()
                    .matching("com.qa.autotest.(*)..")
                    .should().beFreeOfCycles()
                    .because("分层之间只允许单向依赖");

    /** 逐条对给定类集检查，任一条不过即抛 AssertionError（元测试复用）。 */
    public static void checkAll(JavaClasses classes) {
        R1_DOMAIN_NOT_DEPEND_ON_UPPER_LAYERS.check(classes);
        R2_DOMAIN_NOT_DEPEND_ON_FRAMEWORKS.check(classes);
        R3_API_NOT_DEPEND_ON_INFRASTRUCTURE.check(classes);
        R4_APPLICATION_NOT_DEPEND_ON_API.check(classes);
        R5_COMMON_IS_LEAF.check(classes);
        NO_PACKAGE_CYCLES.check(classes);
    }
}
