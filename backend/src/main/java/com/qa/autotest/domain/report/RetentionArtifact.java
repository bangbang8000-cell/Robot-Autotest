package com.qa.autotest.domain.report;

/**
 * 待清理的产物文件（RPT-06）。只包含 availability='ok' 的行。
 *
 * @param id         run_artifact.id
 * @param path       文件路径（相对或绝对，按落库值原样删除）
 * @param sizeBytes  文件字节数
 * @param pinned     是否手动保留
 * @param note       备注（p0_evidence 等）
 */
public record RetentionArtifact(long id,
                                String path,
                                long sizeBytes,
                                boolean pinned,
                                String note) {

    /** 该文件是否受保护（pinned 或 p0 证据）。 */
    public boolean protectedFile() {
        return pinned || (note != null && note.contains("p0_evidence"));
    }
}
