package com.qa.autotest.application.execution;

import java.nio.file.Path;

/**
 * 文件系统探针。抽象出来便于预检单测不依赖真实磁盘配额。
 */
public interface FileSystemProbe {

    boolean exists(Path path);

    boolean executable(Path path);

    boolean writable(Path path);

    long usableSpace(Path path);
}
