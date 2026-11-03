package com.qa.autotest.application.ci;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 令牌级滑动窗口限流（PLN-03）。
 *
 * <p>单实例内存实现：键为令牌 ID，窗口内请求数超过上限即拒绝（HTTP 429）。
 * 多实例部署时各自计数，上限会成倍放宽——本版为单实例部署，接受该近似；
 * 若后续扩到多实例，应换成共享存储（Redis / 数据库计数），接口保持不变。
 */
@Component
public class TokenRateLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public TokenRateLimiter(CiProperties properties, Clock clock) {
        this.limit = Math.max(1, properties.getTriggerPerMinute());
        this.window = Duration.ofSeconds(Math.max(1, properties.getTriggerWindowSeconds()));
        this.clock = clock;
    }

    /**
     * 尝试占用一次配额。
     *
     * @return true 放行；false 超限
     */
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        Deque<Instant> queue = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            Instant edge = now.minus(window);
            while (!queue.isEmpty() && !queue.peekFirst().isAfter(edge)) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }
}
