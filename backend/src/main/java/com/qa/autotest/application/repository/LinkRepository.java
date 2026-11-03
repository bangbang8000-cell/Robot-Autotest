package com.qa.autotest.application.repository;

import com.qa.autotest.domain.asset.Link;
import com.qa.autotest.domain.common.PageResult;
import java.util.Optional;

/** 链路仓储。 */
public interface LinkRepository {

    PageResult<Link> page(long projectId, String keyword, int page, int size);

    Optional<Link> find(long projectId, long id);

    long insert(Link link, Long operatorId);

    void update(Link link, Long operatorId);

    void delete(long projectId, long id);
}
