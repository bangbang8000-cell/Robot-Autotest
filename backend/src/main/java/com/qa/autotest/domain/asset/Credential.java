package com.qa.autotest.domain.asset;

import java.time.Instant;

/**
 * 凭据视图（FR-v0.2-13）：对外<b>永不回显明文或密文</b>，只返回「是否已配置」与修改时间。
 *
 * <p>{@code cipherBlob} 只存在于仓储内部记录，不出应用层。
 */
public record Credential(
        Long id,
        Long projectId,
        String name,
        String credType,
        String username,
        boolean configured,
        int keyVersion,
        Instant lastRotatedAt) {
}
