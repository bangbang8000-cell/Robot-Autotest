package com.qa.autotest.application.group;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.common.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ScenarioServiceTest {

    /** 不连库的纯校验分支：非法入参必须在写库之前被拒。 */
    private static ScenarioService withoutDb() {
        return new ScenarioService(null);
    }

    @Test
    void blankNameRejectedBeforeAnyWrite() {
        assertThrows(BusinessException.class,
                () -> withoutDb().createScenario(1L, "  ", List.of(1L)));
    }

    @Test
    void tooManyOrIllegalCaseSetsRejected() {
        List<Long> many = LongStream.rangeClosed(1, 51).boxed().toList();
        assertThrows(BusinessException.class,
                () -> withoutDb().createScenario(1L, "S", many));
        assertThrows(BusinessException.class,
                () -> withoutDb().createScenario(1L, "S", Arrays.asList(1L, null)));
    }

    /** D5「一层编排、不做 DAG」的结构守门：源码里不存在设置父场景的入口。 */
    @Test
    void oneLevelOrchestrationHasNoNestingEntry() throws Exception {
        Path source = Path.of("src/main/java/com/qa/autotest/application/group/ScenarioService.java");
        String text = Files.readString(source);
        assertTrue(text.contains("VALUES (?, ?, ?, NULL)"),
                "createScenario 必须把 parent_scenario_id 固定为 NULL");
        assertFalse(text.contains("parent_scenario_id = ?"),
                "不允许出现按参数写父场景的语句");
    }
}
