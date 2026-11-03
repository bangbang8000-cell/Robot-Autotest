package com.qa.autotest.infrastructure;

import com.qa.autotest.application.plan.RunDispatcher;
import com.qa.autotest.common.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 计划运行派发器：单线程（core=max=1），与平台「执行队列严格串行」语义一致。
 *
 * <p>提交时用 {@link TenantContext#runAs} 包裹，保证异步线程里租户守卫与审计拿得到 project_id。
 */
@Component
public class PlanRunDispatcher implements RunDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(PlanRunDispatcher.class);

    private final ThreadPoolTaskExecutor executor;

    public PlanRunDispatcher() {
        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setCorePoolSize(1);
        this.executor.setMaxPoolSize(1);
        this.executor.setQueueCapacity(64);
        this.executor.setThreadNamePrefix("plan-run-");
        this.executor.initialize();
    }

    @Override
    public void submit(long projectId, Long planId, String runId, Runnable task) {
        executor.execute(() -> TenantContext.runAs(null, "planner", projectId, () -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                LOG.error("计划 {} 运行 {} 执行派发异常: {}", planId, runId, ex.getMessage(), ex);
            }
        }));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
