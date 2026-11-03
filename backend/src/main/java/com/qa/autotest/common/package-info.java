/**
 * 公共层：统一响应、错误码、工具。
 *
 * <p><b>叶子层</b>：只允许被依赖，不得依赖 {@code api} / {@code application} /
 * {@code domain} / {@code infrastructure}（ArchUnit 规则 R5 守卫）。
 */
package com.qa.autotest.common;
