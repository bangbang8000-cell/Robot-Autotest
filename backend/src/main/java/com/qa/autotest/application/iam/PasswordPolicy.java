package com.qa.autotest.application.iam;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 密码策略（FR-v0.2-05）。
 *
 * <p>规则：长度 10~64、至少含大写 / 小写 / 数字、不得等于用户名、90 天过期。
 * 校验失败抛 {@link ErrorCode#INVALID_ARGUMENT}，由全局异常处理器转 400。
 */
@Component
public class PasswordPolicy {

    /** 最短长度。 */
    public static final int MIN_LENGTH = 10;

    /** 最长长度（防超长口令拖慢 PBKDF2）。 */
    public static final int MAX_LENGTH = 64;

    /** 过期天数。 */
    public static final int EXPIRY_DAYS = 90;

    /** 校验口令；不合规直接抛异常。 */
    public void validate(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "口令长度至少 " + MIN_LENGTH + " 位");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "口令长度不能超过 " + MAX_LENGTH + " 位");
        }
        if (rawPassword.chars().noneMatch(Character::isUpperCase)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "口令必须包含大写字母");
        }
        if (rawPassword.chars().noneMatch(Character::isLowerCase)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "口令必须包含小写字母");
        }
        if (rawPassword.chars().noneMatch(Character::isDigit)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "口令必须包含数字");
        }
        if (username != null && rawPassword.equalsIgnoreCase(username)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "口令不得与用户名相同");
        }
    }

    /** 计算过期时间（从现在起 90 天）。 */
    public Instant expiresAt(Instant now) {
        return now.plus(Duration.ofDays(EXPIRY_DAYS));
    }

    /** 口令是否已过期（无过期时间视为不过期，兼容历史账号）。 */
    public boolean expired(Instant expiresAt, Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
