package com.qa.autotest.application.repository;

import com.qa.autotest.domain.iam.ProjectApiToken;
import java.time.Instant;
import java.util.Optional;

/**
 * 项目 API 令牌仓储端口（PLN-03）。
 *
 * <p>只暴露按哈希查询与「最近使用时间」回写；令牌明文不进入应用层。
 */
public interface ProjectApiTokenRepository {

    /**
     * 查有效令牌：token_kind='api' 且未吊销、未过期。
     *
     * @param tokenHash SHA-256 十六进制
     */
    Optional<ProjectApiToken> findActiveByHash(String tokenHash, Instant now);

    void touch(String tokenHash, Instant usedAt);
}
