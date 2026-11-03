package com.qa.autotest.application.iam;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 关键操作审计标记（DEV-PLAN §3.2）。
 *
 * <p>标注在服务方法上，声明该操作写入 {@code audit_log} 的动作名与资源类型；
 * 具体写入由方法内的 {@link AuditService#recordChange} 完成。测试会通过反射核对
 * 注解声明的 action/resource 与实际记录一致，防止「注释写了、代码忘了」。
 *
 * <p>动作命名统一为 {@code 资源:动作}（例如 {@code credential:upsert}）。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    /** 动作名，格式 {@code resource:action}。 */
    String action();

    /** 资源类型，对应 audit_log.resource_type。 */
    String resource();
}
