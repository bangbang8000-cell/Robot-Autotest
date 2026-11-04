package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.ProjectAssetRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 资产引用与归属的 JDBC 实现；表名由 {@link AssetScopeSql#tableOf} 映射，类型不拼进 SQL。 */
@Repository
public class ProjectAssetRepositoryJdbc implements ProjectAssetRepository {

    private final JdbcTemplate jdbc;

    public ProjectAssetRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AssetScope> scopeOf(String assetType, long assetId) {
        String table = AssetScopeSql.tableOf(assetType);
        String sql = "SELECT id, project_id, owner_scope FROM `" + table + "` WHERE id = ?";
        TenantGuard.requireProjectScope(sql, table);
        List<AssetScope> rows = jdbc.query(sql, (rs, rowNum) -> new AssetScope(
                rs.getLong("id"),
                rs.getObject("project_id") == null ? null : rs.getLong("project_id"),
                rs.getString("owner_scope")), assetId);
        return rows.stream().findFirst();
    }

    @Override
    public void shareToPlatform(String assetType, long assetId, long ownerProjectId) {
        String table = AssetScopeSql.tableOf(assetType);
        String sql = "UPDATE `" + table + "` SET owner_scope = 'PLATFORM', project_id = NULL "
                + "WHERE id = ? AND owner_scope = 'PROJECT' AND project_id = ?";
        TenantGuard.requireProjectScope(sql, table);
        jdbc.update(sql, assetId, ownerProjectId);
    }

    @Override
    public void addReference(long projectId, String assetType, long assetId, String alias,
                             Long operatorId) {
        String sql = """
                INSERT INTO project_asset
                  (project_id, asset_type, asset_id, alias, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE alias = VALUES(alias), updated_by = VALUES(updated_by)
                """;
        TenantGuard.requireProjectScope(sql, "project_asset");
        jdbc.update(sql, projectId, AssetScopeSql.normalize(assetType), assetId, alias,
                operatorId, operatorId);
    }

    @Override
    public boolean removeReference(long projectId, String assetType, long assetId) {
        String sql = """
                DELETE FROM project_asset
                 WHERE project_id = ? AND asset_type = ? AND asset_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "project_asset");
        return jdbc.update(sql, projectId, AssetScopeSql.normalize(assetType), assetId) > 0;
    }

    /** 大小写统一由仓储负责，服务层不用重复写 toUpperCase。 */
    static String normalize(String assetType) {
        if (assetType == null || assetType.isBlank()) {
            throw new IllegalArgumentException("资产类型不能为空");
        }
        return assetType.trim().toUpperCase(Locale.ROOT);
    }
}
