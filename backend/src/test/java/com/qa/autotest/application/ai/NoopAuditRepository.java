package com.qa.autotest.application.ai;

import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.domain.iam.AuditEntry;
import java.util.ArrayList;
import java.util.List;

/** AI 单测用的空审计仓储：只满足接口，不落库；动作名留内存供断言。 */
public class NoopAuditRepository implements AuditRepository {

    private final List<String> actions = new ArrayList<>();

    @Override
    public void insert(Long projectId, Long actorId, String action, String resourceType,
                       String resourceId, String detail) {
        actions.add(action);
    }

    @Override
    public List<String> recentActions(Long projectId, int limit) {
        return List.copyOf(actions);
    }

    @Override
    public void insertEntry(AuditEntry entry) {
        actions.add(entry.action());
    }

    public List<String> actions() {
        return List.copyOf(actions);
    }
}
