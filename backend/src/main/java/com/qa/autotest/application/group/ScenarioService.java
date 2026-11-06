package com.qa.autotest.application.group;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 项目模板 / 归档 + 一层测试场景编排（IAMX-04，D5：不做 DAG）。 */
@Service
public class ScenarioService {

    private final JdbcTemplate jdbc;

    public ScenarioService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一层编排：场景只能直接挂用例集，不允许嵌套场景，也不接受循环引用。 */
    public long createScenario(long projectId, String name, List<Long> caseSetIds) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "场景名不能为空");
        }
        if (caseSetIds == null || caseSetIds.size() > 50) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用例集数量须在 1~50");
        }
        for (Long id : caseSetIds) {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "非法用例集 id");
            }
        }
        String joined = String.join(",", caseSetIds.stream().map(String::valueOf).toList());
        jdbc.update("INSERT INTO test_scenario "
                        + "(project_id, name, case_set_ids, parent_scenario_id) "
                        + "VALUES (?, ?, ?, NULL)",
                projectId, name, joined);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    public List<Map<String, Object>> scenarios(long projectId) {
        return jdbc.queryForList("SELECT id, name, case_set_ids, parent_scenario_id "
                + "FROM test_scenario WHERE project_id = ? ORDER BY id", projectId);
    }

    /** DAG 检测：本版只允许一层，任何 parent_scenario_id 非空都视为违规数据。 */
    public List<String> assertOneLevel(long projectId) {
        List<Map<String, Object>> rows = scenarios(projectId);
        List<String> violations = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.get("parent_scenario_id") != null) {
                violations.add("场景 " + row.get("id") + " 存在嵌套父场景");
            }
        }
        return violations;
    }

    public long createTemplate(String name, String configJson) {
        jdbc.update("INSERT INTO project_template (name, config_json) VALUES (?, ?)",
                name, configJson);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    public int archiveTemplate(long templateId) {
        return jdbc.update("UPDATE project_template SET archived = 1 WHERE id = ?", templateId);
    }

    public Map<String, Object> templates() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_template WHERE archived = 0", Integer.class));
        result.put("archived", jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_template WHERE archived = 1", Integer.class));
        return result;
    }
}
