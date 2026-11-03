package com.qa.archfixture.clean.domain;

/** 干净 fixture：纯 POJO 领域对象，不依赖任何框架与上层，用来证明规则不会误杀。 */
public class CleanDomainObject {

    private final String code;

    public CleanDomainObject(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
