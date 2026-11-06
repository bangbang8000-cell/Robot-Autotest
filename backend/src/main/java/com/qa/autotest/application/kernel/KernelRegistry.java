package com.qa.autotest.application.kernel;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.kernel.ExecutionKernel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 内核注册表（KRN-01）：Spring 收集所有 SPI 实现，未知名字一律拒绝而非静默回退。 */
@Service
public class KernelRegistry {

    private final Map<String, ExecutionKernel> kernels;

    public KernelRegistry(List<ExecutionKernel> beans) {
        Map<String, ExecutionKernel> collected = new LinkedHashMap<>();
        for (ExecutionKernel bean : beans) {
            if (collected.putIfAbsent(bean.name(), bean) != null) {
                throw new IllegalStateException("内核名重复: " + bean.name());
            }
        }
        this.kernels = Map.copyOf(collected);
    }

    public ExecutionKernel require(String name) {
        ExecutionKernel kernel = kernels.get(name);
        if (kernel == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "未注册的执行内核: " + name + "；可选 " + kernels.keySet());
        }
        return kernel;
    }

    public Set<String> names() {
        return kernels.keySet();
    }
}
