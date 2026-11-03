package com.qa.autotest.application.ci;

import com.qa.autotest.application.repository.ProjectApiTokenRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.security.PasswordHasher;
import com.qa.autotest.domain.iam.ProjectApiToken;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 项目 API 令牌认证（PLN-03）。
 *
 * <p>与普通用户会话完全分离：只读 {@code token_kind='api'} 的行，要求绑定项目且具备
 * {@code ci:trigger} scope。任何失败分支都不回显令牌内容。
 */
@Service
public class CiTokenService {

    private static final String REQUIRED_SCOPE = "ci:trigger";

    private final ProjectApiTokenRepository tokens;
    private final Clock clock;

    public CiTokenService(ProjectApiTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * 解析 {@code Authorization: Bearer <token>}。
     *
     * @throws BusinessException 401 缺失/无效令牌；403 未绑定项目或缺 scope
     */
    public ProjectApiToken authenticate(String authorizationHeader) {
        String raw = bearerValue(authorizationHeader);
        if (raw == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "缺少 Bearer 令牌");
        }
        String tokenHash = PasswordHasher.sha256Hex(raw);
        Optional<ProjectApiToken> found = tokens.findActiveByHash(tokenHash, clock.instant());
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "令牌无效或已过期");
        }
        ProjectApiToken token = found.get();
        if (!token.boundToProject()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "令牌未绑定项目，不能用于 CI 触发");
        }
        if (!token.hasScope(REQUIRED_SCOPE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "令牌缺少 ci:trigger scope");
        }
        tokens.touch(tokenHash, clock.instant());
        return token;
    }

    static String bearerValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        if (value.length() < 7 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String raw = value.substring(7).trim();
        return raw.isEmpty() ? null : raw;
    }
}
