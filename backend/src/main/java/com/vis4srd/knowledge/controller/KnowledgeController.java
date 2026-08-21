package com.vis4srd.knowledge.controller;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.common.security.SecurityUtils;
import com.vis4srd.knowledge.service.KnowledgeService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/api/knowledge/topics")
    public ApiResponse<Map<String, Object>> getTopics() {
        return ApiResponse.ok(knowledgeService.getTopics());
    }

    @GetMapping("/api/knowledge/sub-topics")
    public ApiResponse<List<Map<String, Object>>> getSubTopics(
            @RequestParam(name = "topic_id", required = false) String topicId) {
        return ApiResponse.ok(knowledgeService.getSubTopics(parseIntOrNull(topicId)));
    }

    @GetMapping("/api/knowledge/documents")
    public ApiResponse<Map<String, Object>> getDocuments(
            @RequestParam(name = "topic_id", required = false) String topicId,
            @RequestParam(name = "sub_topic_id", required = false) String subTopicId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        return ApiResponse.ok(knowledgeService.getDocuments(
                parseIntOrNull(topicId),
                parseIntOrNull(subTopicId),
                keyword,
                format,
                status,
                page,
                pageSize));
    }

    @GetMapping("/api/knowledge/documents/{docId}")
    public ApiResponse<Map<String, Object>> getDocumentDetail(@PathVariable String docId) {
        return ApiResponse.ok(knowledgeService.getDocumentDetail(docId));
    }

    @GetMapping("/api/knowledge/documents/{docId}/preview")
    public ApiResponse<Map<String, Object>> getDocumentPreview(
            @PathVariable String docId,
            @RequestParam(name = "max_length", defaultValue = "500") int maxLength) {
        return ApiResponse.ok(knowledgeService.getDocumentPreview(docId, maxLength));
    }

    @GetMapping("/api/knowledge/documents/{docId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String docId) {
        return toFileResponse(knowledgeService.loadDocumentFile(docId, false));
    }

    @GetMapping("/api/knowledge/documents/{docId}/preview-stream")
    public ResponseEntity<Resource> previewStream(@PathVariable String docId) {
        return toFileResponse(knowledgeService.loadDocumentFile(docId, true));
    }

    @GetMapping("/api/knowledge/documents/{docId}/base64")
    public ApiResponse<Map<String, Object>> getDocumentBase64(@PathVariable String docId) {
        return ApiResponse.ok(knowledgeService.getDocumentBase64(docId));
    }

    @DeleteMapping("/api/knowledge/documents/{docId}")
    public ApiResponse<Void> deleteDocument(@PathVariable long docId) {
        requireAdmin();
        boolean ok = knowledgeService.deleteDocument(docId);
        if (!ok) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在或已删除");
        }
        return ApiResponse.ok(null, "文档已删除");
    }

    @PutMapping("/api/knowledge/documents/{docId}")
    public ApiResponse<Map<String, Object>> updateDocument(
            @PathVariable long docId,
            @RequestBody Map<String, Object> body) {
        requireAdmin();
        return ApiResponse.ok(knowledgeService.updateDocument(docId, body), "文档更新成功");
    }

    @PostMapping("/api/knowledge/documents")
    public ApiResponse<Map<String, Object>> createDocument(@RequestBody Map<String, Object> body) {
        AuthUser user = requireAdmin();
        return ApiResponse.ok(
                knowledgeService.createDocument(body, user.getUsername()),
                "文档创建成功");
    }

    @PostMapping(value = "/api/upload/knowledge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("topic_id") int topicId,
            @RequestParam(name = "summary", required = false) String summary,
            @RequestParam(name = "sub_topic_id", required = false) Integer subTopicId,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String format) {
        AuthUser user = requireAdmin();
        Map<String, Object> data = knowledgeService.uploadDocument(
                file, title, topicId, subTopicId, summary, keywords, format, user.getUsername());
        return ApiResponse.ok(data, "上传成功");
    }

    @GetMapping("/api/knowledge/keywords")
    public ApiResponse<List<Map<String, Object>>> getKnowledgeKeywords(
            @RequestParam(name = "topic_id", required = false) String topicId,
            @RequestParam(name = "is_hot", defaultValue = "false") boolean isHot) {
        return ApiResponse.ok(knowledgeService.getKeywords(parseIntOrNull(topicId), isHot));
    }

    /** 对齐 Python system.py：GET /api/keywords */
    @GetMapping("/api/keywords")
    public ApiResponse<List<Map<String, Object>>> getSystemKeywords(
            @RequestParam(name = "is_hot", required = false) Boolean isHot) {
        return ApiResponse.ok(knowledgeService.getSystemKeywords(isHot));
    }

    @PostMapping("/api/knowledge/search")
    public ApiResponse<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query"));
        int topK = 5;
        Object top = body.get("top_k") != null ? body.get("top_k") : body.get("topK");
        if (top instanceof Number n) {
            topK = n.intValue();
        } else if (top != null) {
            try {
                topK = Integer.parseInt(String.valueOf(top));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return ApiResponse.ok(knowledgeService.searchDocuments(query, topK));
    }

    @GetMapping("/api/knowledge/related")
    public ApiResponse<Map<String, Object>> getRelated(
            @RequestParam(name = "user_hash", required = false) String userHash,
            @RequestParam(name = "risk_level", required = false) String riskLevel) {
        return ApiResponse.ok(knowledgeService.getRelated(riskLevel));
    }

    private ResponseEntity<Resource> toFileResponse(KnowledgeService.ResourceFile file) {
        String dispositionType = file.inline() ? "inline" : "attachment";
        String encodedName = new String(file.fileName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + encodedName + "\"")
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.resource());
    }

    private AuthUser requireAdmin() {
        AuthUser user = SecurityUtils.requireCurrentUser();
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return user;
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
