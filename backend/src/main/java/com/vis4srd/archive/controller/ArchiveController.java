package com.vis4srd.archive.controller;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.common.security.SecurityUtils;
import com.vis4srd.archive.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getUsers(
            @RequestParam(required = false) String dataset,
            @RequestParam(name = "risk_level", required = false) String riskLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(archiveService.getUsers(dataset, riskLevel, keyword, status, page, pageSize));
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        requireAdmin();
        @SuppressWarnings("unchecked")
        List<String> userIds = body.get("user_ids") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        int deleted = archiveService.deleteUsers(userIds);
        return ApiResponse.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/{userHash}")
    public ApiResponse<Map<String, Object>> getUserDetail(
            @PathVariable String userHash,
            @RequestParam(required = false) String dataset) {
        return ApiResponse.ok(archiveService.getUserDetail(userHash, dataset));
    }

    @DeleteMapping("/{userHash}")
    public ApiResponse<Map<String, Object>> deleteUser(@PathVariable String userHash) {
        requireAdmin();
        int deleted = archiveService.deleteUser(userHash);
        return ApiResponse.ok(Map.of("deleted", deleted));
    }

    private void requireAdmin() {
        AuthUser user = SecurityUtils.requireCurrentUser();
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }
}
