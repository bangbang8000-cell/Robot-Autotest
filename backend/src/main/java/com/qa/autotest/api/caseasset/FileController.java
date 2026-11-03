package com.qa.autotest.api.caseasset;

import com.qa.autotest.application.caseasset.FileWorkspaceService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.caseasset.FileNode;
import com.qa.autotest.domain.caseasset.RobotFile;
import com.qa.autotest.domain.caseasset.RobotFileVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文件工作区 6 端点（CAS-04）。 */
@RestController
@RequestMapping("/api/projects/{projectId}/files")
public class FileController {

    public record SaveFileRequest(String path, String content, String comment) {
    }

    public record MoveFileRequest(String fromPath, String toPath) {
    }

    private final FileWorkspaceService files;

    public FileController(FileWorkspaceService files) {
        this.files = files;
    }

    @GetMapping
    public ApiResponse<List<FileNode>> tree(@PathVariable long projectId) {
        return ApiResponse.ok(files.tree(projectId));
    }

    @GetMapping("/content")
    public ApiResponse<RobotFile> read(@PathVariable long projectId,
                                       @RequestParam String path) {
        return ApiResponse.ok(files.read(projectId, path));
    }

    @GetMapping("/versions")
    public ApiResponse<List<RobotFileVersion>> versions(@PathVariable long projectId,
                                                        @RequestParam String path) {
        return ApiResponse.ok(files.versions(projectId, path));
    }

    @PutMapping
    public ApiResponse<RobotFile> save(@PathVariable long projectId,
                                       @RequestBody SaveFileRequest request) {
        return ApiResponse.ok(files.save(projectId, request.path(), request.content(),
                request.comment()));
    }

    @PostMapping("/upload")
    public ApiResponse<RobotFile> upload(@PathVariable long projectId,
                                         @RequestParam String path,
                                         @RequestParam(required = false) String comment,
                                         @RequestPart("file") MultipartFile file)
            throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ApiResponse.ok(files.save(projectId, path, content, comment));
    }

    @PostMapping("/move")
    public ApiResponse<RobotFile> move(@PathVariable long projectId,
                                       @RequestBody MoveFileRequest request) {
        return ApiResponse.ok(files.move(projectId, request.fromPath(), request.toPath()));
    }

    @DeleteMapping
    public ApiResponse<Void> delete(@PathVariable long projectId, @RequestParam String path) {
        files.delete(projectId, path);
        return ApiResponse.ok(null);
    }
}
