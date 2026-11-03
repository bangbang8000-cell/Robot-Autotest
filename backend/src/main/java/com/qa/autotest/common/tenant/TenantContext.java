package com.qa.autotest.common.tenant;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;

/**
 * 请求级租户上下文（ADR-0006）。
 *
 * <p>由认证拦截器在请求进入时写入、请求结束时 {@link #clear()}；业务层只从这里取
 * {@code userId} / {@code projectId}，<b>不接受前端直接传 projectId</b>。
 *
 * <p>定时任务 / 异步线程必须显式调用 {@link #runAs(Long, String, Long, Runnable)} 包裹，
 * 否则上下文为空，租户守卫会直接拒绝——宁可失败，不可全表查。
 */
public final class TenantContext {

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 当前请求主体。 */
    public record Principal(Long userId, String username, Long projectId) {
    }

    public static void set(Long userId, String username, Long projectId) {
        HOLDER.set(new Principal(userId, username, projectId));
    }

    public static void setProjectId(Long projectId) {
        Principal current = HOLDER.get();
        if (current == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "未认证，无法设置项目上下文");
        }
        HOLDER.set(new Principal(current.userId(), current.username(), projectId));
    }

    public static Principal current() {
        return HOLDER.get();
    }

    public static Long userId() {
        Principal current = HOLDER.get();
        return current == null ? null : current.userId();
    }

    public static String username() {
        Principal current = HOLDER.get();
        return current == null ? null : current.username();
    }

    public static Long projectId() {
        Principal current = HOLDER.get();
        return current == null ? null : current.projectId();
    }

    public static Long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "未认证");
        }
        return id;
    }

    public static Long requireProjectId() {
        Long id = projectId();
        if (id == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少项目上下文（请求头 X-Project-Id）");
        }
        return id;
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 在指定身份下执行一段逻辑（定时任务 / 异步线程用），结束后恢复原上下文。 */
    public static void runAs(Long userId, String username, Long projectId, Runnable action) {
        Principal previous = HOLDER.get();
        try {
            HOLDER.set(new Principal(userId, username, projectId));
            action.run();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
