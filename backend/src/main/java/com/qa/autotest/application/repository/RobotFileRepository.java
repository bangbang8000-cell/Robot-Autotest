package com.qa.autotest.application.repository;

import com.qa.autotest.domain.caseasset.FileNode;
import com.qa.autotest.domain.caseasset.RobotFile;
import com.qa.autotest.domain.caseasset.RobotFileVersion;
import java.util.List;
import java.util.Optional;

/** 脚本文件工作区仓储（ADR-0005）。 */
public interface RobotFileRepository {

    List<FileNode> nodes(long projectId);

    Optional<RobotFile> findByPath(long projectId, String path);

    Optional<RobotFile> find(long projectId, long id);

    long insert(long projectId, String path, String content, String sha256, Long operatorId);

    /** 保存内容：更新文件 + 写版本快照 + current_version+1。 */
    void updateContent(long projectId, long id, String content, String sha256,
                       String comment, Long operatorId);

    /**
     * 带乐观锁的保存（EDT-09）：只有当前版本仍等于 {@code expectedVersion} 才写入。
     *
     * @return false 表示版本已变（并发保存），调用方应返回 409
     */
    boolean updateContentIfVersion(long projectId, long id, int expectedVersion, String content,
                                   String sha256, String comment, Long operatorId);

    void delete(long projectId, long id);

    void deleteByPrefix(long projectId, String prefix);

    void move(long projectId, long id, String newPath, Long operatorId);

    boolean pathExists(long projectId, String path, Long excludeId);

    List<RobotFileVersion> versions(long projectId, long fileId);
}
