package com.vis4srd.scale.controller;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.scale.catalog.ScaleCatalog;
import com.vis4srd.scale.service.ScaleService;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scale")
@RequiredArgsConstructor
public class ScaleController {

    private final ScaleService scaleService;
    private final ScaleCatalog scaleCatalog;

    @GetMapping("/definitions")
    public ApiResponse<List<Map<String, Object>>> getDefinitions() {
        return ApiResponse.ok(scaleCatalog.getAllScaleDefinitions());
    }

    @GetMapping("/definitions/{scaleCode}")
    public ApiResponse<Map<String, Object>> getDefinition(@PathVariable String scaleCode) {
        Map<String, Object> definition = scaleCatalog.getScaleDefinition(scaleCode);
        if (definition == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "量表不存在");
        }
        return ApiResponse.ok(definition);
    }

    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> getTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(name = "user_hash", required = false) String userHash,
            @RequestParam(name = "archive_id", required = false) Integer archiveId,
            @RequestParam(name = "data_source", required = false) String dataSource) {
        return ApiResponse.ok(scaleService.getTasks(status, userHash, archiveId, dataSource, page, limit));
    }

    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        String scaleId = String.valueOf(body.getOrDefault("scaleId", ""));
        String userHash = String.valueOf(body.getOrDefault("userHash", ""));
        Integer archiveId = body.get("archiveId") instanceof Number n ? n.intValue() : null;
        String dataSource = body.get("dataSource") == null ? null : String.valueOf(body.get("dataSource"));
        String taskName = body.get("taskName") == null ? null : String.valueOf(body.get("taskName"));

        String normalized = scaleCatalog.normalizeScaleCode(scaleId);
        Map<String, Object> meta = scaleCatalog.getScaleMeta(normalized);
        if (meta == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "量表 '" + scaleId + "' 不存在");
        }

        Map<String, Object> archive = scaleService.findArchiveUser(userHash, archiveId, dataSource);
        if (archive == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "未在心理档案中找到对应用户，请先确认数据源和用户");
        }

        String sourceLabel = firstNonBlank(
                archive.get("dataset_display_name"),
                archive.get("dataset_source"),
                dataSource,
                "unknown");
        String category = String.valueOf(meta.getOrDefault("category", ""));
        String color = scaleColor(category);

        Map<String, Object> taskData = new LinkedHashMap<>();
        taskData.put("task_name", firstNonBlank(taskName, meta.get("name") + "评估任务_" + archive.get("user_id")));
        taskData.put("user_id", archive.get("id"));
        taskData.put("user_hash", archive.get("user_id"));
        taskData.put("user_alias", archive.get("user_id"));
        taskData.put("archive_id", archive.get("id"));
        taskData.put("data_source", archive.get("dataset_source"));
        taskData.put("data_source_label", sourceLabel);
        taskData.put("scale_id", null);
        taskData.put("scale_code", meta.get("code"));
        taskData.put("scale_name", meta.get("name"));
        taskData.put("scale_full_name", meta.get("full_name"));
        taskData.put("scale_category", category);
        taskData.put("scale_color", color);
        taskData.put("scale_bg_color", color);
        taskData.put("total_questions", meta.get("question_count"));

        long taskId = scaleService.createTask(taskData);
        Map<String, Object> created = scaleService.getTaskById(taskId);
        return ApiResponse.ok(scaleService.toResponse(created));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable long taskId) {
        Map<String, Object> task = scaleService.getTaskById(taskId);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return ApiResponse.ok(scaleService.toResponse(task));
    }

    @PostMapping("/tasks/{taskId}/submit")
    public ApiResponse<Map<String, Object>> submit(
            @PathVariable long taskId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> task = scaleService.getTaskById(taskId);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        if ("completed".equals(String.valueOf(task.get("status")))) {
            throw new BusinessException("该任务已完成，不能重复提交");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answers = body.get("answers") instanceof List<?> list
                ? list.stream().map(item -> {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> a = new HashMap<>();
                        a.put("qId", m.get("qId"));
                        a.put("score", m.get("score"));
                        return a;
                    }
                    return Map.<String, Object>of();
                }).toList()
                : List.of();

        Map<String, Object> evaluation = scaleCatalog.evaluateScaleAnswers(
                String.valueOf(task.getOrDefault("scale_code", "PHQ-9")),
                answers);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> normalizedAnswers =
                (List<Map<String, Object>>) evaluation.get("normalizedAnswers");
        int totalScore = evaluation.get("totalScore") instanceof Number n ? n.intValue() : 0;
        String riskLevel = String.valueOf(evaluation.get("riskLevel"));
        Object assessmentResult = evaluation.get("assessmentResult");

        scaleService.submitTask(taskId, normalizedAnswers, totalScore, riskLevel, assessmentResult);
        Map<String, Object> updated = scaleService.getTaskById(taskId);
        return ApiResponse.ok(scaleService.toResponse(updated));
    }

    @DeleteMapping("/tasks/{taskId}")
    public Map<String, Object> deleteTask(@PathVariable long taskId) {
        if (!scaleService.deleteTask(taskId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "删除成功");
        return body;
    }

    private static String scaleColor(String category) {
        return switch (category == null ? "" : category) {
            case "suicide" -> "#ef4444";
            case "depression" -> "#8b5cf6";
            case "anxiety" -> "#3b82f6";
            case "hopelessness" -> "#6366f1";
            case "sleep" -> "#0f766e";
            default -> "#3b82f6";
        };
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v != null && String.valueOf(v).isBlank() == false && !"null".equals(String.valueOf(v))) {
                return String.valueOf(v);
            }
        }
        return "";
    }
}
