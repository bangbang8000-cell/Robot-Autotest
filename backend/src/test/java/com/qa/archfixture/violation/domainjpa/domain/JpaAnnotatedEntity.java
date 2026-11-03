package com.qa.archfixture.violation.domainjpa.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 违规 fixture：领域对象带 JPA 注解（R2 必须拦住）。 */
@Entity
public class JpaAnnotatedEntity {

    @Id
    private Long id;

    public Long getId() {
        return id;
    }
}
