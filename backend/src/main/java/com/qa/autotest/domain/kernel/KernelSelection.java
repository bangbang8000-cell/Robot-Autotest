package com.qa.autotest.domain.kernel;

/** 内核选择结果（KRN-04）：kernel + 生效来源 + 是否被上层覆盖。 */
public record KernelSelection(String kernel, String source, boolean overridden) {
}
