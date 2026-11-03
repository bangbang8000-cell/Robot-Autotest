package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.SessionTokenRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 会话令牌仓储实现（api_token，token_kind='session'）。 */
@Repository
public class SessionTokenRepositoryJdbc implements SessionTokenRepository {

    private final JdbcTemplate jdbc;

    public SessionTokenRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(long userId, String tokenHash, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO api_token (user_id, name, token_hash, token_kind, expires_at)
                VALUES (?, 'session', ?, 'session', ?)
                """, userId, tokenHash, Timestamp.from(expiresAt));
    }

    @Override
    public java.util.Optional<Long> findValidUserId(String tokenHash, Instant now) {
        List<Long> rows = jdbc.query("""
                SELECT user_id FROM api_token
                 WHERE token_hash = ? AND token_kind = 'session'
                   AND revoked_at IS NULL AND expires_at IS NOT NULL AND expires_at > ?
                """, (rs, rowNum) -> rs.getLong(1), tokenHash, Timestamp.from(now));
        return JdbcSupport.first(rows);
    }

    @Override
    public void touch(String tokenHash, Instant usedAt) {
        jdbc.update("UPDATE api_token SET last_used_at = ? WHERE token_hash = ?",
                Timestamp.from(usedAt), tokenHash);
    }

    @Override
    public void revoke(String tokenHash, Instant revokedAt) {
        jdbc.update("UPDATE api_token SET revoked_at = ? WHERE token_hash = ?",
                Timestamp.from(revokedAt), tokenHash);
    }
}
