/**
 * 接口层：控制器、DTO、参数校验、异常映射。
 *
 * <p>只允许依赖 {@code application} 与 {@code common}；<b>不得直接依赖 {@code infrastructure}</b>
 * （ArchUnit 规则 R3 守卫）。这里的职责是「把 HTTP 翻译成用例调用」，不写业务判断。
 */
package com.qa.autotest.api;
