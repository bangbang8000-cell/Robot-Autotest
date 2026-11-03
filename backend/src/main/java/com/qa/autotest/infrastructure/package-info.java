/**
 * 基础设施层：仓储实现、外部适配、执行器。
 *
 * <p>实现 {@code domain} 定义的仓储接口，可以依赖框架；<b>不得被 {@code api} 直接依赖</b>
 * （ArchUnit 规则 R3 守卫）。
 */
package com.qa.autotest.infrastructure;
