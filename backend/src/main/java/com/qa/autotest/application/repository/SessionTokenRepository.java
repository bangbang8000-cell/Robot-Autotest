package com.qa.autotest.application.repository;

import java.time.Instant;
import java.util.Optional;

/** 会话令牌仓储（api_token.token_kind='session'）。 */
public interface SessionTokenRepository {

    void insert(long userId, String tokenHash, Instant expiresAt);

    /** 有效（未撤销、未过期）令牌对应的用户 id。 */
    Optional<Long> findValidUserId(String tokenHash, Instant now);

    void touch(String tokenHash, Instant usedAt);

    /** 续期：把未撤销令牌的过期时间推到 {@code expiresAt}，同时记一次使用。 */
    void extend(String tokenHash, Instant expiresAt, Instant usedAt);

    void revoke(String tokenHash, Instant revokedAt);
}
