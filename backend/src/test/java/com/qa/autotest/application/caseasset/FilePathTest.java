package com.qa.autotest.application.caseasset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 文件路径穿越防护单测（CAS-04，TC-v0.2-030）。 */
class FilePathTest {

    @Test
    @DisplayName("相对路径被规范化成统一形态")
    void 正常路径() {
        assertEquals("suites/smoke.robot", FileWorkspaceService.normalize("./suites//smoke.robot"));
        assertEquals("suites/smoke.robot", FileWorkspaceService.normalize("suites\\smoke.robot"));
    }

    @Test
    @DisplayName(".. 一律拒绝")
    void 拒绝上跳() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> FileWorkspaceService.normalize("suites/../../etc/passwd"));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
    }

    @Test
    @DisplayName("绝对路径被拒")
    void 拒绝绝对路径() {
        assertThrows(BusinessException.class,
                () -> FileWorkspaceService.normalize("/etc/passwd"));
        assertThrows(BusinessException.class,
                () -> FileWorkspaceService.normalize("C:/Windows/system.ini"));
    }

    @Test
    @DisplayName("扩展名白名单外的文件被拒")
    void 拒绝非法扩展名() {
        assertThrows(BusinessException.class,
                () -> FileWorkspaceService.normalize("suites/run.exe"));
    }

    @Test
    @DisplayName("目录路径（无扩展名）放行")
    void 目录放行() {
        assertEquals("suites/roce", FileWorkspaceService.normalize("suites/roce/"));
    }
}
