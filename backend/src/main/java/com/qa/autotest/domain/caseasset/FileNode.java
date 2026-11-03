package com.qa.autotest.domain.caseasset;

import java.time.Instant;

/** 文件工作区节点（CAS-04 的树视图）。 */
public record FileNode(
        String path,
        boolean directory,
        int size,
        Instant updatedAt) {
}
