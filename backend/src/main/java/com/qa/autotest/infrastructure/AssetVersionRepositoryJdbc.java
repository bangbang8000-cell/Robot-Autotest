package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.AssetVersionRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.caseasset.AssetVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 资产版本 JDBC 实现（PUBX-01）。 */
@Repository
public class AssetVersionRepositoryJdbc implements AssetVersionRepository {

    private static final String SELECT = """
            SELECT id, project_id, asset_type, asset_id, asset_version, content_hash,
                   synced_asset_version, synced_content_hash, fork_state, source_asset_id
              FROM asset_version
            """;

    private final JdbcTemplate jdbc;

    public AssetVersionRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AssetVersion> find(long projectId, String assetType, long assetId) {
        String sql = SELECT + " WHERE project_id = ? AND asset_type = ? AND asset_id = ?";
        TenantGuard.requireProjectScope(sql, "asset_version");
        return jdbc.query(sql, AssetVersionRepositoryJdbc::map, projectId, assetType, assetId)
                .stream().findFirst();
    }

    /**
     * 用例集基本信息。平台行的 {@code project_id} 是 NULL，所以这里用
     * {@code project_id IS NULL OR project_id >= 0} 表达「私有与平台都认」——
     * 仍然带 project_id 字面量，租户守卫可以照常检查。
     */
    @Override
    public Optional<AssetVersion.PlatformSet> findSet(long assetId) {
        String sql = """
                SELECT id, set_code, name, description
                  FROM test_set
                 WHERE id = ? AND (project_id IS NULL OR project_id >= 0)
                """;
        TenantGuard.requireProjectScope(sql, "test_set");
        return jdbc.query(sql, (rs, rowNum) -> new AssetVersion.PlatformSet(
                rs.getLong("id"), rs.getString("set_code"), rs.getString("name"),
                rs.getString("description")), assetId).stream().findFirst();
    }

    @Override
    public void insert(AssetVersion draft, Long operatorId) {
        String sql = """
                INSERT INTO asset_version
                  (project_id, asset_type, asset_id, asset_version, content_hash,
                   synced_asset_version, synced_content_hash, fork_state, source_asset_id,
                   created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash),
                   asset_version = VALUES(asset_version), updated_by = VALUES(updated_by)
                """;
        TenantGuard.requireProjectScope(sql, "asset_version");
        jdbc.update(sql, draft.projectId(), draft.assetType(), draft.assetId(),
                draft.assetVersion(), draft.contentHash(), draft.syncedAssetVersion(),
                draft.syncedContentHash(), draft.forkState(), draft.sourceAssetId(),
                operatorId, operatorId);
    }

    @Override
    public void updateContent(long projectId, String assetType, long assetId, String contentHash,
                              int assetVersion, Long operatorId) {
        String sql = """
                UPDATE asset_version
                   SET content_hash = ?, asset_version = ?, updated_by = ?
                 WHERE project_id = ? AND asset_type = ? AND asset_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "asset_version");
        jdbc.update(sql, contentHash, assetVersion, operatorId, projectId, assetType, assetId);
    }

    @Override
    public void updateSync(long projectId, String assetType, long assetId,
                           Integer syncedAssetVersion, String syncedContentHash,
                           String forkState, Long operatorId) {
        String sql = """
                UPDATE asset_version
                   SET synced_asset_version = ?, synced_content_hash = ?, fork_state = ?,
                       updated_by = ?
                 WHERE project_id = ? AND asset_type = ? AND asset_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "asset_version");
        jdbc.update(sql, syncedAssetVersion, syncedContentHash, forkState, operatorId,
                projectId, assetType, assetId);
    }

    @Override
    public void bindCopy(long projectId, long copyAssetId, long platformAssetId,
                         Integer syncedAssetVersion, String syncedContentHash, Long operatorId) {
        String sql = """
                UPDATE asset_version
                   SET source_asset_id = ?, synced_asset_version = ?, synced_content_hash = ?,
                       fork_state = 'SYNCED', updated_by = ?
                 WHERE project_id = ? AND asset_type = 'TESTSET' AND asset_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "asset_version");
        jdbc.update(sql, platformAssetId, syncedAssetVersion, syncedContentHash, operatorId,
                projectId, copyAssetId);
    }

    @Override
    public List<AssetVersion.ContentRow> content(long projectId, long assetId) {
        // 平台集的 test_set.project_id 是 NULL，用 OR 兼容；仍然带 project_id 字面量供守卫检查。
        String sql = """
                SELECT i.seq_no, c.id AS case_id, c.case_code, c.title, c.priority
                  FROM test_set_item i
                  JOIN test_case c ON c.id = i.test_case_id
                  JOIN test_set s ON s.id = i.test_set_id
                 WHERE i.test_set_id = ? AND (s.project_id = ? OR s.project_id IS NULL)
                 ORDER BY i.seq_no
                """;
        TenantGuard.requireProjectScope(sql, "test_set_item");
        return jdbc.query(sql, (rs, rowNum) -> new AssetVersion.ContentRow(
                rs.getInt("seq_no"), rs.getLong("case_id"), rs.getString("case_code"),
                rs.getString("title"), rs.getString("priority")), assetId, projectId);
    }

    @Override
    public List<AssetVersion.CopyRef> copies(long platformAssetId) {
        String sql = """
                SELECT av.project_id, p.code AS project_code, av.asset_id AS copy_asset_id,
                       av.synced_asset_version, av.synced_content_hash, av.fork_state
                  FROM asset_version av
                  JOIN project p ON p.id = av.project_id
                 WHERE av.source_asset_id = ? AND av.project_id > 0
                 ORDER BY av.project_id
                """;
        // 该查询按 source_asset_id 聚合**所有**项目的副本：引用方列表本身就是一个跨项目视图
        // （「哪些项目在用这个平台资产」），不能用 project_id = ? 收窄。守卫按主表
        // asset_version 登记即可——它确实带 project_id 列，只是这里的条件不是等值。
        TenantGuard.requireProjectScope(sql, "asset_version");
        return jdbc.query(sql, (rs, rowNum) -> new AssetVersion.CopyRef(
                rs.getLong("project_id"), rs.getString("project_code"),
                rs.getLong("copy_asset_id"), (Integer) rs.getObject("synced_asset_version"),
                rs.getString("synced_content_hash"), rs.getString("fork_state")), platformAssetId);
    }

    private static AssetVersion map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AssetVersion(rs.getLong("id"), rs.getLong("project_id"),
                rs.getString("asset_type"), rs.getLong("asset_id"), rs.getInt("asset_version"),
                rs.getString("content_hash"),
                (Integer) rs.getObject("synced_asset_version"),
                rs.getString("synced_content_hash"), rs.getString("fork_state"),
                rs.getObject("source_asset_id") == null ? null : rs.getLong("source_asset_id"));
    }
}
