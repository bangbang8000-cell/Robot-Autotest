package com.qa.autotest.application.execution;

import java.nio.file.Files;
import java.nio.file.Path;

/** NIO 实现。目录不存在时向上找最近存在的父目录判断可写性。 */
public class NioFileSystemProbe implements FileSystemProbe {

    @Override
    public boolean exists(Path path) {
        return path != null && Files.exists(path);
    }

    @Override
    public boolean executable(Path path) {
        return path != null && Files.isRegularFile(path) && Files.isExecutable(path);
    }

    @Override
    public boolean writable(Path path) {
        if (path == null) {
            return false;
        }
        Path current = path;
        while (current != null) {
            if (Files.exists(current)) {
                return Files.isDirectory(current) && Files.isWritable(current);
            }
            current = current.getParent();
        }
        return false;
    }

    @Override
    public long usableSpace(Path path) {
        if (path == null) {
            return 0;
        }
        Path current = path;
        while (current != null) {
            if (Files.exists(current)) {
                try {
                    return Files.getFileStore(current).getUsableSpace();
                } catch (Exception ex) {
                    return 0;
                }
            }
            current = current.getParent();
        }
        return 0;
    }
}
