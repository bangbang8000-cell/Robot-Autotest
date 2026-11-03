package com.qa.archfixture.violation.api2infra.api;

import com.qa.archfixture.violation.api2infra.infrastructure.InfraRepository;

/**
 * 违规 fixture：接口层直接 new 基础设施层对象（R3 必须拦住）。
 *
 * <p>这个包名故意放在 {@code com.qa.archfixture} 下，不在 {@code com.qa.autotest} 里，
 * 这样正向测试不会扫到它（TEST-PLAN TC-v0.1-012）。
 */
public class ApiCallingInfra {

    public String load() {
        return new InfraRepository().find();
    }
}
