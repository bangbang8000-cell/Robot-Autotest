package com.qa.autotest.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 趋势 SQL 的状态口径守卫（TC-OVW-07）。
 *
 * <p><b>为什么需要这条「读常量」的测试</b>：V4_1 之后 {@code run.run_status} 的取值只有
 * {@code queued/preparing/running/finished/failed/canceled}，但 {@code TrendRepositoryJdbc}
 * 的通过率 SQL 一直写的是 {@code IN ('passed','failed')}。SQL 语法合法、接口有响应、数字看着正常，
 * 但 {@code finished}（成功完成）的 run 全被漏掉——这类「验证在说谎」的缺陷没有任何报错，
 * 只能靠断言字面量本身来守。
 */
class TrendRepositorySqlTest {

    @Test
    @DisplayName("通过率 SQL 用 finished/failed，不得出现枚举里不存在的 passed")
    void 通过率口径与状态枚举一致() {
        assertThat(TrendRepositoryJdbc.PASS_RATE_DAY)
                .contains("run_status IN ('finished', 'failed')")
                .doesNotContain("'passed'");
        assertThat(TrendRepositoryJdbc.PASS_RATE_WEEK)
                .contains("run_status IN ('finished', 'failed')")
                .doesNotContain("'passed'");
    }
}
