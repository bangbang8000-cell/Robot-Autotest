package com.qa.autotest.application.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qa.autotest.application.repository.SessionTokenRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.security.PasswordHasher;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 会话续期单测（FR-v0.2-04「过期前 5 分钟自动续」的后端契约）。 */
class AuthServiceRenewTest {

    private final UserRepository users = mock(UserRepository.class);
    private final SessionTokenRepository sessions = mock(SessionTokenRepository.class);
    private final AuthService auth =
            new AuthService(users, sessions, new PasswordPolicy(), mock(AuditService.class));

    @Test
    @DisplayName("有效令牌：续期后过期时间再推一个 TTL，并写库")
    void 有效令牌续期成功() {
        String token = "raw-token";
        String hash = PasswordHasher.sha256Hex(token);
        when(sessions.findValidUserId(eq(hash), any())).thenReturn(Optional.of(7L));

        Instant before = Instant.now();
        Instant expiresAt = auth.renew(token);
        Instant after = Instant.now();

        assertTrue(!expiresAt.isBefore(before.plus(AuthService.SESSION_TTL)),
                "续期后的过期时间不得早于 now+TTL");
        assertTrue(!expiresAt.isAfter(after.plus(AuthService.SESSION_TTL)));
        verify(sessions).extend(eq(hash), eq(expiresAt), any());
    }

    @Test
    @DisplayName("已过期 / 已撤销：拒绝续期，要求重新登录")
    void 无效令牌拒绝续期() {
        when(sessions.findValidUserId(any(), any())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> auth.renew("stale-token"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.errorCode());
        verify(sessions, never()).extend(any(), any(), any());
    }

    @Test
    @DisplayName("空令牌：直接拒绝，不查库")
    void 空令牌直接拒绝() {
        assertThrows(BusinessException.class, () -> auth.renew(null));
        assertThrows(BusinessException.class, () -> auth.renew("  "));
        verify(sessions, never()).findValidUserId(any(), any());
    }
}
