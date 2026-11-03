package com.qa.autotest.application.report;

/**
 * 产物文件删除端口（RPT-06）。
 *
 * <p>把文件系统隔离在应用层之外：单元测试用内存实现，绝不会碰到仓库里的真实文件；
 * 生产实现只删除 {@code run_artifact.path} 里登记过的路径。
 */
public interface ArtifactFileStore {

    /**
     * 删除一个产物文件。
     *
     * @param path 落库的产物路径
     * @return true 表示本次确实删掉了文件；false 表示文件本就不存在（视为已清理）
     */
    boolean delete(String path);
}
