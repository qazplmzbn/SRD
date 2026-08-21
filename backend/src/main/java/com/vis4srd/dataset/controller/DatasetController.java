package com.vis4srd.dataset.controller;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.common.security.SecurityUtils;
import com.vis4srd.dataset.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getDatasets() {
        return ApiResponse.ok(datasetService.getDatasets());
    }

    @GetMapping("/external")
    public ApiResponse<List<Map<String, Object>>> getExternalDatasets() {
        return ApiResponse.ok(datasetService.getExternalDatasets());
    }

    @GetMapping("/compare")
    public ApiResponse<List<Map<String, Object>>> getDatasetsCompare() {
        return ApiResponse.ok(datasetService.getDatasetsCompare());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getDatasetsStats() {
        return ApiResponse.ok(datasetService.getDatasetsStats());
    }

    @GetMapping("/csv/{datasetKey}")
    public void csvDisabled(@PathVariable String datasetKey) {
        throw new BusinessException(HttpStatus.GONE, "系统已切换为纯数据库模式，CSV 直连接口已停用");
    }

    @GetMapping("/{datasetKey}/archives")
    public ApiResponse<Map<String, Object>> getArchives(
            @PathVariable String datasetKey,
            @RequestParam(required = false) String dataset,
            @RequestParam(name = "risk_level", required = false) String riskLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        String key = dataset != null && !dataset.isBlank() ? dataset : datasetKey;
        return ApiResponse.ok(datasetService.getArchivesPage(key, riskLevel, page, pageSize));
    }

    @GetMapping("/{datasetKey}/posts")
    public ApiResponse<Map<String, Object>> getPosts(
            @PathVariable String datasetKey,
            @RequestParam(name = "user_hash") String userHash,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
        return ApiResponse.ok(datasetService.getUserPosts(datasetKey, userHash, page, pageSize));
    }

    @GetMapping("/{datasetKey}/keywords")
    public ApiResponse<Map<String, Object>> getKeywords(
            @PathVariable String datasetKey,
            @RequestParam(name = "user_hash") String userHash,
            @RequestParam(name = "top_n", defaultValue = "8") int topN) {
        return ApiResponse.ok(datasetService.getUserKeywords(datasetKey, userHash, topN));
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam(name = "mode_type", defaultValue = "all") String modeType) throws Exception {
        requireAdmin();
        return ApiResponse.ok(datasetService.uploadDataset(
                file.getBytes(), file.getOriginalFilename(), name, modeType));
    }

    @PostMapping("/builtin/sync")
    public ApiResponse<Map<String, Object>> syncBuiltin(
            @RequestBody(required = false) Map<String, Object> payload) {
        requireAdmin();
        List<String> keys = null;
        if (payload != null) {
            Object v = payload.get("datasetKeys");
            if (v == null) {
                v = payload.get("dataset_keys");
            }
            if (v instanceof List<?> list) {
                keys = list.stream().map(String::valueOf).toList();
            }
        }
        return ApiResponse.ok(datasetService.syncBuiltinDatasets(keys));
    }

    private void requireAdmin() {
        AuthUser user = SecurityUtils.requireCurrentUser();
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }
}
