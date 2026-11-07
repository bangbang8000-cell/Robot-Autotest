package com.qa.autotest.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** G-B8-1：入库的 case_key 必须跨 run 稳定，根套件 run-&lt;RUN_ID&gt; 不得进入路径。 */
class ReportRepositoryCaseKeyTest {

    private static final String RUN_ID = "RUN-20261107151146-abcdef";

    @Test
    @DisplayName("根套件 run-<RUN_ID> 被剥离，路径从空开始")
    void 剥离根套件() {
        assertEquals("", ReportRepositoryJdbc.rootBasePath("run-" + RUN_ID, RUN_ID));
    }

    @Test
    @DisplayName("普通根套件名原样保留")
    void 保留普通根套件() {
        assertEquals("B8 用例集", ReportRepositoryJdbc.rootBasePath("B8 用例集", RUN_ID));
    }

    @Test
    @DisplayName("runId 为空时不做误剥离")
    void runId为空时保留() {
        assertEquals("run-" + RUN_ID, ReportRepositoryJdbc.rootBasePath("run-" + RUN_ID, ""));
    }

    @Test
    @DisplayName("根套件名为空时返回空路径")
    void 根套件名为空() {
        assertEquals("", ReportRepositoryJdbc.rootBasePath(null, RUN_ID));
    }
}
