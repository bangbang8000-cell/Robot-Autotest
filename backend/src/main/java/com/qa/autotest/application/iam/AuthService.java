package com.qa.autotest.application.iam;

import com.qa.autotest.application.repository.SessionTokenRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.security.PasswordHasher;
import com.qa.autotest.common.security.TokenGenerator;
import com.qa.autotest.domain.iam.AppUser;
import com.qa.autotest.domain.iam.UserSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 登录与会话（IAM-02）。
 *
 * <p>锁定策略：连续失败 5 次锁 15 分钟；会话令牌 32 字节随机、只存 SHA-256、8 小时过期。
 * 登录失败一律返回同一句「用户名或密码错误」，不区分账号是否存在。
 */
@Service
public class AuthService {

    /** 会话有效期。 */
    public static final Duration SESSION_TTL = Duration.ofHours(8);

    /** 触发锁定的失败次数。 */
    public static final int MAX_FAILED = 5;

    /** 锁定时长。 */
    public static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final UserRepository users;
    private final SessionTokenRepository sessions;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;

    public AuthService(UserRepository users, SessionTokenRepository sessions,
                       PasswordPolicy passwordPolicy, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
    }

    /** 登录成功的结果：明文令牌只在这里出现一次，之后只存哈希。 */
    public record LoginResult(String token, Instant expiresAt, UserSummary user,
                              boolean passwordExpired) {
    }

    public LoginResult login(String username, String rawPassword) {
        Instant now = Instant.now();
        Optional<AppUser> found = users.findByUsername(username == null ? "" : username.trim());
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        AppUser user = found.get();
        if (!user.enabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        if (user.lockedAt(now)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已锁定，请稍后再试");
        }
        if (!PasswordHasher.verify(rawPassword, user.passwordHash())) {
            int failed = user.failedLoginCount() + 1;
            Instant lockedUntil = failed >= MAX_FAILED ? now.plus(LOCK_TTL) : null;
            users.updateLoginFailure(user.id(), failed, lockedUntil);
            audit.record("auth.login-failed", "app_user", String.valueOf(user.id()),
                    "failed=" + failed);
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        users.updateLoginSuccess(user.id(), now);
        String token = TokenGenerator.newToken();
        Instant expiresAt = now.plus(SESSION_TTL);
        sessions.insert(user.id(), PasswordHasher.sha256Hex(token), expiresAt);
        audit.record("auth.login", "app_user", String.valueOf(user.id()), "username=" + user.username());
        UserSummary summary = UserSummary.withoutRoles(user).withRoles(users.roleCodesOf(user.id()));
        return new LoginResult(token, expiresAt, summary,
                passwordPolicy.expired(user.passwordExpiresAt(), now));
    }

    /** 注销：撤销令牌；令牌不存在也静默成功（不泄露有效性）。 */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.revoke(PasswordHasher.sha256Hex(token), Instant.now());
        }
    }

    /** 当前用户视图。 */
    public UserSummary me(long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "用户不存在"));
        return UserSummary.withoutRoles(user).withRoles(users.roleCodesOf(userId));
    }

    /** 令牌 → 用户 id（认证拦截器用）。 */
    public Optional<Long> resolveUserId(String token) {
        return sessions.findValidUserId(PasswordHasher.sha256Hex(token), Instant.now());
    }

    /** 令牌 → 用户实体（认证拦截器用；只取 id/username 等必要字段）。 */
    public Optional<AppUser> resolveUser(String token) {
        return resolveUserId(token).flatMap(users::findById);
    }

    /** 记录令牌使用时间（可通过配置关闭，当前每个已认证请求都刷新）。 */
    public void touch(String token) {
        sessions.touch(PasswordHasher.sha256Hex(token), Instant.now());
    }
}
