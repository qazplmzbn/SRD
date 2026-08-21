package com.vis4srd.model.controller;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.SecurityUtils;
import com.vis4srd.model.service.ModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型中心 API，对齐 Python {@code backend/src/routes/models.py}。
 * 字面路径（templates / ollama / providers / compare 等）优先于 {@code /{modelId}}。
 */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    // ===== 字面路径（必须在 {modelId} 之前声明） =====

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getModels(
            @RequestParam(required = false) String category,
            @RequestParam(name = "model_type", required = false) String modelType,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(modelService.getModels(category, modelType, status));
    }

    @GetMapping("/templates")
    public ApiResponse<List<Map<String, Object>>> getTemplates(
            @RequestParam(name = "task_type", required = false) String taskType,
            @RequestParam(name = "model_id", required = false) Long modelId) {
        return ApiResponse.ok(modelService.getTemplates(taskType, modelId));
    }

    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> compareModels(
            @RequestParam(name = "model_ids", required = false) String modelIds) {
        return ApiResponse.ok(modelService.compareModels(modelIds));
    }

    @GetMapping("/inference-history")
    public ApiResponse<Map<String, Object>> getInferenceHistory(
            @RequestParam(name = "model_id", required = false) String modelId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(modelService.getInferenceHistory(modelId, Math.min(Math.max(limit, 1), 100)));
    }

    @GetMapping("/ollama/status")
    public ApiResponse<Map<String, Object>> getOllamaStatus(
            @RequestParam(name = "base_url", required = false) String baseUrl) {
        return ApiResponse.ok(modelService.checkOllamaHealth(baseUrl));
    }

    @GetMapping("/ollama/models")
    public ApiResponse<Map<String, Object>> listOllamaModels(
            @RequestParam(name = "base_url", required = false) String baseUrl) {
        String url = StringUtils.hasText(baseUrl) ? baseUrl : ModelService.DEFAULT_OLLAMA_URL;
        Map<String, Object> result = modelService.listOllamaAvailableModels(url);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("models", result.getOrDefault("models", List.of()));
        data.put("base_url", modelService.normalizeOllamaBaseUrl(url));
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        if (ok) {
            return ApiResponse.ok(data);
        }
        Object error = result.get("error");
        return new ApiResponse<>(false, data, error == null ? null : String.valueOf(error));
    }

    @GetMapping("/ollama/configured")
    public ApiResponse<List<Map<String, Object>>> getConfiguredOllamaModels() {
        return ApiResponse.ok(modelService.getConfiguredOllamaModels());
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> getProviders() {
        return ApiResponse.ok(modelService.listProviders());
    }

    @GetMapping("/api/configured")
    public ApiResponse<List<Map<String, Object>>> getConfiguredApiModels() {
        return ApiResponse.ok(modelService.getConfiguredApiModels());
    }

    @PostMapping("/predict")
    public ApiResponse<Map<String, Object>> predict(@RequestBody Map<String, Object> body) {
        SecurityUtils.requireCurrentUser();
        return ApiResponse.ok(modelService.predict(body));
    }

    @PostMapping("/templates")
    public ApiResponse<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
        SecurityUtils.requireCurrentUser();
        return ApiResponse.ok(modelService.createTemplate(body));
    }

    @GetMapping("/templates/{templateId}")
    public ApiResponse<Map<String, Object>> getTemplate(@PathVariable long templateId) {
        Map<String, Object> template = modelService.getTemplateById(templateId);
        if (template == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模板不存在");
        }
        return ApiResponse.ok(template);
    }

    @PutMapping("/templates/{templateId}")
    public ApiResponse<Map<String, Object>> updateTemplate(
            @PathVariable long templateId,
            @RequestBody Map<String, Object> body) {
        SecurityUtils.requireCurrentUser();
        Map<String, Object> template = modelService.updateTemplate(templateId, body);
        if (template == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模板不存在");
        }
        return ApiResponse.ok(template);
    }

    @DeleteMapping("/templates/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable long templateId) {
        SecurityUtils.requireCurrentUser();
        if (!modelService.deleteTemplate(templateId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模板不存在或已删除");
        }
        return ApiResponse.ok(null, "模板已删除");
    }

    // ===== 模型 CRUD / 调用（{modelId}） =====

    @PostMapping
    public ApiResponse<Map<String, Object>> createModel(@RequestBody Map<String, Object> body) {
        SecurityUtils.requireCurrentUser();
        return ApiResponse.ok(modelService.createModel(body));
    }

    @GetMapping("/{modelId}")
    public ApiResponse<Map<String, Object>> getModel(@PathVariable long modelId) {
        Map<String, Object> model = modelService.getModelById(modelId);
        if (model == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        return ApiResponse.ok(model);
    }

    @PutMapping("/{modelId}")
    public ApiResponse<Map<String, Object>> updateModel(
            @PathVariable long modelId,
            @RequestBody Map<String, Object> body) {
        SecurityUtils.requireCurrentUser();
        return ApiResponse.ok(modelService.updateModel(modelId, body));
    }

    @DeleteMapping("/{modelId}")
    public ApiResponse<Void> deleteModel(@PathVariable long modelId) {
        SecurityUtils.requireCurrentUser();
        modelService.deleteModel(modelId);
        return ApiResponse.ok(null, "模型删除成功");
    }

    @PutMapping("/{modelId}/api-key")
    public ApiResponse<Map<String, Object>> updateApiKey(
            @PathVariable long modelId,
            @RequestBody Map<String, Object> keyData) {
        SecurityUtils.requireCurrentUser();
        Map<String, Object> updated = modelService.updateApiKey(modelId, keyData);
        Object keyObj = keyData.get("apiKey");
        if (keyObj == null) {
            keyObj = keyData.get("api_key");
        }
        String msg = (keyObj != null && StringUtils.hasText(String.valueOf(keyObj).trim()))
                ? "API Key 配置成功"
                : "API Key 已清除";
        return ApiResponse.ok(updated, msg);
    }

    @PostMapping("/{modelId}/test")
    public ApiResponse<Map<String, Object>> testModel(
            @PathVariable long modelId,
            @RequestBody(required = false) Object body) {
        SecurityUtils.requireCurrentUser();
        String testPrompt = "请只回复：OK";
        if (body instanceof String s && StringUtils.hasText(s)) {
            testPrompt = s;
        } else if (body instanceof Map<?, ?> map) {
            Object p = map.get("test_prompt");
            if (p == null) {
                p = map.get("prompt");
            }
            if (p != null && StringUtils.hasText(String.valueOf(p))) {
                testPrompt = String.valueOf(p);
            }
        }

        Map<String, Object> config = modelService.getModelForInference(modelId);
        if (config == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模型不存在或未激活");
        }

        String modelType = String.valueOf(config.get("model_type"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model_id", modelId);
        data.put("model_name", config.get("model_name"));
        data.put("model_type", modelType);

        Map<String, Object> result;
        if ("ollama".equals(modelType)) {
            result = modelService.callOllamaModel(config, testPrompt, "你是一个测试助手。");
            data.put("response", result.get("response"));
            data.put("error", result.get("error"));
            return new ApiResponse<>(Boolean.TRUE.equals(result.get("success")), data, null);
        }
        if ("api".equals(modelType)) {
            result = modelService.callApiModel(config, testPrompt, "你是一个测试助手。");
            data.put("provider", config.getOrDefault("provider", ""));
            data.put("response", result.get("response"));
            data.put("error", result.get("error"));
            return new ApiResponse<>(Boolean.TRUE.equals(result.get("success")), data, null);
        }

        data.put("response", null);
        data.put("error", "模型类型 " + modelType + " 暂不支持在线测试");
        return ApiResponse.ok(data);
    }

    @PostMapping("/{modelId}/call")
    public ApiResponse<Map<String, Object>> callModel(
            @PathVariable long modelId,
            @RequestBody Map<String, Object> inferenceData) {
        SecurityUtils.requireCurrentUser();

        Map<String, Object> config = modelService.getModelForInference(modelId);
        if (config == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模型不存在或未激活");
        }

        Object promptObj = inferenceData.get("prompt");
        if (promptObj == null || !StringUtils.hasText(String.valueOf(promptObj))) {
            throw new BusinessException("缺少必填参数: prompt");
        }
        String prompt = String.valueOf(promptObj);
        Object sys = inferenceData.get("system_prompt");
        if (sys == null) {
            sys = inferenceData.get("systemPrompt");
        }
        String systemPrompt = sys == null ? "你是一个有用的AI助手。" : String.valueOf(sys);

        String modelType = String.valueOf(config.get("model_type"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model_id", modelId);
        data.put("model_name", config.get("model_name"));
        data.put("model_type", modelType);

        Map<String, Object> result;
        if ("ollama".equals(modelType)) {
            result = modelService.callOllamaModel(config, prompt, systemPrompt);
        } else if ("api".equals(modelType)) {
            result = modelService.callApiModel(config, prompt, systemPrompt);
        } else {
            data.put("response", null);
            data.put("error", "模型类型 " + modelType + " 暂不支持在线推理");
            return new ApiResponse<>(false, data, null);
        }

        if (Boolean.TRUE.equals(result.get("success"))) {
            modelService.updateModelUsage(modelId, 100);
        }
        data.put("response", result.get("response"));
        data.put("error", result.get("error"));
        return new ApiResponse<>(Boolean.TRUE.equals(result.get("success")), data, null);
    }
}
