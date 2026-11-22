package com.vis4srd.model.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ModelService {

    /** 与前端 / 本地开发约定一致的 Ollama 默认地址。 */
    public static final String DEFAULT_OLLAMA_URL = "http://127.0.0.1:11434";
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    public ModelService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    // ======================== Models ========================

    public List<Map<String, Object>> getModels(String category, String modelType, String status) {
        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT id, model_name, model_code, model_category, model_type,
                           provider, api_key, api_base_url, model_file_path,
                           supported_datasets, config_template, description,
                           performance_metrics, is_available, is_builtin, status,
                           ollama_model_name, ollama_base_url, detection_type,
                           embedding_file_path, version, is_default,
                           usage_count, last_used_at, avg_processing_time_ms,
                           created_at, updated_at
                    FROM models
                    WHERE 1=1
                    """);
            List<Object> params = new ArrayList<>();
            if (StringUtils.hasText(category)) {
                sql.append(" AND model_category = ?");
                params.add(category);
            }
            if (StringUtils.hasText(modelType)) {
                sql.append(" AND model_type = ?");
                params.add(modelType);
            }
            if (StringUtils.hasText(status)) {
                sql.append(" AND status = ?");
                params.add(status);
            }
            sql.append(" ORDER BY id DESC");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(convertModelToCamel(row));
            }
            return result;
        } catch (Exception ex) {
            log.warn("获取模型列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getModelById(long modelId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM models WHERE id = ?", modelId);
            if (rows.isEmpty()) {
                return null;
            }
            return convertModelToCamel(rows.get(0));
        } catch (Exception ex) {
            log.warn("获取模型详情失败 id={}: {}", modelId, ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> getModelRawById(long modelId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM models WHERE id = ?", modelId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            log.warn("读取模型原始行失败 id={}: {}", modelId, ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> createModel(Map<String, Object> body) {
        Map<String, Object> db = new LinkedHashMap<>();
        putIfPresent(db, "model_name", first(body, "modelName", "model_name"));
        Object code = first(body, "modelCode", "model_code");
        db.put("model_code", code != null ? String.valueOf(code) : buildModelCode(body));
        putIfPresent(db, "model_category", first(body, "modelCategory", "model_category"));
        putIfPresent(db, "model_type", first(body, "modelType", "model_type"));
        putIfPresent(db, "provider", body.get("provider"));
        putIfPresent(db, "api_key", first(body, "apiKey", "api_key"));
        putIfPresent(db, "api_base_url", first(body, "apiBaseUrl", "api_base_url"));
        putIfPresent(db, "config_template", first(body, "configTemplate", "config_template"));
        putIfPresent(db, "ollama_model_name", first(body, "ollamaModelName", "ollama_model_name"));
        putIfPresent(db, "ollama_base_url", first(body, "ollamaBaseUrl", "ollama_base_url"));
        putIfPresent(db, "model_path", first(body, "modelPath", "model_path"));
        putIfPresent(db, "lora_path", first(body, "loraPath", "lora_path"));
        putIfPresent(db, "detection_type", first(body, "detectionType", "detection_type"));
        putIfPresent(db, "model_file_path", first(body, "modelFilePath", "model_file_path"));
        putIfPresent(db, "embedding_file_path", first(body, "embeddingFilePath", "embedding_file_path"));
        Object datasets = first(body, "supportedDatasets", "supported_datasets");
        if (datasets != null) {
            db.put("supported_datasets", toJsonString(datasets));
        }
        putIfPresent(db, "description", body.get("description"));
        Object available = first(body, "isAvailable", "is_available");
        db.put("is_available", available == null || toBool(available));
        db.put("status", body.get("status") != null ? body.get("status") : "active");

        if (db.get("model_name") == null) {
            throw new BusinessException("缺少必填字段: modelName");
        }

        String columns = String.join(", ", db.keySet());
        String placeholders = String.join(", ", db.keySet().stream().map(k -> "?").toList());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO models (" + columns + ") VALUES (" + placeholders + ")",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            for (Object v : db.values()) {
                ps.setObject(i++, v);
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建模型失败");
        }
        Map<String, Object> created = getModelById(key.longValue());
        if (created == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建模型失败");
        }
        return created;
    }

    public Map<String, Object> updateModel(long modelId, Map<String, Object> body) {
        Map<String, Object> db = new LinkedHashMap<>();
        copyIfKey(body, "modelName", db, "model_name");
        copyIfKey(body, "modelCode", db, "model_code");
        copyIfKey(body, "provider", db, "provider");
        if (StringUtils.hasText(asString(body.get("apiKey")))) {
            db.put("api_key", asString(body.get("apiKey")).trim());
        }
        copyIfKey(body, "apiBaseUrl", db, "api_base_url");
        copyIfKey(body, "configTemplate", db, "config_template");
        copyIfKey(body, "ollamaModelName", db, "ollama_model_name");
        copyIfKey(body, "ollamaBaseUrl", db, "ollama_base_url");
        copyIfKey(body, "modelPath", db, "model_path");
        copyIfKey(body, "loraPath", db, "lora_path");
        copyIfKey(body, "detectionType", db, "detection_type");
        copyIfKey(body, "modelFilePath", db, "model_file_path");
        copyIfKey(body, "modelCategory", db, "model_category");
        copyIfKey(body, "modelType", db, "model_type");
        copyIfKey(body, "embeddingFilePath", db, "embedding_file_path");
        if (body.containsKey("supportedDatasets")) {
            db.put("supported_datasets", toJsonString(body.get("supportedDatasets")));
        }
        copyIfKey(body, "description", db, "description");
        if (body.containsKey("isAvailable")) {
            db.put("is_available", toBool(body.get("isAvailable")));
        }
        copyIfKey(body, "status", db, "status");

        if (db.isEmpty()) {
            throw new BusinessException("没有需要更新的字段");
        }

        String setClause = String.join(", ", db.keySet().stream().map(k -> k + " = ?").toList());
        List<Object> params = new ArrayList<>(db.values());
        params.add(modelId);
        String guard = "";
        if (db.containsKey("api_base_url") && !db.containsKey("api_key")) {
            // Never reuse a stored credential at a different endpoint, even during concurrent edits.
            guard = " AND (api_key IS NULL OR api_key = '' OR BINARY api_base_url <=> BINARY ?)";
            params.add(db.get("api_base_url"));
        }
        int updated = jdbcTemplate.update("UPDATE models SET " + setClause + " WHERE id = ?" + guard, params.toArray());
        if (updated == 0) {
            if (!guard.isEmpty() && getModelById(modelId) != null) {
                throw new BusinessException("修改服务地址时请重新填写 API Key");
            }
            throw new BusinessException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        Map<String, Object> model = getModelById(modelId);
        if (model == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        return model;
    }

    public void deleteModel(long modelId) {
        Map<String, Object> raw = getModelRawById(modelId);
        if (raw == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        if (toBool(raw.get("is_builtin"))) {
            String name = String.valueOf(raw.getOrDefault("model_name", "内置模型"));
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "「" + name + "」是系统预置模型，禁止删除。");
        }
        jdbcTemplate.update("DELETE FROM models WHERE id = ?", modelId);
    }

    public Map<String, Object> updateApiKey(long modelId, Map<String, Object> keyData) {
        Map<String, Object> raw = getModelRawById(modelId);
        if (raw == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        if (!"api".equals(String.valueOf(raw.get("model_type")))) {
            throw new BusinessException("仅 API 模型支持配置 API Key");
        }
        Object keyObj = first(keyData, "apiKey", "api_key");
        String apiKey = keyObj == null ? "" : String.valueOf(keyObj).trim();
        String status = StringUtils.hasText(apiKey) ? "active" : "inactive";
        jdbcTemplate.update(
                "UPDATE models SET api_key = ?, status = ? WHERE id = ?",
                apiKey, status, modelId);
        return getModelById(modelId);
    }

    // ======================== Templates ========================

    public List<Map<String, Object>> getTemplates(String taskType, Long modelId) {
        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT id, name, task_type, description, prompt_content,
                           variables, model_id, is_active, usage_count, created_at, updated_at
                    FROM prompt_templates
                    WHERE is_active = TRUE
                    """);
            List<Object> params = new ArrayList<>();
            if (StringUtils.hasText(taskType)) {
                sql.append(" AND task_type = ?");
                params.add(taskType);
            }
            if (modelId != null) {
                sql.append(" AND model_id = ?");
                params.add(modelId);
            }
            sql.append(" ORDER BY usage_count DESC, id");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(convertTemplateToCamel(row));
            }
            return result;
        } catch (Exception ex) {
            log.warn("获取模板列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getTemplateById(long templateId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM prompt_templates WHERE id = ?", templateId);
            if (rows.isEmpty()) {
                return null;
            }
            return convertTemplateToCamel(rows.get(0));
        } catch (Exception ex) {
            log.warn("获取模板详情失败 id={}: {}", templateId, ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> createTemplate(Map<String, Object> body) {
        Map<String, Object> data = normalizeTemplatePayload(body);
        String name = asString(data.get("name"));
        String taskType = asString(first(data, "task_type", "taskType"));
        if (!StringUtils.hasText(name) || !StringUtils.hasText(taskType)) {
            throw new BusinessException("缺少必填字段: name / task_type");
        }
        String promptContent = asString(first(data, "prompt_content", "promptContent"));
        Object isActive = first(data, "is_active", "isActive");
        boolean active = isActive == null || toBool(isActive);

        List<String> columns = new ArrayList<>(List.of("name", "task_type", "prompt_content", "is_active"));
        List<Object> values = new ArrayList<>(List.of(name, taskType, promptContent == null ? "" : promptContent, active));
        Object description = data.get("description");
        if (description != null) {
            columns.add("description");
            values.add(description);
        }
        Object mid = first(data, "model_id", "modelId");
        if (mid != null) {
            columns.add("model_id");
            values.add(mid);
        }

        String colSql = String.join(", ", columns);
        String ph = String.join(", ", columns.stream().map(c -> "?").toList());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO prompt_templates (" + colSql + ") VALUES (" + ph + ")",
                    Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建模板失败");
        }
        Map<String, Object> created = getTemplateById(key.longValue());
        if (created == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建模板失败");
        }
        return created;
    }

    public Map<String, Object> updateTemplate(long templateId, Map<String, Object> body) {
        Map<String, Object> data = normalizeTemplatePayload(body);
        Map<String, Object> db = new LinkedHashMap<>();
        if (data.containsKey("name") || body.containsKey("name")) {
            db.put("name", first(data, "name"));
        }
        if (body.containsKey("taskType") || data.containsKey("task_type")) {
            db.put("task_type", first(data, "task_type", "taskType"));
        }
        if (body.containsKey("promptContent") || data.containsKey("prompt_content")) {
            db.put("prompt_content", first(data, "prompt_content", "promptContent"));
        }
        if (body.containsKey("description") || data.containsKey("description")) {
            db.put("description", data.get("description"));
        }
        if (body.containsKey("modelId") || data.containsKey("model_id")) {
            db.put("model_id", first(data, "model_id", "modelId"));
        }
        if (body.containsKey("isActive") || data.containsKey("is_active")) {
            db.put("is_active", toBool(first(data, "is_active", "isActive")));
        }

        if (db.isEmpty()) {
            return getTemplateById(templateId);
        }

        String setClause = String.join(", ", db.keySet().stream().map(k -> k + " = ?").toList());
        List<Object> params = new ArrayList<>(db.values());
        params.add(templateId);
        int updated = jdbcTemplate.update(
                "UPDATE prompt_templates SET " + setClause + " WHERE id = ?", params.toArray());
        if (updated == 0) {
            return null;
        }
        return getTemplateById(templateId);
    }

    public boolean deleteTemplate(long templateId) {
        int n = jdbcTemplate.update("DELETE FROM prompt_templates WHERE id = ?", templateId);
        return n > 0;
    }

    // ======================== Compare / history / predict ========================

    public Map<String, Object> compareModels(String modelIdsCsv) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.hasText(modelIdsCsv)) {
            for (String part : modelIdsCsv.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    ids.add(Long.parseLong(trimmed));
                }
            }
        } else {
            for (Map<String, Object> m : getModels("detection", null, null)) {
                Object id = m.get("id");
                if (id instanceof Number n) {
                    ids.add(n.longValue());
                }
            }
        }

        List<Map<String, Object>> modelsData = new ArrayList<>();
        for (Long mid : ids) {
            Map<String, Object> raw = getModelRawById(mid);
            if (raw == null) {
                continue;
            }
            Object metricsObj = raw.get("performance_metrics");
            Map<String, Object> metrics = parseJsonMap(metricsObj);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", raw.get("id"));
            item.put("name", raw.get("model_name"));
            item.put("metrics", metrics == null ? Map.of() : metrics);
            modelsData.add(item);
        }

        List<String> dimensions = List.of("准确率", "精确率", "召回率", "F1分数", "AUC");
        List<Map<String, Object>> radarModels = new ArrayList<>();
        for (Map<String, Object> m : modelsData) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = m.get("metrics") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            Map<String, Object> radar = new LinkedHashMap<>();
            radar.put("name", m.get("name"));
            radar.put("values", List.of(
                    metricValue(metrics, "accuracy"),
                    metricValue(metrics, "precision"),
                    metricValue(metrics, "recall"),
                    metricValue(metrics, "f1"),
                    metricValue(metrics, "auc")
            ));
            radarModels.add(radar);
        }

        Map<String, Object> radarData = new LinkedHashMap<>();
        radarData.put("dimensions", dimensions);
        radarData.put("models", radarModels);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("models", modelsData);
        data.put("radar_data", radarData);
        return data;
    }

    public Map<String, Object> getInferenceHistory(String modelId, int limit) {
        int n = Math.min(Math.max(limit, 1), 20);
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "inf_" + i);
            item.put("model_id", "emoji");
            item.put("user_hash", String.format("user_%04d", i));
            item.put("post_count", 5 + (i % 16));
            item.put("risk_score", Math.round((0.1 + (i % 9) * 0.1) * 10000.0) / 10000.0);
            item.put("inference_time_ms", 20 + (i % 60));
            item.put("created_at", String.format("2026-03-%02dT%02d:%02d:00Z",
                    28 - (i % 5) + 1, 10 + (i % 12), i % 60));
            history.add(item);
        }
        if (StringUtils.hasText(modelId)) {
            history = history.stream()
                    .filter(h -> modelId.equals(String.valueOf(h.get("model_id"))))
                    .toList();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("history", history);
        data.put("total", history.size());
        return data;
    }

    /**
     * 检测类预测由 risk 域编排；此处返回兼容占位结果，避免前端断链。
     */
    public Map<String, Object> predict(Map<String, Object> body) {
        List<String> posts = body.get("posts") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        if (posts.isEmpty() && body.get("user_hash") == null && body.get("userHash") == null) {
            throw new BusinessException("没有可用的帖子数据");
        }
        int postCount = posts.isEmpty() ? 0 : posts.size();

        Map<String, Object> emoji = new LinkedHashMap<>();
        emoji.put("risk_level", "medium");
        emoji.put("risk_score", 0.5);
        emoji.put("note", "由 risk 域编排，model 域仅提供占位响应");

        Map<String, Object> predictions = new LinkedHashMap<>();
        predictions.put("emoji", emoji);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_hash", first(body, "user_hash", "userHash"));
        data.put("post_count", postCount == 0 ? 1 : postCount);
        data.put("predictions", predictions);
        data.put("orchestrated_by", "risk");
        data.put("message", "检测类预测由 risk 域编排");
        data.put("timestamp", LocalDateTime.now().format(ISO));
        return data;
    }

    // ======================== Ollama / API / providers ========================

    public Map<String, Object> checkOllamaHealth(String baseUrl) {
        String url = normalizeBaseUrl(baseUrl);
        Map<String, Object> listResult = listOllamaAvailableModels(url);
        boolean ok = Boolean.TRUE.equals(listResult.get("success"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", ok);
        data.put("available", ok);
        data.put("healthy", ok);
        data.put("base_url", url);
        data.put("models", listResult.getOrDefault("models", List.of()));
        if (!ok && listResult.get("error") != null) {
            data.put("error", listResult.get("error"));
        }
        return data;
    }

    public String normalizeOllamaBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listOllamaAvailableModels(String baseUrl) {
        String url = normalizeBaseUrl(baseUrl);
        try {
            RestTemplate rt = ollamaRestTemplate();
            ResponseEntity<Map> resp = rt.getForEntity(url + "/api/tags", Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object models = resp.getBody().get("models");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("models", models == null ? List.of() : models);
                return result;
            }
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", "获取模型列表失败: " + resp.getStatusCode().value());
            fail.put("models", List.of());
            return fail;
        } catch (Exception ex) {
            log.warn("Ollama list models failed ({}): {}", url, ex.getMessage());
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", ex.getMessage() == null ? "Ollama 不可用" : ex.getMessage());
            fail.put("models", List.of());
            return fail;
        }
    }

    public List<Map<String, Object>> getConfiguredOllamaModels() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, model_name, model_code, ollama_model_name, ollama_base_url,
                           description, status, usage_count
                    FROM models
                    WHERE model_type = 'ollama' AND status = 'active'
                    ORDER BY id DESC
                    """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> m : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", m.get("id"));
                item.put("modelName", m.get("model_name"));
                item.put("modelCode", m.get("model_code"));
                item.put("ollamaModelName", m.get("ollama_model_name"));
                item.put("ollamaBaseUrl", m.get("ollama_base_url"));
                item.put("description", m.get("description"));
                item.put("status", m.get("status"));
                item.put("usageCount", m.get("usage_count"));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            log.warn("获取已配置 Ollama 模型失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getConfiguredApiModels() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, model_name, model_code, provider, api_base_url,
                           description, status, usage_count
                    FROM models
                    WHERE model_type = 'api' AND status = 'active'
                    ORDER BY id DESC
                    """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> m : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", m.get("id"));
                item.put("modelName", m.get("model_name"));
                item.put("modelCode", m.get("model_code"));
                item.put("provider", m.get("provider"));
                item.put("apiBaseUrl", m.get("api_base_url"));
                item.put("description", m.get("description"));
                item.put("status", m.get("status"));
                item.put("usageCount", m.get("usage_count"));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            log.warn("获取已配置 API 模型失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> listProviders() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(provider("dashscope", "DashScope", "通义千问",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                List.of("qwen-flash", "qwen-plus", "qwen-turbo", "qwen-max", "qwen3.6-plus"),
                "阿里云通义千问系列模型"));
        result.add(provider("deepseek", "DeepSeek", "DeepSeek",
                "https://api.deepseek.com/v1",
                List.of("deepseek-chat", "deepseek-coder", "deepseek-v3", "deepseek-r1"),
                "DeepSeek 系列模型（高性价比）"));
        result.add(provider("zhipu", "ZhipuAI", "智谱AI",
                "https://open.bigmodel.cn/api/paas/v4",
                List.of("glm-4-flash", "glm-4-plus", "glm-4", "glm-5-plus"),
                "智谱 AI GLM 系列模型"));
        result.add(provider("moonshot", "Moonshot", "Kimi",
                "https://api.moonshot.cn/v1",
                List.of("moonshot-v1-8k", "moonshot-v1-32k", "kimi-k2.5"),
                "月之暗面 Kimi 系列模型（超长上下文）"));
        result.add(provider("hunyuan", "Hunyuan", "腾讯云混元",
                "https://api.hunyuan.cloud.tencent.com/v1",
                List.of("hunyuan-pro", "hunyuan-standard", "hunyuan-lite"),
                "腾讯云混元系列模型"));
        result.add(provider("openai", "OpenAI", "OpenAI",
                "https://api.openai.com/v1",
                List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"),
                "OpenAI GPT 系列模型"));
        result.add(provider("google", "Google", "Gemini",
                "https://generativelanguage.googleapis.com/v1beta/openai/",
                List.of("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"),
                "Google Gemini 系列模型"));
        result.add(provider("anthropic", "Anthropic", "Claude",
                "https://api.anthropic.com/v1",
                List.of("claude-3-5-sonnet-latest", "claude-3-opus-latest", "claude-3-haiku-latest"),
                "Anthropic Claude 系列模型"));
        result.add(provider("ollama", "Ollama", "Ollama 本地模型",
                DEFAULT_OLLAMA_URL, List.of(),
                "Ollama 本地部署模型（需提前下载）"));
        result.add(provider("local", "Local", "本地模型",
                "http://localhost:8000/v1", List.of(),
                "其他本地部署模型（如 vLLM、LM Studio 等）"));
        return result;
    }

    public Map<String, Object> getModelForInference(long modelId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, model_name, model_code, model_type, model_category,
                           ollama_model_name, ollama_base_url, api_key, api_base_url,
                           provider, temperature, status
                    FROM models WHERE id = ? AND status = 'active'
                    """, modelId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            log.warn("获取推理模型失败 id={}: {}", modelId, ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> callOllamaModel(Map<String, Object> modelConfig, String prompt, String systemPrompt) {
        String modelName = asString(first(modelConfig, "ollama_model_name", "model_name"));
        String baseUrl = asString(modelConfig.get("ollama_base_url"));
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = DEFAULT_OLLAMA_URL;
        }
        baseUrl = normalizeBaseUrl(baseUrl);
        double temperature = toDouble(modelConfig.get("temperature"), 0.7);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("prompt", (systemPrompt == null ? "" : systemPrompt) + "\n\n" + prompt);
        payload.put("stream", false);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature >= 0 ? temperature : 0.7);
        options.put("num_predict", 512);
        payload.put("options", options);

        try {
            RestTemplate rt = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(5))
                    .setReadTimeout(Duration.ofSeconds(120))
                    .build();
            ResponseEntity<Map> resp = rt.postForEntity(baseUrl + "/api/generate", payload, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object reply = resp.getBody().get("response");
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("success", true);
                ok.put("response", reply == null ? "" : String.valueOf(reply).trim());
                ok.put("model", modelName);
                ok.put("model_type", "ollama");
                return ok;
            }
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", "Ollama 调用失败: HTTP " + resp.getStatusCode().value());
            fail.put("model", modelName);
            return fail;
        } catch (RestClientException ex) {
            log.warn("Ollama 调用失败（上游详情不写入日志）");
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", "Ollama 调用失败: " + ex.getMessage());
            fail.put("model", modelName);
            return fail;
        }
    }

    public Map<String, Object> callApiModel(Map<String, Object> modelConfig, String prompt, String systemPrompt) {
        String modelName = asString(first(modelConfig, "model_code", "model_name"));
        String baseUrl = asString(modelConfig.get("api_base_url"));
        String apiKey = asString(modelConfig.get("api_key"));
        if (!StringUtils.hasText(baseUrl)) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", "未配置 api_base_url");
            fail.put("model", modelName);
            return fail;
        }
        if (!StringUtils.hasText(apiKey)) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", "未配置 API Key");
            fail.put("model", modelName);
            return fail;
        }

        String endpoint = normalizeBaseUrl(baseUrl) + "/chat/completions";
        double temperature = toDouble(modelConfig.get("temperature"), 0.7);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("temperature", temperature >= 0 ? temperature : 0.7);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "你是一个有用的AI助手。" : systemPrompt),
                Map.of("role", "user", "content", prompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            RestTemplate rt = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(5))
                    .setReadTimeout(Duration.ofSeconds(120))
                    .build();
            ResponseEntity<Map> resp = rt.exchange(
                    endpoint, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                String reply = extractChatContent(resp.getBody());
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("success", true);
                ok.put("response", reply);
                ok.put("model", modelName);
                ok.put("model_type", "api");
                return ok;
            }
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", "API 调用失败: HTTP " + resp.getStatusCode().value());
            fail.put("model", modelName);
            return fail;
        } catch (Exception ex) {
            log.warn("API 模型调用失败（上游详情不写入日志）");
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("error", "API 调用失败: " + ex.getMessage());
            fail.put("model", modelName);
            return fail;
        }
    }

    public void updateModelUsage(long modelId, int processingTimeMs) {
        try {
            jdbcTemplate.update("""
                    UPDATE models
                    SET usage_count = usage_count + 1,
                        last_used_at = NOW(),
                        avg_processing_time_ms = (
                            (COALESCE(avg_processing_time_ms, 0) * usage_count + ?) / (usage_count + 1)
                        )
                    WHERE id = ?
                    """, processingTimeMs, modelId);
        } catch (Exception ex) {
            log.warn("更新模型使用统计失败 id={}: {}", modelId, ex.getMessage());
        }
    }

    // ======================== converters / helpers ========================

    public Map<String, Object> convertModelToCamel(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("modelName", row.get("model_name"));
        result.put("modelCode", row.get("model_code"));
        result.put("modelCategory", row.get("model_category"));
        result.put("modelType", row.get("model_type"));
        result.put("provider", row.get("provider"));
        result.put("apiBaseUrl", row.get("api_base_url"));
        result.put("configTemplate", row.get("config_template"));
        result.put("ollamaModelName", row.get("ollama_model_name"));
        result.put("ollamaBaseUrl", row.get("ollama_base_url"));
        result.put("modelPath", row.get("model_path"));
        result.put("loraPath", row.get("lora_path"));
        result.put("detectionType", row.get("detection_type"));
        result.put("modelFilePath", row.get("model_file_path"));
        result.put("embeddingFilePath", row.get("embedding_file_path"));
        result.put("supportedDatasets", parseSupportedDatasets(row.get("supported_datasets")));
        result.put("description", row.get("description"));
        result.put("version", row.get("version"));
        result.put("isAvailable", toBool(row.get("is_available")));
        result.put("isDefault", toBool(row.get("is_default")));
        result.put("isBuiltin", toBool(row.get("is_builtin")));
        result.put("performanceMetrics", parseJsonMap(row.get("performance_metrics")));
        result.put("status", row.get("status"));
        result.put("errorMessage", row.get("error_message"));
        result.put("lastUsedAt", formatTs(row.get("last_used_at")));
        result.put("usageCount", row.get("usage_count"));
        result.put("avgProcessingTimeMs", row.get("avg_processing_time_ms"));
        result.put("createdAt", formatTs(row.get("created_at")));
        result.put("updatedAt", formatTs(row.get("updated_at")));

        if ("api".equals(String.valueOf(row.get("model_type")))) {
            String key = asString(row.get("api_key"));
            result.put("hasApiKey", StringUtils.hasText(key));
        } else {
            result.put("hasApiKey", null);
        }

        if (toBool(row.get("is_builtin")) && "detection".equals(String.valueOf(row.get("model_category")))) {
            result.put("isAvailable", true);
            result.put("status", "active");
        }
        return result;
    }

    public Map<String, Object> convertTemplateToCamel(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("name", row.get("name"));
        result.put("taskType", row.get("task_type"));
        result.put("description", row.get("description"));
        result.put("promptContent", row.get("prompt_content"));
        result.put("variables", parseJsonObject(row.get("variables")));
        result.put("modelId", row.get("model_id"));
        result.put("isActive", row.get("is_active") == null || toBool(row.get("is_active")));
        result.put("usageCount", row.get("usage_count"));
        result.put("createdAt", formatTs(row.get("created_at")));
        result.put("updatedAt", formatTs(row.get("updated_at")));
        return result;
    }

    private Map<String, Object> normalizeTemplatePayload(Map<String, Object> templateData) {
        Map<String, Object> normalized = new HashMap<>(templateData);
        putAlias(normalized, "taskType", "task_type");
        putAlias(normalized, "promptContent", "prompt_content");
        putAlias(normalized, "modelId", "model_id");
        putAlias(normalized, "isActive", "is_active");
        return normalized;
    }

    private void putAlias(Map<String, Object> map, String camel, String snake) {
        if (map.containsKey(camel) && !map.containsKey(snake)) {
            map.put(snake, map.get(camel));
        }
    }

    private String buildModelCode(Map<String, Object> modelData) {
        Object raw = first(modelData,
                "modelCode", "model_code",
                "ollamaModelName", "ollama_model_name",
                "modelName", "model_name");
        String base = raw == null ? "model" : String.valueOf(raw).trim().toLowerCase();
        String normalized = NON_ALNUM.matcher(base).replaceAll("-").replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "model";
        }
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return normalized + "-" + suffix;
    }

    private RestTemplate ollamaRestTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static Map<String, Object> provider(
            String key, String name, String nameZh, String baseUrl, List<String> models, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("nameZh", nameZh);
        m.put("baseUrl", baseUrl);
        m.put("models", models);
        m.put("description", description);
        return m;
    }

    @SuppressWarnings("unchecked")
    private String extractChatContent(Map<?, ?> body) {
        Object choices = body.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg) {
                    Object content = msg.get("content");
                    return content == null ? "" : String.valueOf(content).trim();
                }
                Object text = choice.get("text");
                if (text != null) {
                    return String.valueOf(text).trim();
                }
            }
        }
        return "";
    }

    private Object parseSupportedDatasets(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        String s = String.valueOf(value).trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            List<Object> parsed = objectMapper.readValue(s, new TypeReference<>() {});
            return parsed.stream().map(String::valueOf).toList();
        } catch (Exception ex) {
            return s;
        }
    }

    private Map<String, Object> parseJsonMap(Object value) {
        Object parsed = parseJsonObject(value);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return parsed == null ? null : Map.of();
    }

    private Object parseJsonObject(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            return value;
        }
        String s = ((String) value).trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return objectMapper.readValue(s, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private static Object first(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key) && data.get(key) != null) {
                return data.get(key);
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> db, String key, Object value) {
        if (value != null) {
            db.put(key, value);
        }
    }

    private static void copyIfKey(Map<String, Object> src, String srcKey, Map<String, Object> db, String dbKey) {
        if (src.containsKey(srcKey)) {
            db.put(dbKey, src.get(srcKey));
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return DEFAULT_OLLAMA_URL;
        }
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String formatTs(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(ISO);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(ISO);
        }
        return String.valueOf(value);
    }

    private static boolean toBool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = String.valueOf(value).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static double metricValue(Map<String, Object> metrics, String key) {
        Object v = metrics.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return 0;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
