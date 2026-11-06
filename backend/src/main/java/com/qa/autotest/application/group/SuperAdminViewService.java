package com.qa.autotest.application.group;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 超管全局视图 / 跨项目统计（IAMX-03）。非管理员一律 403。 */
@Service
public class SuperAdminViewService {

    private final JdbcTemplate jdbc;
    private final AccessControlService access;

    public SuperAdminViewService(JdbcTemplate jdbc, AccessControlService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public Map<String, Object> overview() {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅超管可查看全局视图");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projects", count("SELECT COUNT(*) FROM project"));
        result.put("groups", count("SELECT COUNT(*) FROM user_group"));
        result.put("scenarios", count("SELECT COUNT(*) FROM test_scenario"));
        result.put("testReports", count("SELECT COUNT(*) FROM test_report"));
        result.put("importBatches", count("SELECT COUNT(*) FROM import_batch"));
        result.put("visibleProjects", access.visibleProjects().size());
        return result;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
