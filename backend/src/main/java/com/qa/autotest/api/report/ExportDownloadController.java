package com.qa.autotest.api.report;

import com.qa.autotest.application.report.ExportService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出文件下载（RPT-05）：按 exportId + projectId 校验归属后流式返回。
 */
@RestController
@RequestMapping("/api/exports")
public class ExportDownloadController {

    private final ExportService exports;

    public ExportDownloadController(ExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/{exportId}/download")
    public ResponseEntity<Resource> download(@PathVariable long exportId,
                                             @RequestParam long projectId) throws java.io.IOException {
        ExportService.ExportDownload download = exports.download(projectId, exportId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentLength(Files.size(download.path()))
                .body(new FileSystemResource(download.path()));
    }
}
