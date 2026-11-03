package com.qa.autotest.application.repository;

import com.qa.autotest.domain.iam.Role;
import java.util.List;
import java.util.Optional;

/** 角色仓储。 */
public interface RoleRepository {

    List<Role> findAll();

    Optional<Role> findById(long id);

    Optional<Role> findByCode(String code);

    long insert(Role role);

    void update(long id, String name, String description, String permissionsJson, Long updatedBy);

    void delete(long id);

    boolean codeExists(String code, Long excludeId);
}
