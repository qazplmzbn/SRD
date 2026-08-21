package com.vis4srd.upload.controller;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.common.security.SecurityUtils;
import com.vis4srd.upload.service.ArchiveImportService;
import com.vis4srd.upload.service.ArchiveTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadArchiveController {

    private final ArchiveTemplateService templateService;
    private final ArchiveImportService importService;

    @GetMapping("/archive/templates")
    public ApiResponse<Map<String, Object>> getArchiveTemplates() {
        return ApiResponse.ok(templateService.buildTemplateResponse());
    }

    @GetMapping("/archive/templates/{dataSource}/{templateType}")
    public ResponseEntity<Resource> downloadArchiveTemplate(
            @PathVariable String dataSource,
            @PathVariable String templateType) {
        ArchiveTemplateService.TemplateFile file = templateService.resolveTemplateFile(dataSource, templateType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.resource());
    }

    @PostMapping(value = "/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadArchive(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "data_source", defaultValue = "custom") String dataSource) {
        requireAdmin();
        return ApiResponse.ok(importService.upload(file, dataSource));
    }

    @PostMapping("/archive/confirm")
    public ApiResponse<Map<String, Object>> confirmArchiveImport(@RequestBody Map<String, Object> importData) {
        requireAdmin();
        return ApiResponse.ok(importService.confirm(importData));
    }

    @GetMapping("/archive/datasets")
    public ApiResponse<List<Map<String, Object>>> listUploadedDatasets() {
        return ApiResponse.ok(importService.listUploadedDatasets());
    }

    @DeleteMapping("/archive/datasets/{filename}")
    public ApiResponse<Void> deleteUploadedDataset(@PathVariable String filename) {
        requireAdmin();
        importService.deleteUploadedDataset(filename);
        return ApiResponse.ok(null, "文件已删除");
    }

    private void requireAdmin() {
        AuthUser user = SecurityUtils.requireCurrentUser();
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }
}
