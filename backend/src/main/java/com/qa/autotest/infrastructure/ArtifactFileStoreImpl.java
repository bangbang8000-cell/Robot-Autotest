package com.qa.autotest.infrastructure;

import com.qa.autotest.application.report.ArtifactFileStore;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 产物文件删除的生产实现（RPT-06）。
 *
 * <p>只删除 {@code run_artifact.path} 里登记过的单个文件，成功后尝试移除已空的运行目录，
 * <b>不做目录递归扫描</b>，避免把未登记的现场文件一起带走。
 */
@Component
public class ArtifactFileStoreImpl implements ArtifactFileStore {

    @Override
    public boolean delete(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Path target = Paths.get(path).normalize();
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                removeEmptyParent(target);
            }
            return deleted;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "产物删除失败: " + path);
        }
    }

    private static void removeEmptyParent(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return;
        }
        try (Stream<Path> children = Files.list(parent)) {
            if (children.findAny().isEmpty()) {
                Files.deleteIfExists(parent);
            }
        }
    }
}
