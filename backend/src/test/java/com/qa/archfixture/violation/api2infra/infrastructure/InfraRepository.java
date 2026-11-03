package com.qa.archfixture.violation.api2infra.infrastructure;

/** 违规 fixture 的靶子：被接口层直接依赖的基础设施类。 */
public class InfraRepository {

    public String find() {
        return "infra";
    }
}
