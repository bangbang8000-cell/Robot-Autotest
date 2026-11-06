package com.qa.autotest.api.caseasset;

import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.application.caseasset.FileWorkspaceService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.caseasset.FileNode;
import com.qa.autotest.domain.caseasset.RobotFile;
import com.qa.autotest.domain.caseasset.RobotFileVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CAS-04 接口层：文件工作区 6 端点（树 / 读取 / 版本 / 保存 / 上传 / 移动 / 删除）的路由与参数透传，
 * 以及「业务异常 → HTTP 状态码」这一跳。
 *
 * <p>用 standaloneSetup + 真实 {@link GlobalExceptionHandler}，断言的是**线上同一套**异常映射。
 */
class FileControllerTest {

    private FileWorkspaceService files;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        files = mock(FileWorkspaceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FileController(files))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static RobotFile file(String path, String content, int version) {
        return new RobotFile(5L, 7L, path, content, "sha-" + version, version);
    }

    @Test
    @DisplayName("树与读取：path 原样透传，目录标记与版本号原样返回")
    void 树与读取() throws Exception {
        Instant at = Instant.parse("2026-11-06T00:00:00Z");
        when(files.tree(7L)).thenReturn(List.of(
                new FileNode("suites", true, 0, at),
                new FileNode("suites/login.robot", false, 128, at)));
        mvc.perform(get("/api/projects/7/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value("suites"))
                .andExpect(jsonPath("$.data[0].directory").value(true))
                .andExpect(jsonPath("$.data[1].size").value(128));

        when(files.read(7L, "suites/login.robot")).thenReturn(file("suites/login.robot", "*** Test Cases ***", 2));
        mvc.perform(get("/api/projects/7/files/content").param("path", "suites/login.robot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("suites/login.robot"))
                .andExpect(jsonPath("$.data.currentVersion").value(2));
    }

    @Test
    @DisplayName("版本列表：path 透传，版本号与备注原样返回")
    void 版本列表() throws Exception {
        when(files.versions(7L, "suites/login.robot")).thenReturn(List.of(
                new RobotFileVersion(9L, "suites/login.robot", 2, "b", "sha-2", "改断言",
                        Instant.parse("2026-11-06T00:00:00Z")),
                new RobotFileVersion(8L, "suites/login.robot", 1, "a", "sha-1", "初稿",
                        Instant.parse("2026-11-05T00:00:00Z"))));

        mvc.perform(get("/api/projects/7/files/versions").param("path", "suites/login.robot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNo").value(2))
                .andExpect(jsonPath("$.data[0].comment").value("改断言"))
                .andExpect(jsonPath("$.data[1].versionNo").value(1));
    }

    @Test
    @DisplayName("保存：path / content / comment 三字段全部透传")
    void 保存透传() throws Exception {
        when(files.save(7L, "suites/login.robot", "*** Test Cases ***", "改断言"))
                .thenReturn(file("suites/login.robot", "*** Test Cases ***", 3));

        mvc.perform(put("/api/projects/7/files").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"suites/login.robot\",\"content\":\"*** Test Cases ***\","
                                + "\"comment\":\"改断言\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(3));
        verify(files).save(7L, "suites/login.robot", "*** Test Cases ***", "改断言");
    }

    @Test
    @DisplayName("上传：multipart 文件按 UTF-8 解码后走同一条保存通道")
    void 上传按UTF8解码() throws Exception {
        MockMultipartFile part = new MockMultipartFile("file", "login.robot", "text/plain",
                "*** Test Cases ***\n登录 中文断言".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(files.save(7L, "suites/login.robot", "*** Test Cases ***\n登录 中文断言", "导入"))
                .thenReturn(file("suites/login.robot", "*** Test Cases ***\n登录 中文断言", 1));

        mvc.perform(multipart("/api/projects/7/files/upload").file(part)
                        .param("path", "suites/login.robot").param("comment", "导入"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(1));
        verify(files).save(7L, "suites/login.robot", "*** Test Cases ***\n登录 中文断言", "导入");
    }

    @Test
    @DisplayName("移动与删除：from/to 两端路径与待删路径原样透传")
    void 移动与删除() throws Exception {
        when(files.move(7L, "suites/login.robot", "archive/login.robot"))
                .thenReturn(file("archive/login.robot", "x", 1));

        mvc.perform(post("/api/projects/7/files/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromPath\":\"suites/login.robot\",\"toPath\":\"archive/login.robot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("archive/login.robot"));
        verify(files).move(7L, "suites/login.robot", "archive/login.robot");

        mvc.perform(delete("/api/projects/7/files").param("path", "archive/login.robot"))
                .andExpect(status().isOk());
        verify(files).delete(7L, "archive/login.robot");
    }

    @Test
    @DisplayName("404 / 400：文件不存在走 NOT_FOUND，路径非法走 INVALID_ARGUMENT")
    void 状态码映射() throws Exception {
        when(files.read(7L, "suites/none.robot"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "文件不存在"));
        mvc.perform(get("/api/projects/7/files/content").param("path", "suites/none.robot"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codeName").value("NOT_FOUND"));

        when(files.save(eq(7L), eq("../evil.robot"), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_ARGUMENT, "路径不合法: ../evil.robot"));
        mvc.perform(put("/api/projects/7/files").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"../evil.robot\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codeName").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("路径不合法: ../evil.robot"));
    }

    @Test
    @DisplayName("403：无 CASE 权限时删除被拒，且服务层未被绕过")
    void 越权403() throws Exception {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权限：需要 CASE:DELETE"))
                .when(files).delete(anyLong(), anyString());

        mvc.perform(delete("/api/projects/7/files").param("path", "suites/login.robot"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codeName").value("FORBIDDEN"));
    }
}
