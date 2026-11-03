package com.qa.autotest.infrastructure;

import com.qa.autotest.common.security.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 凭据加密密钥装配：{@code CRED_MASTER_KEY} 缺失即启动失败（AST-03）。
 *
 * <p>宁可起不来，也不允许用空密钥加密后入库。
 */
@Configuration
public class CipherConfig {

    @Bean
    public SecretCipher secretCipher(@Value("${CRED_MASTER_KEY:}") String masterKey) {
        return SecretCipher.fromBase64Key(masterKey);
    }
}
