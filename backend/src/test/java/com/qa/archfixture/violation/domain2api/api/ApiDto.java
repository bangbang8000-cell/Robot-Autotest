package com.qa.archfixture.violation.domain2api.api;

/** 违规 fixture 的靶子：接口层 DTO。 */
public class ApiDto {

    private final String value;

    public ApiDto(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
