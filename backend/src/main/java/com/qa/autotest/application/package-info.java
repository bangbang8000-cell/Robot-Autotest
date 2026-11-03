/**
 * 应用层：用例编排与事务边界。
 *
 * <p>可以依赖 {@code domain} 与 {@code common}；<b>不得依赖 {@code api}</b>
 * （ArchUnit 规则 R4 守卫）。一个应用服务 = 一次业务动作，事务边界就画在这里。
 */
package com.qa.autotest.application;
