/**
 * 领域层：实体、值对象、领域服务、仓储接口。
 *
 * <p><b>纯 POJO</b>，不依赖 Spring / JPA / 上层任何包（ArchUnit 规则 R1、R2 守卫）。
 * 持久化映射写在 {@code infrastructure}；这样领域逻辑（如 v0.4 的租约状态机）
 * 可以脱离数据库与 Spring 容器单测。
 */
package com.qa.autotest.domain;
