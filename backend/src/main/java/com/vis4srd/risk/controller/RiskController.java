package com.vis4srd.risk.controller;

import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java 风险检测 API，对齐 Python {@code backend/src/routes/risk.py} 与前端约定。
 */
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    // ===== 静态 =====

    @GetMapping("/task-types")
    public ApiResponse<List<Map<String, Object>>> getTaskTypes() {
        return ApiResponse.ok(riskService.getTaskTypes());
    }

    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> getRiskModels() {
        return ApiResponse.ok(riskService.getRiskModels());
    }

    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> getCompare() {
        return ApiResponse.ok(riskService.getCompareData());
    }

    // ===== 通用任务（字面路径优先于 {taskId}） =====

    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(riskService.createTask(body == null ? Map.of() : body));
    }

    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(name = "data_source", required = false) String dataSource) {
        return ApiResponse.ok(riskService.listTasks(page, limit, status, dataSource));
    }

    @GetMapping("/tasks/{taskId}/report")
    public ApiResponse<Map<String, Object>> getReport(@PathVariable String taskId) {
        return ApiResponse.ok(riskService.getReport(taskId));
    }

    @GetMapping("/tasks/{taskId}/export-report")
    public ResponseEntity<byte[]> exportReport(@PathVariable String taskId) {
        String html = riskService.exportReportHtml(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=utf-8")
                .body(html.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/tasks/{taskId}/execute")
    public ApiResponse<Map<String, Object>> executeTask(@PathVariable String taskId) {
        return ApiResponse.ok(riskService.executeTask(taskId), "任务执行成功");
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String taskId) {
        return ApiResponse.ok(riskService.getTask(taskId));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(@PathVariable String taskId) {
        riskService.deleteTask(taskId);
        return ApiResponse.ok(null, "任务删除成功");
    }

    // ===== Emocc =====

    @PostMapping("/emocc-tasks")
    public ApiResponse<Map<String, Object>> createEmoccTask(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(riskService.createEmoccTask(body == null ? Map.of() : body));
    }

    @GetMapping("/emocc-tasks")
    public ApiResponse<Map<String, Object>> listEmoccTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(riskService.listEmoccTasks(page, limit));
    }

    @GetMapping("/emocc-tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getEmoccTask(@PathVariable String taskId) {
        return ApiResponse.ok(riskService.getEmoccTask(taskId));
    }

    @DeleteMapping("/emocc-tasks/{taskId}")
    public ApiResponse<Void> deleteEmoccTask(@PathVariable String taskId) {
        riskService.deleteEmoccTask(taskId);
        return ApiResponse.ok(null, "Emocc任务删除成功");
    }

    @PostMapping("/emocc-tasks/{taskId}/execute")
    public ApiResponse<Map<String, Object>> executeEmoccTask(@PathVariable String taskId) {
        return ApiResponse.ok(riskService.executeEmoccTask(taskId), "任务执行成功");
    }

    @GetMapping("/emocc-models")
    public ApiResponse<Map<String, Object>> getEmoccModels() {
        return ApiResponse.ok(riskService.getEmoccModels());
    }

    // ===== FeaLearner =====

    @PostMapping("/fealearner-tasks")
    public ApiResponse<Map<String, Object>> createFealearnerTask(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(riskService.createFealearnerTask(body == null ? Map.of() : body));
    }

    @GetMapping("/fealearner-tasks")
    public ApiResponse<Map<String, Object>> listFealearnerTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(riskService.listFealearnerTasks(page, limit));
    }

    @GetMapping("/fealearner-tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getFealearnerTask(@PathVariable String taskId) {
        return ApiResponse.ok(riskService.getFealearnerTask(taskId));
    }

    @DeleteMapping("/fealearner-tasks/{taskId}")
    public ApiResponse<Void> deleteFealearnerTask(@PathVariable String taskId) {
        riskService.deleteFealearnerTask(taskId);
        return ApiResponse.ok(null, "删除成功");
    }

    @PostMapping("/fealearner-tasks/{taskId}/execute")
    public ApiResponse<Map<String, Object>> executeFealearnerTask(@PathVariable String taskId) {
        return ApiResponse.ok(riskService.executeFealearnerTask(taskId), "任务执行成功");
    }

    @GetMapping("/fealearner-models")
    public ApiResponse<Map<String, Object>> getFealearnerModels() {
        return ApiResponse.ok(riskService.getFealearnerModels());
    }

    // ===== Predict =====

    @GetMapping("/predict/{userHash}")
    public ApiResponse<Map<String, Object>> predict(
            @PathVariable String userHash,
            @RequestParam(name = "data_source", required = false) String dataSource) {
        return ApiResponse.ok(riskService.predict(userHash, dataSource));
    }
}
