package com.qa.autotest.domain.importing;

/** 导入后的用例来源（IMP-01）。刻意不带设备外键：用例与设备资产彻底解耦。 */
public record CaseSource(String relPath, String kind, String nodeId, String checksum) {
}
