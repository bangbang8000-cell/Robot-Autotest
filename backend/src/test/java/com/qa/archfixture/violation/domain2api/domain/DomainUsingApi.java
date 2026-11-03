package com.qa.archfixture.violation.domain2api.domain;

import com.qa.archfixture.violation.domain2api.api.ApiDto;

/** 违规 fixture：领域对象反向引用接口层 DTO（R1 必须拦住）。 */
public class DomainUsingApi {

    public ApiDto toDto() {
        return new ApiDto("x");
    }
}
