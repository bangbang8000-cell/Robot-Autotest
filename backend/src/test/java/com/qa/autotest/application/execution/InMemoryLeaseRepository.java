package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.ResourceLeaseRepository;
import com.qa.autotest.domain.execution.Lease;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 测试用内存租约仓储。
 *
 * <p>唯一键互斥用 {@link ConcurrentHashMap#putIfAbsent} 复刻数据库 {@code uk_resource_lease_key}
 * 的语义：并发下只有一个线程能把 key 放进去。真机 MariaDB 的验证见
 * {@code docs/evidence/v0.4/lease-conflict-0.sql}（首行标注待 149 执行）。
 */
public final class InMemoryLeaseRepository implements ResourceLeaseRepository {

    private final Map<String, Lease> held = new ConcurrentHashMap<>();
    private final List<HistoryRow> history = new CopyOnWriteArrayList<>();

    public record HistoryRow(String resourceKey, long jobId, String state, String reason) {
    }

    @Override
    public boolean tryAcquire(Lease lease) {
        return held.putIfAbsent(lease.resourceKey(), lease) == null;
    }

    @Override
    public int renew(long jobId, Instant expiresAt) {
        int renewed = 0;
        for (Map.Entry<String, Lease> entry : held.entrySet()) {
            Lease lease = entry.getValue();
            if (lease.jobId() == jobId) {
                entry.setValue(new Lease(lease.resourceKey(), lease.jobId(), lease.runId(),
                        lease.projectId(), lease.acquiredAt(), expiresAt));
                renewed++;
            }
        }
        return renewed;
    }

    @Override
    public int release(long jobId, String reason) {
        List<String> keys = keysOfJob(jobId);
        Instant now = Instant.now();
        for (String key : keys) {
            Lease lease = held.get(key);
            if (lease != null) {
                recordHistory(lease, now, "released", reason);
                held.remove(key);
            }
        }
        return keys.size();
    }

    @Override
    public int releaseOne(String resourceKey, long jobId, String reason) {
        Lease lease = held.get(resourceKey);
        if (lease == null || lease.jobId() != jobId) {
            return 0;
        }
        recordHistory(lease, Instant.now(),
                reason != null && reason.contains("expired") ? "expired" : "released", reason);
        held.remove(resourceKey);
        return 1;
    }

    @Override
    public List<Lease> findByJob(long jobId) {
        return held.values().stream().filter(l -> l.jobId() == jobId).collect(Collectors.toList());
    }

    @Override
    public List<Lease> findByRunId(String runId) {
        return held.values().stream().filter(l -> runId.equals(l.runId())).collect(Collectors.toList());
    }

    @Override
    public List<Lease> findExpired(Instant now) {
        return held.values().stream()
                .filter(l -> l.expiresAt() != null && l.expiresAt().isBefore(now))
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> findHeldJobIds() {
        return held.values().stream().map(Lease::jobId).distinct().collect(Collectors.toList());
    }

    @Override
    public List<LeaseView> listByProject(long projectId) {
        Instant now = Instant.now();
        List<LeaseView> views = new ArrayList<>();
        held.values().stream().filter(l -> l.projectId() == projectId).forEach(l -> views.add(
                new LeaseView(l.resourceKey(), l.runId(), l.jobId(), l.projectId(),
                        l.acquiredAt(), l.expiresAt(), remainingSeconds(now, l.expiresAt()), "held")));
        return views;
    }

    @Override
    public void recordHistory(Lease lease, Instant releasedAt, String state, String reason) {
        history.add(new HistoryRow(lease.resourceKey(), lease.jobId(), state, reason));
    }

    public List<HistoryRow> history() {
        return List.copyOf(history);
    }

    public Optional<Lease> peek(String resourceKey) {
        return Optional.ofNullable(held.get(resourceKey));
    }

    public int size() {
        return held.size();
    }

    public void seed(Lease lease) {
        held.put(lease.resourceKey(), lease);
    }

    private List<String> keysOfJob(long jobId) {
        return held.entrySet().stream()
                .filter(e -> e.getValue().jobId() == jobId)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static long remainingSeconds(Instant now, Instant expiresAt) {
        return expiresAt == null ? 0 : Math.max(0, Duration.between(now, expiresAt).toSeconds());
    }
}
