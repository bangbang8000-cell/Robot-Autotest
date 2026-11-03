package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.ProjectApiTokenRepository;
import com.qa.autotest.domain.iam.ProjectApiToken;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 项目 API 令牌仓储实现（api_token，token_kind='api'，PLN-03）。
 *
 * <p>认证发生在租户上下文建立之前，因此这里不调用 TenantGuard：查询条件用令牌哈希精确命中，
 * 不接受任何项目参数；返回的 project_id 由后续服务写入租户上下文。
 */
@Repository
public class ProjectApiTokenRepositoryJdbc implements ProjectApiTokenRepository {

    private final JdbcTemplate jdbc;

    public ProjectApiTokenRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProjectApiToken> findActiveByHash(String tokenHash, Instant now) {
        List<ProjectApiToken> rows = jdbc.query("""
                SELECT id, user_id, name, project_id, scopes
                  FROM api_token
                 WHERE token_hash = ? AND token_kind = 'api'
                   AND revoked_at IS NULL
                   AND (expires_at IS NULL OR expires_at > ?)
                """, (rs, rowNum) -> new ProjectApiToken(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getObject("project_id", Long.class),
                        rs.getString("scopes")),
                tokenHash, Timestamp.from(now));
        return JdbcSupport.first(rows);
    }

    @Override
    public void touch(String tokenHash, Instant usedAt) {
        jdbc.update("UPDATE api_token SET last_used_at = ? WHERE token_hash = ?",
                Timestamp.from(usedAt), tokenHash);
    }
}
