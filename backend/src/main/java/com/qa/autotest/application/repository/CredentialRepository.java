package com.qa.autotest.application.repository;

import com.qa.autotest.domain.asset.Credential;
import java.util.List;
import java.util.Optional;

/** 凭据仓储：密文只进不出；对外视图由应用层组装。 */
public interface CredentialRepository {

    List<Credential> list(long projectId);

    Optional<Credential> find(long projectId, long id);

    /** 仅供执行域取用；调用必须写审计（AST-03）。 */
    Optional<String> cipherOf(long projectId, long id);

    long insert(long projectId, String name, String credType, String username,
                String cipherBlob, int keyVersion, Long operatorId);

    void update(long projectId, long id, String name, String username, String cipherBlob,
                Long operatorId);

    void delete(long projectId, long id);

    boolean nameExists(long projectId, String name, Long excludeId);
}
