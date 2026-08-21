package com.vis4srd.risk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.config.LlmProperties;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.llm.LlmChatClient;
import com.vis4srd.dataset.service.DatasetService;
import com.vis4srd.dataset.util.DatasetTextAnalysis;
import com.vis4srd.integration.PythonInferenceClient;
import com.vis4srd.integration.PythonInferenceProperties;
import com.vis4srd.model.service.ModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯 Java 风险检测：任务 CRUD + Emocc/FeaLearner 启发式评分 + LLM 临床分析报告（对齐原 Python 融合分析字段）。
 */
@Slf4j
@Service
public class RiskService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");
    private static final String EMOCC_EXCLUDE =
            "NOT (task_mode <=> 'single' AND JSON_UNQUOTE(JSON_EXTRACT(detection_model_configs, '$.model_type')) <=> 'emocc_local')";
    private static final String EMOCC_FILTER =
            "task_mode = 'single' AND JSON_UNQUOTE(JSON_EXTRACT(detection_model_configs, '$.model_type')) = 'emocc_local'";
    private static final String FEA_FILTER =
            "task_mode = 'single' AND JSON_UNQUOTE(JSON_EXTRACT(detection_model_configs, '$.model_type')) IN ('fealearner_java', 'fealearner_local')";

    private static final List<Map<String, Object>> TASK_TYPES = List.of(
            Map.of(
                    "id", 1,
                    "type_code", "suicide",
                    "type_name", "自杀风险检测",
                    "description", "对用户进行自杀风险评估和检测",
                    "icon", "Shield",
                    "color", "bg-red-500",
                    "sort_order", 1
            )
    );

    private static final List<String> FEA_DATASETS = List.of("reddit", "weibo", "bigdata", "sigir");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DatasetService datasetService;
    private final ModelService modelService;
    private final LlmChatClient llmChatClient;
    private final LlmProperties llmProperties;
    private final PythonInferenceClient pythonInferenceClient;
    private final PythonInferenceProperties pythonInferenceProperties;

    public RiskService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DatasetService datasetService,
            ModelService modelService,
            LlmChatClient llmChatClient,
            LlmProperties llmProperties,
            PythonInferenceClient pythonInferenceClient,
            PythonInferenceProperties pythonInferenceProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.datasetService = datasetService;
        this.modelService = modelService;
        this.llmChatClient = llmChatClient;
        this.llmProperties = llmProperties;
        this.pythonInferenceClient = pythonInferenceClient;
        this.pythonInferenceProperties = pythonInferenceProperties;
    }

    // ======================== 静态接口 ========================

    public List<Map<String, Object>> getTaskTypes() {
        return TASK_TYPES;
    }

    public List<Map<String, Object>> getRiskModels() {
        Map<String, Object> emocc = new LinkedHashMap<>();
        emocc.put("id", "emoji");
        emocc.put("name", "Emocc");
        emocc.put("description", "Emocc 本地风险检测模型，结合文本语义与 Emoji 线索进行风险分类");
        emocc.put("type", "risk_detection");
        emocc.put("accuracy", 0.823);
        emocc.put("recall", 0.815);
        emocc.put("f1", 0.809);
        return List.of(emocc);
    }

    public Map<String, Object> getCompareData() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("name", "Emocc");
        model.put("accuracy", 0.823);
        model.put("precision", 0.798);
        model.put("recall", 0.815);
        model.put("f1", 0.809);
        model.put("auc", 0.878);

        Map<String, Object> radar = new LinkedHashMap<>();
        radar.put("dimensions", List.of("准确率", "精确率", "召回率", "F1分数", "AUC"));
        radar.put("emoji", List.of(0.823, 0.798, 0.815, 0.809, 0.878));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("models", List.of(model));
        data.put("radar_data", radar);
        return data;
    }

    public Map<String, Object> getEmoccModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        for (String ds : List.of("reddit", "weibo", "bigdata", "sigir")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("datasetKey", ds);
            m.put("modelName", emoccModelName(ds));
            m.put("modelType", "emocc_local");
            m.put("description", "Java 启发式风险证据聚合（对齐 Emocc 任务接口）");
            models.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model_name", "Emocc");
        data.put("model_type", "emocc_local");
        data.put("models", models);
        if (!models.isEmpty()) {
            data.putAll(models.get(0));
        }
        return data;
    }

    public Map<String, Object> getFealearnerModels() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model_name", "FeaLearner");
        data.put("model_type", "fealearner_java");
        data.put("description", "基于帖子启发式重要性与风险证据的 FeaLearner Java 实现");
        data.put("architecture", Map.of(
                "text_encoder", "DatasetTextAnalysis",
                "classifier", "importance aggregation",
                "output", "high|medium|low"));
        data.put("features", List.of(
                "基于文本风险关键词与证据域聚合",
                "支持 reddit / weibo / bigdata / sigir"));
        data.put("performance", Map.of(
                "accuracy", 0.85,
                "precision", 0.83,
                "recall", 0.82,
                "f1", 0.825));
        data.put("supported_datasets", FEA_DATASETS);
        data.put("input_format", Map.of("user_hash", "string", "dataset", String.join("|", FEA_DATASETS)));
        data.put("output_format", Map.of(
                "risk_level", "high|medium|low",
                "risk_score", "0.0-1.0",
                "confidence", "0.0-1.0"));
        return data;
    }

    // ======================== 通用任务 ========================

    public Map<String, Object> createTask(Map<String, Object> body) {
        long start = System.currentTimeMillis();
        String userHash = str(first(body, "userHash", "user_hash"), "");
        String dataSource = str(first(body, "dataSource", "data_source"), "reddit");
        int taskTypeId = toInt(first(body, "taskTypeId", "task_type_id"), 1);
        String taskMode = str(first(body, "taskMode", "task_mode"), "single");
        if (!"single".equals(taskMode) && !"multi".equals(taskMode)) {
            taskMode = "single";
        }
        String taskName = str(first(body, "taskName", "task_name"), null);
        String taskDescription = str(first(body, "taskDescription", "task_description"), null);
        Long singleModelId = toLongObj(first(body, "singleModelId", "single_model_id"));
        Long promptTemplateId = toLongObj(first(body, "promptTemplateId", "single_prompt_template_id", "prompt_template_id"));
        Long fusionModelId = toLongObj(first(body, "fusionModelId", "fusion_model_id"));

        int postCount = countUserPosts(dataSource, userHash);

        String modelType = "api";
        String modelName = "unknown";
        if (singleModelId != null) {
            Map<String, Object> model = modelService.getModelRawById(singleModelId);
            if (model != null) {
                modelName = str(model.get("model_name"), "unknown");
                String provider = str(model.get("provider"), "");
                modelType = "ollama".equalsIgnoreCase(provider) ? "ollama" : "api";
            }
        }

        String taskCode = generateTaskCode("risk");
        String finalName = StringUtils.hasText(taskName)
                ? taskName
                : "风险检测_" + (userHash.length() >= 8 ? userHash.substring(0, 8) : (userHash.isEmpty() ? "unknown" : userHash));
        String finalDesc = StringUtils.hasText(taskDescription)
                ? taskDescription
                : "对用户 " + userHash + " 进行自杀风险检测";

        Map<String, Object> detectionConfigs = new LinkedHashMap<>();
        detectionConfigs.put("model_type", modelType);
        detectionConfigs.put("model_name", modelName);

        long id = insertRiskTask(Map.ofEntries(
                Map.entry("task_code", taskCode),
                Map.entry("task_name", finalName),
                Map.entry("task_description", finalDesc),
                Map.entry("task_mode", taskMode),
                Map.entry("task_type_id", taskTypeId),
                Map.entry("user_hash", userHash),
                Map.entry("data_source", dataSource),
                Map.entry("post_count", postCount),
                Map.entry("status", "pending"),
                Map.entry("progress", 0),
                Map.entry("detection_status", "pending"),
                Map.entry("fusion_status", "pending"),
                Map.entry("detection_progress", 0),
                Map.entry("fusion_progress", 0),
                Map.entry("single_model_id", singleModelId == null ? 0 : singleModelId),
                Map.entry("single_prompt_template_id", promptTemplateId == null ? 0 : promptTemplateId),
                Map.entry("detection_model_configs", detectionConfigs),
                Map.entry("fusion_model_id", fusionModelId == null ? 0 : fusionModelId)
        ), true);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("taskCode", "RISK-" + taskCode);
        data.put("taskName", finalName);
        data.put("taskDescription", finalDesc);
        data.put("taskMode", taskMode);
        data.put("taskTypeId", taskTypeId);
        data.put("userHash", userHash);
        data.put("dataSource", dataSource);
        data.put("postCount", postCount);
        data.put("singleModelId", singleModelId);
        data.put("modelName", modelName);
        data.put("promptTemplateId", promptTemplateId);
        data.put("progress", 0);
        data.put("status", "pending");
        data.put("detectionProgress", 0);
        data.put("fusionProgress", 0);
        data.put("detectionStatus", "pending");
        data.put("fusionStatus", "pending");
        data.put("resultSummary", null);
        data.put("createdAt", LocalDateTime.now().format(ISO));
        data.put("startedAt", null);
        data.put("completedAt", null);
        data.put("processingTimeMs", (int) (System.currentTimeMillis() - start));
        return data;
    }

    public Map<String, Object> listTasks(int page, int limit, String status, String dataSource) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int offset = (safePage - 1) * safeLimit;

        StringBuilder where = new StringBuilder(EMOCC_EXCLUDE);
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status);
        }
        if (StringUtils.hasText(dataSource)) {
            where.append(" AND LOWER(data_source) = LOWER(?)");
            params.add(dataSource);
        }

        Map<String, Object> stats = getTaskStats();
        long total = 0;
        List<Map<String, Object>> rows = List.of();
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM risk_detection_tasks WHERE " + where,
                    Long.class,
                    params.toArray());
            total = cnt == null ? 0 : cnt;
            List<Object> listParams = new ArrayList<>(params);
            listParams.add(safeLimit);
            listParams.add(offset);
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT id, task_code, task_name, task_description, task_mode, task_type_id,
                                   archive_id, user_hash, data_source, post_count,
                                   single_model_id, single_prompt_template_id, detection_model_configs,
                                   fusion_model_id, fusion_prompt_template_id,
                                   progress, status, started_at, completed_at, processing_time_ms,
                                   detection_progress, fusion_progress, detection_status, fusion_status,
                                   error_message, result_summary, created_at
                            FROM risk_detection_tasks
                            WHERE
                            """ + where + """
                             ORDER BY created_at DESC
                            LIMIT ? OFFSET ?
                            """,
                    listParams.toArray());
        } catch (Exception ex) {
            log.warn("查询风险任务列表失败: {}", ex.getMessage());
        }

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            tasks.add(toDetectionTask(row));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", tasks);
        data.put("stats", stats);
        data.put("total", total);
        data.put("page", safePage);
        data.put("limit", safeLimit);
        return data;
    }

    public Map<String, Object> getTask(String taskId) {
        Map<String, Object> row = findTaskRow(taskId, null);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.get("id"));
        data.put("task_code", row.get("task_code"));
        data.put("taskCode", "RISK-" + str(row.get("task_code"), ""));
        data.put("taskName", str(row.get("task_name"), "风险检测任务"));
        data.put("taskDescription", str(row.get("task_description"), ""));
        data.put("taskMode", str(row.get("task_mode"), "single"));
        data.put("taskTypeId", row.get("task_type_id") == null ? 1 : row.get("task_type_id"));
        data.put("userHash", str(row.get("user_hash"), ""));
        data.put("dataSource", str(row.get("data_source"), ""));
        data.put("postCount", row.get("post_count") == null ? 0 : row.get("post_count"));
        data.put("status", str(row.get("status"), "pending"));
        data.put("progress", row.get("progress") == null ? 0 : row.get("progress"));
        data.put("resultSummary", parseJsonObject(row.get("result_summary")));
        data.put("errorMessage", row.get("error_message"));
        data.put("startedAt", formatTs(row.get("started_at")));
        data.put("completedAt", formatTs(row.get("completed_at")));
        data.put("processingTimeMs", row.get("processing_time_ms"));
        data.put("createdAt", formatTs(row.get("created_at")));
        return data;
    }

    public void deleteTask(String taskId) {
        ResolvedId resolved = resolveTaskId(taskId, "RISK-");
        int deleted;
        try {
            if (resolved.numericId != null) {
                deleted = jdbcTemplate.update("DELETE FROM risk_detection_tasks WHERE id = ?", resolved.numericId);
            } else {
                deleted = jdbcTemplate.update("DELETE FROM risk_detection_tasks WHERE task_code = ?", resolved.code);
            }
        } catch (Exception ex) {
            log.warn("删除风险任务失败: {}", ex.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "删除失败: " + ex.getMessage());
        }
        if (deleted <= 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在或已删除");
        }
    }

    public Map<String, Object> executeTask(String taskId) {
        Map<String, Object> task = findTaskRow(taskId, null);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        String status = str(task.get("status"), "");
        if ("running".equals(status)) {
            throw new BusinessException("任务正在执行中");
        }
        if ("completed".equals(status)) {
            throw new BusinessException("任务已完成，无需重复执行");
        }

        long dbId = toLong(task.get("id"));
        markRunning(dbId);
        long start = System.currentTimeMillis();

        Map<String, Object> detectionConfigs = parseJsonObject(task.get("detection_model_configs"));
        String modelType = str(detectionConfigs.get("model_type"), "");
        Long singleModelId = toLongObj(task.get("single_model_id"));
        if (!StringUtils.hasText(modelType) && singleModelId != null && singleModelId > 0) {
            Map<String, Object> model = modelService.getModelRawById(singleModelId);
            if (model != null) {
                modelType = "ollama".equalsIgnoreCase(str(model.get("provider"), "")) ? "ollama" : "api";
                detectionConfigs.put("model_type", modelType);
                detectionConfigs.put("model_name", str(model.get("model_name"), "unknown"));
            }
        }
        if (!StringUtils.hasText(modelType)) {
            modelType = "api";
        }

        try {
            Map<String, Object> resultSummary;
            String taskName = str(task.get("task_name"), "").toLowerCase(Locale.ROOT);
            if ("emocc_local".equals(modelType) || taskName.contains("emocc")) {
                resultSummary = executeHeuristicDetection(task, "emocc_local", toLongObj(task.get("fusion_model_id")));
            } else if ("fealearner_java".equals(modelType) || "fealearner_local".equals(modelType) || taskName.contains("fealearner")) {
                resultSummary = executeHeuristicDetection(task, "fealearner_java", toLongObj(task.get("fusion_model_id")));
            } else if ("ollama".equals(modelType)) {
                resultSummary = executeLlmTask(task, true);
            } else {
                resultSummary = executeLlmTask(task, false);
            }

            int elapsed = (int) (System.currentTimeMillis() - start);
            markCompleted(dbId, elapsed, resultSummary);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", dbId);
            data.put("taskCode", "RISK-" + str(task.get("task_code"), ""));
            data.put("resultSummary", resultSummary);
            data.put("processingTimeMs", elapsed);
            data.put("startedAt", LocalDateTime.now().format(ISO));
            data.put("completedAt", LocalDateTime.now().format(ISO));
            data.put("message", "任务执行成功");
            return data;
        } catch (BusinessException ex) {
            markFailed(dbId, (int) (System.currentTimeMillis() - start), ex.getMessage());
            throw ex;
        } catch (Throwable ex) {
            markFailed(dbId, (int) (System.currentTimeMillis() - start), ex.getMessage());
            throw new BusinessException("任务执行失败: " + ex.getMessage());
        }
    }

    public Map<String, Object> getReport(String taskId) {
        Map<String, Object> task = findTaskRow(taskId, List.of("RISK-", "EMOCC-", "FEA-"));
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        Map<String, Object> resultSummary = parseJsonObject(task.get("result_summary"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.get("id"));
        data.put("taskCode", task.get("task_code"));
        data.put("taskName", task.get("task_name"));
        data.put("userHash", task.get("user_hash"));
        data.put("dataSource", task.get("data_source"));
        data.put("postCount", task.get("post_count"));
        data.put("modelName", inferReportModelName(task, resultSummary));
        data.put("processingTimeMs", task.get("processing_time_ms"));
        data.put("createdAt", formatTs(task.get("created_at")));
        data.put("completedAt", formatTs(task.get("completed_at")));
        data.put("resultSummary", resultSummary);
        return data;
    }

    public String exportReportHtml(String taskId) {
        Map<String, Object> task = findTaskRow(taskId, List.of("RISK-", "EMOCC-", "FEA-"));
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        Map<String, Object> rs = parseJsonObject(task.get("result_summary"));
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("userHash", task.get("user_hash"));
        display.put("dataSource", task.get("data_source"));
        display.put("postCount", task.get("post_count"));
        display.put("completedAt", formatTsSpace(task.get("completed_at")));
        @SuppressWarnings("unchecked")
        Map<String, Object> emocc = rs.get("emoccModelResult") instanceof Map
                ? (Map<String, Object>) rs.get("emoccModelResult")
                : null;
        return buildClinicalReportHtml(display, rs, emocc);
    }

    // ======================== Emocc ========================

    public Map<String, Object> createEmoccTask(Map<String, Object> body) {
        String userHash = str(first(body, "userHash", "user_hash"), "");
        String dataSource = str(first(body, "dataSource", "data_source"), "reddit");
        Long detectionModelId = toLongObj(first(body, "detectionModelId", "detection_model_id"));
        Long fusionModelId = toLongObj(first(body, "fusionModelId", "fusion_model_id"));
        String taskNameInput = str(first(body, "taskName", "task_name"), null);

        if (!StringUtils.hasText(userHash)) {
            throw new BusinessException("用户哈希不能为空");
        }

        int postCount = countUserPosts(dataSource, userHash);
        if (postCount <= 0) {
            throw new BusinessException("用户没有可用的帖子数据");
        }

        String taskCode = generateTaskCode("emocc");
        String finalName = StringUtils.hasText(taskNameInput)
                ? taskNameInput
                : "Emocc检测_" + (userHash.length() >= 8 ? userHash.substring(0, 8) : userHash);

        Map<String, Object> detectionConfigs = new LinkedHashMap<>();
        detectionConfigs.put("model_type", "emocc_local");
        detectionConfigs.put("model_name", emoccModelName(dataSource));
        detectionConfigs.put("dataset_key", dataSource);
        detectionConfigs.put("detection_model_id", detectionModelId);

        long id = insertRiskTask(Map.ofEntries(
                Map.entry("task_code", taskCode),
                Map.entry("task_name", finalName),
                Map.entry("task_description", "使用Emocc本地模型对用户 " + (userHash.length() >= 8 ? userHash.substring(0, 8) : userHash) + " 进行自杀风险检测"),
                Map.entry("task_mode", "single"),
                Map.entry("task_type_id", 1),
                Map.entry("user_hash", userHash),
                Map.entry("data_source", dataSource),
                Map.entry("post_count", postCount),
                Map.entry("status", "pending"),
                Map.entry("progress", 0),
                Map.entry("detection_model_configs", detectionConfigs),
                Map.entry("fusion_model_id", fusionModelId == null ? 0 : fusionModelId)
        ), false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("taskCode", "EMOCC-" + taskCode);
        data.put("taskName", finalName);
        data.put("taskDescription", "使用Emocc本地模型对用户 " + (userHash.length() >= 8 ? userHash.substring(0, 8) : userHash) + " 进行自杀风险检测");
        data.put("taskMode", "emocc");
        data.put("userHash", userHash);
        data.put("dataSource", dataSource);
        data.put("postCount", postCount);
        data.put("modelName", emoccModelName(dataSource));
        data.put("progress", 0);
        data.put("status", "pending");
        data.put("resultSummary", null);
        data.put("createdAt", LocalDateTime.now().format(ISO));
        data.put("startedAt", null);
        data.put("completedAt", null);
        data.put("processingTimeMs", null);
        return data;
    }

    public Map<String, Object> listEmoccTasks(int page, int limit) {
        return listTypedTasks(page, limit, EMOCC_FILTER, "EMOCC-", "emocc", true);
    }

    public Map<String, Object> getEmoccTask(String taskId) {
        Map<String, Object> row = findTypedTask(taskId, "EMOCC-", EMOCC_FILTER);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return toTypedTaskView(row, "EMOCC-", "emocc", emoccModelName(str(row.get("data_source"), "reddit")));
    }

    public void deleteEmoccTask(String taskId) {
        deleteTypedTask(taskId, "EMOCC-", EMOCC_FILTER);
    }

    public Map<String, Object> executeEmoccTask(String taskId) {
        Map<String, Object> task = findTypedTask(taskId, "EMOCC-", EMOCC_FILTER);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        if ("completed".equals(str(task.get("status"), ""))) {
            throw new BusinessException("任务已完成，无需重复执行");
        }
        long dbId = toLong(task.get("id"));
        markRunning(dbId);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> resultSummary = executeHeuristicDetection(
                    task, "emocc_local", toLongObj(task.get("fusion_model_id")));
            int elapsed = (int) (System.currentTimeMillis() - start);
            markCompleted(dbId, elapsed, resultSummary);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", dbId);
            data.put("taskCode", "EMOCC-" + str(task.get("task_code"), ""));
            data.put("taskName", task.get("task_name"));
            data.put("userHash", task.get("user_hash"));
            data.put("dataSource", task.get("data_source"));
            data.put("postCount", resultSummary.get("postCount"));
            data.put("modelName", emoccModelName(str(task.get("data_source"), "reddit")));
            data.put("progress", 100);
            data.put("status", "completed");
            data.put("resultSummary", resultSummary);
            data.put("processingTimeMs", elapsed);
            data.put("startedAt", LocalDateTime.now().format(ISO));
            data.put("completedAt", LocalDateTime.now().format(ISO));
            return data;
        } catch (Throwable ex) {
            markFailed(dbId, (int) (System.currentTimeMillis() - start), ex.getMessage());
            throw new BusinessException("Emocc 任务执行失败: " + ex.getMessage());
        }
    }

    // ======================== FeaLearner ========================

    public Map<String, Object> createFealearnerTask(Map<String, Object> body) {
        String userHash = str(first(body, "userHash", "user_hash"), "");
        String dataSource = str(first(body, "dataSource", "data_source"), "reddit").toLowerCase(Locale.ROOT);
        Long fusionModelId = toLongObj(first(body, "fusionModelId", "fusion_model_id"));
        String taskName = str(first(body, "taskName", "task_name"), "");

        if (!FEA_DATASETS.contains(dataSource)) {
            throw new BusinessException("不支持的 dataSource「" + dataSource + "」，可选: " + String.join(", ", FEA_DATASETS));
        }
        if (!StringUtils.hasText(userHash)) {
            throw new BusinessException("缺少 userHash 参数");
        }

        int postCount = countUserPosts(dataSource, userHash);
        if (postCount <= 0) {
            throw new BusinessException("用户没有可用的帖子数据");
        }

        String codeBody = generateTaskCode("fea");
        String taskCode = "FEA-" + codeBody;
        if (!StringUtils.hasText(taskName)) {
            taskName = "FeaLearner检测_" + (userHash.length() >= 8 ? userHash.substring(0, 8) : userHash);
        }
        String modelName = fealearnerModelName(dataSource);

        Map<String, Object> detectionConfigs = new LinkedHashMap<>();
        detectionConfigs.put("model_type", "fealearner_java");
        detectionConfigs.put("source", "fealearner");
        detectionConfigs.put("dataset", dataSource);
        detectionConfigs.put("model_name", modelName);

        long id;
        try {
            id = insertRiskTask(Map.ofEntries(
                    Map.entry("task_code", taskCode),
                    Map.entry("task_name", taskName),
                    Map.entry("task_description", modelName + " 本地模型检测任务"),
                    Map.entry("task_mode", "single"),
                    Map.entry("task_type_id", 1),
                    Map.entry("user_hash", userHash),
                    Map.entry("data_source", dataSource),
                    Map.entry("post_count", postCount),
                    Map.entry("status", "pending"),
                    Map.entry("progress", 0),
                    Map.entry("detection_model_configs", detectionConfigs),
                    Map.entry("fusion_model_id", fusionModelId == null ? 0 : fusionModelId)
            ), false);
        } catch (Exception ex) {
            log.warn("创建 FeaLearner 任务失败: {}", ex.getMessage());
            throw new BusinessException("创建 FeaLearner 任务失败，请检查 risk_detection_tasks 表是否可用");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("taskCode", taskCode);
        data.put("taskName", taskName);
        data.put("taskDescription", modelName + " 本地模型检测任务");
        data.put("taskMode", "fealearner");
        data.put("userHash", userHash);
        data.put("dataSource", dataSource);
        data.put("postCount", postCount);
        data.put("modelName", modelName);
        data.put("progress", 0);
        data.put("status", "pending");
        data.put("resultSummary", null);
        data.put("createdAt", LocalDateTime.now().format(ISO));
        data.put("startedAt", null);
        data.put("completedAt", null);
        data.put("processingTimeMs", null);
        return data;
    }

    public Map<String, Object> listFealearnerTasks(int page, int limit) {
        try {
            return listTypedTasks(page, limit, FEA_FILTER, "", "fealearner", false);
        } catch (Exception ex) {
            log.warn("查询 FeaLearner 任务失败: {}", ex.getMessage());
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("tasks", List.of());
            empty.put("total", 0);
            empty.put("page", Math.max(page, 1));
            empty.put("limit", Math.min(Math.max(limit, 1), 100));
            return empty;
        }
    }

    public Map<String, Object> getFealearnerTask(String taskId) {
        Map<String, Object> row = findTypedTask(taskId, "FEA-", FEA_FILTER);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        Map<String, Object> view = toTypedTaskView(
                row, "", "fealearner", fealearnerModelName(str(row.get("data_source"), "reddit")));
        view.put("taskCode", row.get("task_code"));
        Map<String, Object> rs = parseJsonObject(row.get("result_summary"));
        view.put("fealearnerResult", rs.get("fealearnerModelResult"));
        return view;
    }

    public void deleteFealearnerTask(String taskId) {
        deleteTypedTask(taskId, "FEA-", FEA_FILTER);
    }

    public Map<String, Object> executeFealearnerTask(String taskId) {
        Map<String, Object> task = findTypedTask(taskId, "FEA-", FEA_FILTER);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        if ("completed".equals(str(task.get("status"), ""))) {
            throw new BusinessException("任务已完成，无需重复执行");
        }
        long dbId = toLong(task.get("id"));
        markRunning(dbId);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> resultSummary = executeHeuristicDetection(
                    task, "fealearner_java", toLongObj(task.get("fusion_model_id")));
            int elapsed = (int) (System.currentTimeMillis() - start);
            markCompleted(dbId, elapsed, resultSummary);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", dbId);
            data.put("taskCode", task.get("task_code"));
            data.put("taskName", task.get("task_name"));
            data.put("userHash", task.get("user_hash"));
            data.put("dataSource", task.get("data_source"));
            data.put("postCount", resultSummary.get("postCount"));
            data.put("modelName", fealearnerModelName(str(task.get("data_source"), "reddit")));
            data.put("progress", 100);
            data.put("status", "completed");
            data.put("resultSummary", resultSummary);
            data.put("processingTimeMs", elapsed);
            data.put("startedAt", LocalDateTime.now().format(ISO));
            data.put("completedAt", LocalDateTime.now().format(ISO));
            return data;
        } catch (Throwable ex) {
            markFailed(dbId, (int) (System.currentTimeMillis() - start), ex.getMessage());
            throw new BusinessException("FeaLearner 任务执行失败: " + ex.getMessage());
        }
    }

    // ======================== Predict ========================

    public Map<String, Object> predict(String userHash, String dataSource) {
        if (!StringUtils.hasText(userHash)) {
            throw new BusinessException("缺少 userHash");
        }
        String ds = StringUtils.hasText(dataSource) ? dataSource : "reddit";
        List<String> posts = loadPostTexts(ds, userHash);
        Map<String, Object> aggregated = aggregateHeuristic(posts);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userHash", userHash);
        data.put("dataSource", ds);
        data.put("riskLevel", aggregated.get("riskLevel"));
        data.put("riskScore", aggregated.get("riskScore"));
        data.put("confidence", aggregated.get("confidence"));
        data.put("summary", aggregated.get("summary"));
        data.put("postCount", posts.size());
        data.put("modelType", "heuristic");
        data.put("riskFactors", aggregated.get("riskFactors"));
        return data;
    }

    // ======================== 执行核心 ========================

    private Map<String, Object> executeLlmTask(Map<String, Object> task, boolean ollama) {
        String userHash = str(task.get("user_hash"), "");
        String dataSource = str(task.get("data_source"), "reddit");
        List<String> posts = loadPostTexts(dataSource, userHash);
        if (posts.isEmpty()) {
            throw new BusinessException(
                    "无法获取用户 " + userHash + " 的帖子（MySQL 档案为空且内置 CSV 未匹配到该用户，请确认 dataSource="
                            + dataSource + "）");
        }

        Long singleModelId = toLongObj(task.get("single_model_id"));
        Map<String, Object> modelConfig = null;
        if (singleModelId != null && singleModelId > 0) {
            modelConfig = modelService.getModelRawById(singleModelId);
        }
        if (modelConfig == null) {
            modelConfig = findDefaultModel(ollama);
        }

        if (modelConfig == null) {
            Map<String, Object> heuristic = aggregateHeuristic(posts);
            heuristic.put("modelType", ollama ? "ollama_heuristic" : "api_heuristic");
            heuristic.put("summary", "无可用模型配置，已使用启发式评估。" + heuristic.get("summary"));
            return heuristic;
        }

        String prompt = buildRiskPrompt(posts, userHash, dataSource);
        String systemPrompt = "你是一位专业的心理健康评估专家，专注于自杀风险检测。"
                + "请严格依据用户帖子完成评估，最终只能返回一个合法 JSON 对象。";

        Map<String, Object> callResult = ollama
                ? modelService.callOllamaModel(modelConfig, prompt, systemPrompt)
                : modelService.callApiModel(modelConfig, prompt, systemPrompt);

        if (!Boolean.TRUE.equals(callResult.get("success"))) {
            Map<String, Object> heuristic = aggregateHeuristic(posts);
            heuristic.put("modelType", ollama ? "ollama_heuristic" : "api_heuristic");
            heuristic.put("summary", "模型调用失败，已回退启发式评估。" + heuristic.get("summary"));
            heuristic.put("llmError", str(callResult.get("error"), "unknown"));
            return heuristic;
        }

        String responseText = str(callResult.get("response"), "");
        Map<String, Object> parsed = extractJsonMap(responseText);
        Map<String, Object> heuristic = aggregateHeuristic(posts);
        Map<String, Object> structured = normalizeReportFields(
                parsed.isEmpty() ? Map.of("summary", responseText) : parsed,
                str(heuristic.get("riskLevel"), "medium"),
                toDouble(heuristic.get("riskScore"), 0.5),
                0.8,
                ollama ? "Ollama模型检测完成" : "API模型检测完成");
        structured.put("modelType", ollama ? "ollama" : "api_dashscope");
        structured.put("postCount", posts.size());
        structured.put("modelName", str(modelConfig.get("model_name"), ""));
        if (!StringUtils.hasText(str(structured.get("llmModel"), ""))) {
            structured.put("llmModel", str(first(modelConfig, "model_code", "ollama_model_name", "model_name"), ""));
        }
        if (!(structured.get("riskFactors") instanceof List) || ((List<?>) structured.get("riskFactors")).isEmpty()) {
            structured.put("riskFactors", heuristic.get("riskFactors"));
        }
        return structured;
    }

    private Map<String, Object> executeHeuristicDetection(
            Map<String, Object> task, String modelType, Long fusionModelId) {
        String userHash = str(task.get("user_hash"), "");
        String dataSource = str(task.get("data_source"), "reddit");
        List<String> posts = loadPostTexts(dataSource, userHash);
        if (posts.isEmpty()) {
            throw new BusinessException(
                    "无法获取用户 " + userHash + " 的帖子（MySQL 档案为空且内置 CSV 未匹配到该用户，请确认 dataSource="
                            + dataSource + "）");
        }

        // 优先调用 Python 推理侧车（真实 Emocc / FeaLearner）
        Map<String, Object> sidecar = tryPythonInference(userHash, dataSource, posts, modelType);
        if (sidecar != null) {
            enrichWithLlmClinicalAnalysis(sidecar, posts, modelType, fusionModelId);
            return sidecar;
        }
        if (!pythonInferenceProperties.isFallbackHeuristic()) {
            throw new BusinessException("推理侧车不可用，且已禁用启发式回退（请启动 inference-python :8001）");
        }

        Map<String, Object> aggregated = aggregateHeuristic(posts);
        Map<String, Object> result = new LinkedHashMap<>(aggregated);
        result.put("modelType", modelType);
        result.put("postCount", posts.size());
        result.put("inferenceBackend", "heuristic_fallback");

        if ("emocc_local".equals(modelType)) {
            Map<String, Object> emocc = new LinkedHashMap<>();
            emocc.put("riskLevel", aggregated.get("riskLevel"));
            emocc.put("riskScore", aggregated.get("riskScore"));
            emocc.put("riskClass", riskClassFromLevel(str(aggregated.get("riskLevel"), "medium")));
            emocc.put("confidence", aggregated.get("confidence"));
            emocc.put("postCount", posts.size());
            emocc.put("classProbs", classProbsFromScore(toDouble(aggregated.get("riskScore"), 0.5)));
            emocc.put("postAttentionScores", buildAttentionScores(posts));
            emocc.put("modelType", "emocc_local");
            emocc.put("modelName", emoccModelName(dataSource));
            emocc.put("datasetKey", dataSource);
            result.put("emoccModelResult", emocc);
            result.put("summary", "Emocc 检测完成（启发式回退），风险等级: " + aggregated.get("riskLevel"));
        } else {
            Map<String, Object> fea = new LinkedHashMap<>();
            fea.put("riskLevel", aggregated.get("riskLevel"));
            fea.put("riskScore", aggregated.get("riskScore"));
            fea.put("confidence", aggregated.get("confidence"));
            fea.put("postCount", posts.size());
            fea.put("modelType", modelType);
            fea.put("modelName", fealearnerModelName(dataSource));
            fea.put("datasetKey", dataSource);
            result.put("fealearnerModelResult", fea);
            result.put("summary", "FeaLearner 检测完成（启发式回退），风险等级: " + aggregated.get("riskLevel"));
        }

        enrichWithLlmClinicalAnalysis(result, posts, modelType, fusionModelId);
        return result;
    }

    /**
     * 调用 inference-python；成功返回已填好本地模型结果的 Map，失败返回 null。
     */
    private Map<String, Object> tryPythonInference(
            String userHash, String dataSource, List<String> posts, String modelType) {
        try {
            if ("emocc_local".equals(modelType)) {
                return pythonInferenceClient.inferEmocc(userHash, dataSource, posts)
                        .map(data -> buildResultFromEmoccSidecar(data, dataSource, posts.size()))
                        .orElse(null);
            }
            if ("fealearner_java".equals(modelType) || "fealearner_local".equals(modelType)) {
                return pythonInferenceClient.inferFealearner(userHash, dataSource)
                        .map(data -> buildResultFromFeaSidecar(data, dataSource, posts.size()))
                        .orElse(null);
            }
        } catch (Exception ex) {
            log.warn("调用推理侧车异常，将回退启发式: {}", ex.getMessage());
        }
        return null;
    }

    private Map<String, Object> buildResultFromEmoccSidecar(
            Map<String, Object> data, String dataSource, int fallbackPostCount) {
        String riskLevel = str(first(data, "riskLevel", "risk_level"), "medium");
        double riskScore = toDouble(first(data, "riskScore", "risk_score"), 0.5);
        double confidence = toDouble(first(data, "confidence"), 0.8);
        int postCount = data.get("postCount") instanceof Number n
                ? n.intValue()
                : fallbackPostCount;

        Map<String, Object> emocc = new LinkedHashMap<>();
        emocc.put("riskLevel", riskLevel);
        emocc.put("riskScore", riskScore);
        emocc.put("riskClass", data.containsKey("riskClass")
                ? data.get("riskClass")
                : riskClassFromLevel(riskLevel));
        emocc.put("confidence", confidence);
        emocc.put("postCount", postCount);
        emocc.put("classProbs", first(data, "classProbs", "class_probs"));
        emocc.put("postAttentionScores", first(data, "postAttentionScores", "post_attention_scores"));
        emocc.put("modelType", "emocc_local");
        emocc.put("modelName", emoccModelName(dataSource));
        emocc.put("datasetKey", dataSource);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskLevel", riskLevel);
        result.put("riskScore", riskScore);
        result.put("confidence", confidence);
        result.put("postCount", postCount);
        result.put("modelType", "emocc_local");
        result.put("inferenceBackend", "python_sidecar");
        result.put("emoccModelResult", emocc);
        result.put("summary", "Emocc 检测完成（神经网络），风险等级: " + riskLevel);
        return result;
    }

    private Map<String, Object> buildResultFromFeaSidecar(
            Map<String, Object> data, String dataSource, int fallbackPostCount) {
        String riskLevel = str(first(data, "riskLevel", "risk_level"), "medium");
        double riskScore = toDouble(first(data, "riskScore", "risk_score"), 0.5);
        double confidence = toDouble(first(data, "confidence"), 0.8);
        int postCount = fallbackPostCount;

        Map<String, Object> fea = new LinkedHashMap<>();
        fea.put("riskLevel", riskLevel);
        fea.put("riskScore", riskScore);
        fea.put("confidence", confidence);
        fea.put("postCount", postCount);
        fea.put("predLabel", first(data, "predLabel", "pred_label"));
        fea.put("probabilities", first(data, "probabilities"));
        fea.put("modelType", "fealearner_local");
        fea.put("modelName", fealearnerModelName(dataSource));
        fea.put("datasetKey", dataSource);
        fea.put("personId", first(data, "personId", "person_id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskLevel", riskLevel);
        result.put("riskScore", riskScore);
        result.put("confidence", confidence);
        result.put("postCount", postCount);
        result.put("modelType", "fealearner_local");
        result.put("inferenceBackend", "python_sidecar");
        result.put("fealearnerModelResult", fea);
        result.put("summary", "FeaLearner 检测完成（神经网络），风险等级: " + riskLevel);
        return result;
    }

    /**
     * 调用 LLM 生成报告页所需的模型分析字段（症状/情绪/风险解读/建议等）。
     * 优先任务上的 fusion 模型，其次模型中心默认 API，再次 app.llm 配置。
     */
    private void enrichWithLlmClinicalAnalysis(
            Map<String, Object> result,
            List<String> posts,
            String modelType,
            Long fusionModelId) {
        String detectorLabel = "emocc_local".equals(modelType) ? "Emocc" : "FeaLearner";
        String systemPrompt = """
                你是一位专业的心理健康评估专家，专注于自杀风险检测。
                请基于本地风险检测模型的输出与用户贴文，生成可直接展示的综合临床评估报告。
                原则：专业、关怀；依据证据；不编造诊断；高风险时优先安全。
                只输出一个合法 JSON 对象。
                """;
        String postsPreview = buildPostsPreview(posts, 15, 80);
        String attentionPreview = "";
        Object emoccObj = result.get("emoccModelResult");
        if (emoccObj instanceof Map<?, ?> emoccMap) {
            Object att = emoccMap.get("postAttentionScores");
            if (att != null) {
                attentionPreview = toJson(att);
            }
        }

        String userPrompt = """
                请基于以下%s模型检测结果，生成专业综合临床评估报告。

                【用户贴文摘要】（共%d条）:
                %s

                【注意力/重要性线索】:
                %s

                【本地模型检测结果】:
                - 风险等级: %s
                - 风险分数: %s
                - 置信度: %s

                【输出格式】（必须严格 JSON）
                {
                  "summary": "综合评估摘要（50字以内）",
                  "symptom_description": "临床症状描述：社交媒体上可见的情绪状态、行为特征与主要困扰",
                  "emotional_analysis": "情绪分析：整体情绪倾向、波动模式与主要情绪类型",
                  "risk_level": "high|medium|low",
                  "risk_score": 0.0,
                  "confidence": 0.0,
                  "risk_interpretation": "风险解读：结合模型结果与贴文解释当前等级含义",
                  "risk_factors": ["风险因素1", "风险因素2"],
                  "protective_factors": ["保护因素1"],
                  "key_highlight": "重点关注：模型最关注的帖子或信号",
                  "professional_advice": "专业建议：给临床/辅导人员",
                  "intervention_suggestion": "干预建议：按风险等级给出可执行动作",
                  "follow_up_suggestion": "随访建议：随访频率与观察点"
                }

                请直接输出 JSON，不要其它说明。
                """.formatted(
                detectorLabel,
                posts.size(),
                postsPreview,
                StringUtils.hasText(attentionPreview) ? attentionPreview : "（无单独注意力明细，请依据贴文重要性判断）",
                str(result.get("riskLevel"), "medium"),
                str(result.get("riskScore"), "0.5"),
                str(result.get("confidence"), "0.8")
        );

        Map<String, Object> call = invokeRiskLlm(fusionModelId, systemPrompt, userPrompt);
        if (!Boolean.TRUE.equals(call.get("success"))) {
            log.warn("风险 LLM 临床分析失败，保留启发式结果: {}", call.get("error"));
            result.put("fusionMethod", "heuristic_only");
            result.put("llmError", str(call.get("error"), "llm_unavailable"));
            return;
        }

        Map<String, Object> parsed = extractJsonMap(str(call.get("response"), ""));
        if (parsed.isEmpty()) {
            log.warn("风险 LLM 返回无法解析为 JSON");
            result.put("fusionMethod", "heuristic_only");
            result.put("llmError", "invalid_json");
            return;
        }

        Map<String, Object> structured = normalizeReportFields(
                parsed,
                str(result.get("riskLevel"), "medium"),
                toDouble(result.get("riskScore"), 0.5),
                toDouble(result.get("confidence"), 0.8),
                str(result.get("summary"), detectorLabel + "检测完成"));
        // 保留本地模型块
        if (result.containsKey("emoccModelResult")) {
            structured.put("emoccModelResult", result.get("emoccModelResult"));
        }
        if (result.containsKey("fealearnerModelResult")) {
            structured.put("fealearnerModelResult", result.get("fealearnerModelResult"));
        }
        structured.put("modelType", modelType);
        structured.put("postCount", result.get("postCount"));
        structured.put("fusionMethod", "llm_analysis");
        structured.put("llmModel", str(call.get("modelName"), llmChatClient.resolveModelName(null)));
        if (!StringUtils.hasText(str(structured.get("llmModel"), ""))) {
            structured.put("llmModel", llmChatClient.resolveModelName(null));
        }
        result.clear();
        result.putAll(structured);
    }

    private Map<String, Object> invokeRiskLlm(Long fusionModelId, String systemPrompt, String userPrompt) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 1) 任务指定融合模型
        if (fusionModelId != null && fusionModelId > 0) {
            Map<String, Object> fusionModel = modelService.getModelRawById(fusionModelId);
            if (fusionModel != null) {
                String provider = str(fusionModel.get("provider"), "");
                Map<String, Object> call = "ollama".equalsIgnoreCase(provider)
                        ? modelService.callOllamaModel(fusionModel, userPrompt, systemPrompt)
                        : modelService.callApiModel(fusionModel, userPrompt, systemPrompt);
                if (Boolean.TRUE.equals(call.get("success"))) {
                    out.put("success", true);
                    out.put("response", call.get("response"));
                    out.put("modelName", firstNonBlank(
                            str(fusionModel.get("model_code"), ""),
                            str(fusionModel.get("ollama_model_name"), ""),
                            str(fusionModel.get("model_name"), ""),
                            llmChatClient.resolveModelName(null)));
                    return out;
                }
                log.warn("融合模型调用失败，尝试默认模型: {}", call.get("error"));
            }
        }
        // 2) 模型中心默认 API / Ollama
        Map<String, Object> defaultApi = findDefaultModel(false);
        if (defaultApi != null) {
            Map<String, Object> call = modelService.callApiModel(defaultApi, userPrompt, systemPrompt);
            if (Boolean.TRUE.equals(call.get("success"))) {
                out.put("success", true);
                out.put("response", call.get("response"));
                out.put("modelName", firstNonBlank(
                        str(defaultApi.get("model_code"), ""),
                        str(defaultApi.get("model_name"), ""),
                        llmChatClient.resolveModelName(null)));
                return out;
            }
        }
        Map<String, Object> defaultOllama = findDefaultModel(true);
        if (defaultOllama != null) {
            Map<String, Object> call = modelService.callOllamaModel(defaultOllama, userPrompt, systemPrompt);
            if (Boolean.TRUE.equals(call.get("success"))) {
                out.put("success", true);
                out.put("response", call.get("response"));
                out.put("modelName", firstNonBlank(
                        str(defaultOllama.get("ollama_model_name"), ""),
                        str(defaultOllama.get("model_code"), ""),
                        str(defaultOllama.get("model_name"), "")));
                return out;
            }
        }
        // 3) app.llm 全局配置
        if (StringUtils.hasText(llmProperties.getApiKey())) {
            try {
                String reply = llmChatClient.chat(
                        systemPrompt,
                        userPrompt,
                        llmProperties.getModel(),
                        llmProperties.getApiKey(),
                        llmProperties.getBaseUrl());
                out.put("success", true);
                out.put("response", reply);
                out.put("modelName", llmChatClient.resolveModelName(llmProperties.getModel()));
                return out;
            } catch (Exception ex) {
                out.put("success", false);
                out.put("error", ex.getMessage());
                return out;
            }
        }
        out.put("success", false);
        out.put("error", "未配置可用 LLM（请在模型中心配置 API Key，或设置 app.llm.api-key / LLM_API_KEY）");
        return out;
    }

    private static String buildPostsPreview(List<String> posts, int maxPosts, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(posts.size(), maxPosts);
        for (int i = 0; i < n; i++) {
            String p = posts.get(i) == null ? "" : posts.get(i).replace('\n', ' ').trim();
            if (p.length() > maxChars) {
                p = p.substring(0, maxChars) + "...";
            }
            sb.append("- ").append(p).append('\n');
        }
        return sb.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
    }

    private Map<String, Object> aggregateHeuristic(List<String> posts) {
        double scoreSum = 0;
        Set<String> factors = new LinkedHashSet<>();
        List<String> summaries = new ArrayList<>();
        for (String content : posts) {
            Map<String, Object> evidence = DatasetTextAnalysis.analyzeRiskEvidence(content);
            double importance = DatasetTextAnalysis.calculateImportanceScore(content);
            scoreSum += importance;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> domains = evidence.get("evidence_domains") instanceof List
                    ? (List<Map<String, Object>>) evidence.get("evidence_domains")
                    : List.of();
            for (Map<String, Object> d : domains) {
                Object label = d.get("label");
                if (label != null && StringUtils.hasText(String.valueOf(label))) {
                    factors.add(String.valueOf(label));
                }
            }
            Object es = evidence.get("evidence_summary");
            if (es != null && !"未识别到明显风险词汇。".equals(String.valueOf(es))) {
                summaries.add(String.valueOf(es));
            }
        }
        double avg = posts.isEmpty() ? 0.2 : scoreSum / posts.size();
        avg = Math.max(0.0, Math.min(avg, 1.0));
        String level = DatasetTextAnalysis.importanceLevel(avg);
        double confidence = Math.max(0.55, Math.min(0.95, 0.5 + avg * 0.45));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskLevel", level);
        result.put("riskScore", round4(avg));
        result.put("confidence", round4(confidence));
        result.put("summary", summaries.isEmpty()
                ? "基于 " + posts.size() + " 条帖子的启发式风险证据聚合，未发现显著高危线索。"
                : "基于帖子风险证据聚合：" + String.join("；", summaries.subList(0, Math.min(3, summaries.size()))));
        result.put("postCount", posts.size());
        result.put("modelType", "heuristic");
        result.put("riskFactors", new ArrayList<>(factors));
        result.put("protectiveFactors", List.of());
        result.put("symptomDescription", "");
        result.put("emotionalAnalysis", "");
        result.put("riskInterpretation", "");
        result.put("professionalAdvice", "");
        result.put("interventionSuggestion", "");
        result.put("followUpSuggestion", "");
        return result;
    }

    // ======================== DB helpers ========================

    private long insertRiskTask(Map<String, Object> fields, boolean withProgressFields) {
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("task_code", fields.get("task_code"));
        db.put("task_name", fields.get("task_name"));
        db.put("task_description", fields.get("task_description"));
        db.put("task_mode", fields.get("task_mode"));
        db.put("task_type_id", fields.get("task_type_id"));
        db.put("user_hash", fields.get("user_hash"));
        db.put("data_source", fields.get("data_source"));
        db.put("post_count", fields.get("post_count"));
        db.put("status", fields.get("status"));
        db.put("progress", fields.get("progress"));
        if (withProgressFields) {
            db.put("detection_status", fields.getOrDefault("detection_status", "pending"));
            db.put("fusion_status", fields.getOrDefault("fusion_status", "pending"));
            db.put("detection_progress", fields.getOrDefault("detection_progress", 0));
            db.put("fusion_progress", fields.getOrDefault("fusion_progress", 0));
            Object sm = fields.get("single_model_id");
            if (sm instanceof Number n && n.longValue() > 0) {
                db.put("single_model_id", n.longValue());
            }
            Object pt = fields.get("single_prompt_template_id");
            if (pt instanceof Number n && n.longValue() > 0) {
                db.put("single_prompt_template_id", n.longValue());
            }
        }
        db.put("detection_model_configs", toJson(fields.get("detection_model_configs")));
        Object fm = fields.get("fusion_model_id");
        if (fm instanceof Number n && n.longValue() > 0) {
            db.put("fusion_model_id", n.longValue());
        }

        String columns = String.join(", ", db.keySet());
        String placeholders = String.join(", ", db.keySet().stream().map(k -> "?").toList());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO risk_detection_tasks (" + columns + ") VALUES (" + placeholders + ")",
                        Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                for (Object v : db.values()) {
                    ps.setObject(i++, v);
                }
                return ps;
            }, keyHolder);
        } catch (Exception ex) {
            log.warn("插入 risk_detection_tasks 失败: {}", ex.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "保存任务失败: " + ex.getMessage());
        }

        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        try {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM risk_detection_tasks WHERE task_code = ? ORDER BY id DESC LIMIT 1",
                    Long.class,
                    fields.get("task_code"));
            if (id != null) {
                return id;
            }
        } catch (Exception ex) {
            log.warn("回查任务主键失败: {}", ex.getMessage());
        }
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "任务已写入但无法获得有效编号");
    }

    private Map<String, Object> getTaskStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", 0);
        stats.put("pending", 0);
        stats.put("running", 0);
        stats.put("completed", 0);
        stats.put("failed", 0);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT
                                COUNT(*) as total,
                                SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending,
                                SUM(CASE WHEN status = 'running' THEN 1 ELSE 0 END) as running,
                                SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as completed,
                                SUM(CASE WHEN status IN ('failed', 'cancelled') THEN 1 ELSE 0 END) as failed
                            FROM risk_detection_tasks
                            WHERE (task_mode != 'single' OR JSON_UNQUOTE(JSON_EXTRACT(detection_model_configs, '$.model_type')) != 'emocc_local')
                            """);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                stats.put("total", toLong(row.get("total")));
                stats.put("pending", toLong(row.get("pending")));
                stats.put("running", toLong(row.get("running")));
                stats.put("completed", toLong(row.get("completed")));
                stats.put("failed", toLong(row.get("failed")));
            }
        } catch (Exception ex) {
            log.warn("统计风险任务失败: {}", ex.getMessage());
        }
        return stats;
    }

    private Map<String, Object> findTaskRow(String taskId, List<String> prefixes) {
        String code = taskId == null ? "" : taskId.trim();
        if (prefixes == null || prefixes.isEmpty()) {
            if (code.startsWith("RISK-")) {
                code = code.substring("RISK-".length());
            }
        } else {
            for (String p : prefixes) {
                if (code.startsWith(p)) {
                    code = code.substring(p.length());
                    break;
                }
            }
        }
        ResolvedId resolved = resolveRaw(code);
        try {
            List<Map<String, Object>> rows;
            if (resolved.numericId != null) {
                rows = jdbcTemplate.queryForList("SELECT * FROM risk_detection_tasks WHERE id = ?", resolved.numericId);
            } else {
                rows = jdbcTemplate.queryForList("SELECT * FROM risk_detection_tasks WHERE task_code = ?", resolved.code);
            }
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            log.warn("查询任务失败: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> findTypedTask(String taskId, String prefix, String typeFilter) {
        String raw = taskId == null ? "" : taskId.trim();
        try {
            // 数字 id
            try {
                long id = Long.parseLong(raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw);
                List<Map<String, Object>> byId = jdbcTemplate.queryForList(
                        "SELECT * FROM risk_detection_tasks WHERE id = ? AND " + typeFilter, id);
                if (!byId.isEmpty()) {
                    return byId.get(0);
                }
            } catch (NumberFormatException ignored) {
                // fall through to code lookup
            }

            // task_code：Emocc 存裸 code；FeaLearner 存 FEA- 前缀，两种都试
            List<String> candidates = new ArrayList<>();
            candidates.add(raw);
            if (StringUtils.hasText(prefix) && raw.startsWith(prefix)) {
                candidates.add(raw.substring(prefix.length()));
            } else if (StringUtils.hasText(prefix) && !raw.startsWith(prefix)) {
                candidates.add(prefix + raw);
            }
            for (String code : candidates) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT * FROM risk_detection_tasks WHERE task_code = ? AND " + typeFilter, code);
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            }
            return null;
        } catch (Exception ex) {
            log.warn("查询类型任务失败: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> listTypedTasks(
            int page, int limit, String typeFilter, String codePrefix, String taskMode, boolean prefixCode) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int offset = (safePage - 1) * safeLimit;
        long total = 0;
        List<Map<String, Object>> rows = List.of();
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_detection_tasks WHERE " + typeFilter, Long.class);
        total = cnt == null ? 0 : cnt;
        rows = jdbcTemplate.queryForList(
                """
                        SELECT id, task_code, task_name, task_description, task_mode,
                               user_hash, data_source, post_count, progress, status,
                               detection_model_configs, result_summary,
                               created_at, started_at, completed_at, processing_time_ms
                        FROM risk_detection_tasks
                        WHERE
                        """ + typeFilter + """
                         ORDER BY created_at DESC
                        LIMIT ? OFFSET ?
                        """,
                safeLimit, offset);

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String modelName = "emocc".equals(taskMode)
                    ? emoccModelName(str(row.get("data_source"), "reddit"))
                    : fealearnerModelName(str(row.get("data_source"), "reddit"));
            Map<String, Object> view = toTypedTaskView(row, codePrefix, taskMode, modelName);
            if (!prefixCode) {
                view.put("taskCode", row.get("task_code"));
            }
            tasks.add(view);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", tasks);
        data.put("total", total);
        data.put("page", safePage);
        data.put("limit", safeLimit);
        return data;
    }

    private void deleteTypedTask(String taskId, String prefix, String typeFilter) {
        Map<String, Object> existing = findTypedTask(taskId, prefix, typeFilter);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在或已删除");
        }
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM risk_detection_tasks WHERE id = ? AND " + typeFilter, toLong(existing.get("id")));
            if (deleted <= 0) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在或已删除");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "删除失败: " + ex.getMessage());
        }
    }

    private void markRunning(long id) {
        jdbcTemplate.update(
                "UPDATE risk_detection_tasks SET status = 'running', progress = 0, started_at = NOW(), error_message = NULL WHERE id = ?",
                id);
    }

    private void markCompleted(long id, int processingTimeMs, Map<String, Object> resultSummary) {
        jdbcTemplate.update(
                """
                        UPDATE risk_detection_tasks
                        SET status = 'completed', progress = 100,
                            completed_at = NOW(), processing_time_ms = ?,
                            result_summary = ?, detection_status = 'completed', fusion_status = 'completed',
                            detection_progress = 100, fusion_progress = 100,
                            error_message = NULL
                        WHERE id = ?
                        """,
                processingTimeMs, toJson(resultSummary), id);
    }

    private void markFailed(long id, int processingTimeMs, String error) {
        try {
            jdbcTemplate.update(
                    """
                            UPDATE risk_detection_tasks
                            SET status = 'failed', processing_time_ms = ?, error_message = ?, completed_at = NOW()
                            WHERE id = ?
                            """,
                    processingTimeMs, error == null ? "" : error.substring(0, Math.min(error.length(), 1000)), id);
        } catch (Exception ex) {
            log.warn("更新失败状态出错: {}", ex.getMessage());
        }
    }

    // ======================== mapping / util ========================

    private Map<String, Object> toDetectionTask(Map<String, Object> t) {
        Map<String, Object> configs = parseJsonObject(t.get("detection_model_configs"));
        String modelName = str(configs.get("model_name"), "未知模型");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", t.get("id"));
        item.put("taskCode", "RISK-" + str(t.get("task_code"), ""));
        item.put("taskName", str(t.get("task_name"), "风险检测任务"));
        item.put("taskDescription", str(t.get("task_description"), ""));
        item.put("taskMode", str(t.get("task_mode"), "single"));
        item.put("taskTypeId", t.get("task_type_id") == null ? 1 : t.get("task_type_id"));
        item.put("archiveId", t.get("archive_id"));
        item.put("userHash", str(t.get("user_hash"), ""));
        item.put("dataSource", str(t.get("data_source"), ""));
        item.put("postCount", t.get("post_count") == null ? 0 : t.get("post_count"));
        item.put("singleModelId", t.get("single_model_id"));
        item.put("modelName", modelName);
        item.put("singlePromptTemplateId", t.get("single_prompt_template_id"));
        item.put("detectionModelConfigs", configs.isEmpty() ? t.get("detection_model_configs") : configs);
        item.put("fusionModelId", t.get("fusion_model_id"));
        item.put("fusionPromptTemplateId", t.get("fusion_prompt_template_id"));
        item.put("progress", t.get("progress") == null ? 0 : t.get("progress"));
        item.put("status", str(t.get("status"), "pending"));
        item.put("detectionProgress", t.get("detection_progress") == null ? 0 : t.get("detection_progress"));
        item.put("fusionProgress", t.get("fusion_progress") == null ? 0 : t.get("fusion_progress"));
        item.put("detectionStatus", str(t.get("detection_status"), "pending"));
        item.put("fusionStatus", str(t.get("fusion_status"), "pending"));
        item.put("resultSummary", parseJsonObject(t.get("result_summary")));
        item.put("errorMessage", t.get("error_message"));
        item.put("startedAt", formatTs(t.get("started_at")));
        item.put("completedAt", formatTs(t.get("completed_at")));
        item.put("processingTimeMs", t.get("processing_time_ms"));
        item.put("createdAt", formatTs(t.get("created_at")));
        return item;
    }

    private Map<String, Object> toTypedTaskView(
            Map<String, Object> t, String codePrefix, String taskMode, String modelName) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", t.get("id"));
        String code = str(t.get("task_code"), "");
        item.put("taskCode", StringUtils.hasText(codePrefix) ? codePrefix + code : code);
        item.put("taskName", str(t.get("task_name"), taskMode + "检测任务"));
        item.put("taskDescription", str(t.get("task_description"), ""));
        item.put("taskMode", taskMode);
        item.put("userHash", str(t.get("user_hash"), ""));
        item.put("dataSource", str(t.get("data_source"), ""));
        item.put("postCount", t.get("post_count") == null ? 0 : t.get("post_count"));
        item.put("modelName", modelName);
        item.put("progress", t.get("progress") == null ? 0 : t.get("progress"));
        item.put("status", str(t.get("status"), "pending"));
        item.put("resultSummary", parseJsonObject(t.get("result_summary")));
        item.put("createdAt", formatTs(t.get("created_at")));
        item.put("startedAt", formatTs(t.get("started_at")));
        item.put("completedAt", formatTs(t.get("completed_at")));
        item.put("processingTimeMs", t.get("processing_time_ms"));
        return item;
    }

    private int countUserPosts(String dataSource, String userHash) {
        if (!StringUtils.hasText(userHash) || !StringUtils.hasText(dataSource)) {
            return 0;
        }
        try {
            Map<String, Object> page = datasetService.getUserPosts(dataSource, userHash, 1, 50);
            Object total = page.get("total");
            if (total instanceof Number n && n.longValue() > 0) {
                return (int) Math.min(n.longValue(), Integer.MAX_VALUE);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> posts = page.get("posts") instanceof List
                    ? (List<Map<String, Object>>) page.get("posts")
                    : List.of();
            return posts.size();
        } catch (Exception ex) {
            log.warn("统计用户帖子失败: {}", ex.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> loadPostTexts(String dataSource, String userHash) {
        List<String> texts = new ArrayList<>();
        try {
            Map<String, Object> page = datasetService.getUserPosts(dataSource, userHash, 1, 50);
            List<Map<String, Object>> posts = page.get("posts") instanceof List
                    ? (List<Map<String, Object>>) page.get("posts")
                    : List.of();
            for (Map<String, Object> post : posts) {
                Object c = post.get("content");
                if (c != null && StringUtils.hasText(String.valueOf(c))) {
                    texts.add(String.valueOf(c));
                }
            }
        } catch (Exception ex) {
            log.warn("加载用户帖子失败: {}", ex.getMessage());
        }
        return texts;
    }

    private Map<String, Object> findDefaultModel(boolean ollama) {
        try {
            List<Map<String, Object>> rows = ollama
                    ? jdbcTemplate.queryForList(
                    "SELECT * FROM models WHERE model_type = 'ollama' AND status = 'active' LIMIT 1")
                    : jdbcTemplate.queryForList(
                    """
                            SELECT * FROM models
                            WHERE model_type = 'api' AND status = 'active'
                              AND (provider = 'dashscope' OR model_code LIKE '%qwen%')
                            ORDER BY id ASC LIMIT 1
                            """);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            log.warn("查找默认模型失败: {}", ex.getMessage());
            return null;
        }
    }

    private String buildRiskPrompt(List<String> posts, String userHash, String dataSource) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下用户社交媒体帖子，评估自杀风险，并只返回 JSON。\n");
        sb.append("userHash=").append(userHash).append(", dataSource=").append(dataSource).append("\n");
        sb.append("帖子内容：\n");
        int n = Math.min(posts.size(), 20);
        for (int i = 0; i < n; i++) {
            sb.append("- ").append(posts.get(i), 0, Math.min(posts.get(i).length(), 300)).append("\n");
        }
        sb.append("""
                JSON结构：
                {
                  "risk_level": "low|medium|high",
                  "risk_score": 0.0,
                  "confidence": 0.0,
                  "summary": "一句话总结",
                  "risk_factors": ["因素1", "因素2"],
                  "protective_factors": [],
                  "professional_advice": "",
                  "intervention_suggestion": "",
                  "follow_up_suggestion": ""
                }
                """);
        return sb.toString();
    }

    private Map<String, Object> normalizeReportFields(
            Map<String, Object> payload,
            String fallbackLevel,
            double fallbackScore,
            double fallbackConfidence,
            String fallbackSummary) {
        Object summaryRaw = first(payload, "summary", "reason", "analysis", "risk_interpretation");
        String summaryText = summaryRaw == null ? fallbackSummary : String.valueOf(summaryRaw).trim();
        if (!StringUtils.hasText(summaryText)) {
            summaryText = fallbackSummary;
        }
        double confidence = toDouble(payload.get("confidence"), fallbackConfidence);
        if (confidence > 1) {
            confidence = confidence / 100.0;
        }
        confidence = Math.max(0.0, Math.min(confidence, 1.0));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("riskLevel", normalizeRiskLevel(first(payload, "risk_level", "riskLevel"), fallbackLevel));
        out.put("riskScore", Math.max(0.0, Math.min(toDouble(first(payload, "risk_score", "riskScore"), fallbackScore), 1.0)));
        out.put("confidence", round4(confidence));
        out.put("summary", summaryText);
        out.put("symptomDescription", str(first(payload, "symptom_description", "symptomDescription"), ""));
        out.put("emotionalAnalysis", str(first(payload, "emotional_analysis", "emotionalAnalysis"), ""));
        out.put("riskInterpretation", str(first(payload, "risk_interpretation", "riskInterpretation"), ""));
        out.put("keyHighlight", str(first(payload, "key_highlight", "keyHighlight"), ""));
        out.put("riskFactors", normalizeTextList(first(payload, "risk_factors", "key_risk_factors", "riskFactors")));
        out.put("protectiveFactors", normalizeTextList(first(payload, "protective_factors", "protectiveFactors")));
        out.put("professionalAdvice", str(first(payload, "professional_advice", "professionalAdvice"), ""));
        out.put("interventionSuggestion", str(first(payload, "intervention_suggestion", "interventionSuggestion"), ""));
        out.put("followUpSuggestion", str(first(payload, "follow_up_suggestion", "followUpSuggestion"), ""));
        out.put("llmResponse", str(first(payload, "llm_response", "llmResponse", "response"), ""));
        out.put("llmModel", str(first(payload, "llmModel", "model"), ""));
        return out;
    }

    private String inferReportModelName(Map<String, Object> task, Map<String, Object> resultSummary) {
        Map<String, Object> configs = parseJsonObject(task.get("detection_model_configs"));
        String modelType = str(configs.get("model_type"), "");
        String baseName = str(configs.get("model_name"), "");
        if (resultSummary.containsKey("emoccModelResult") || "emocc_local".equals(modelType)) {
            String llm = str(resultSummary.get("llmModel"), "");
            String base = emoccModelName(str(task.get("data_source"), "reddit"));
            return StringUtils.hasText(llm) ? base + " + " + llm : base + " + heuristic";
        }
        if (resultSummary.containsKey("fealearnerModelResult")
                || "fealearner_java".equals(modelType)
                || "fealearner_local".equals(modelType)) {
            String llm = str(resultSummary.get("llmModel"), "");
            String base = fealearnerModelName(str(task.get("data_source"), "reddit"));
            return StringUtils.hasText(llm) ? base + " + " + llm : base;
        }
        return StringUtils.hasText(baseName) ? baseName : str(resultSummary.get("modelType"), "risk-model");
    }

    private List<Map<String, Object>> buildAttentionScores(List<String> posts) {
        List<Map<String, Object>> scores = new ArrayList<>();
        for (int i = 0; i < Math.min(posts.size(), 10); i++) {
            String text = posts.get(i);
            double score = DatasetTextAnalysis.calculateImportanceScore(text);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("postIndex", i);
            item.put("attentionScore", round4(score));
            item.put("textPreview", text.substring(0, Math.min(text.length(), 80)));
            scores.add(item);
        }
        scores.sort((a, b) -> Double.compare(toDouble(b.get("attentionScore"), 0), toDouble(a.get("attentionScore"), 0)));
        return scores;
    }

    private List<Double> classProbsFromScore(double score) {
        double high = Math.max(0, Math.min(1, score));
        double mid = Math.max(0, Math.min(1, 1 - Math.abs(score - 0.5) * 2)) * 0.4;
        double low = Math.max(0, 1 - high - mid);
        double sum = low + mid + high;
        if (sum <= 0) {
            return List.of(0.34, 0.33, 0.33);
        }
        return List.of(round4(low / sum), round4(mid / sum), round4(high / sum));
    }

    private int riskClassFromLevel(String level) {
        return switch (normalizeRiskLevel(level, "medium")) {
            case "high" -> 2;
            case "low" -> 0;
            default -> 1;
        };
    }

    private String buildClinicalReportHtml(
            Map<String, Object> task, Map<String, Object> rs, Map<String, Object> emocc) {
        String riskLevel = str(rs.get("riskLevel"), "medium");
        double riskScore = toDouble(rs.get("riskScore"), 0.5);
        Object confObj = rs.get("confidence");
        int confidencePct;
        if (confObj instanceof Number n) {
            double c = n.doubleValue();
            confidencePct = (int) Math.round(c > 1 ? c : c * 100);
        } else {
            confidencePct = 80;
        }
        String summary = escapeHtml(str(rs.get("summary"), "暂无摘要"));
        String color = switch (riskLevel) {
            case "high" -> "#ef4444";
            case "low" -> "#22c55e";
            default -> "#f59e0b";
        };
        String label = switch (riskLevel) {
            case "high" -> "高风险";
            case "low" -> "低风险";
            default -> "中风险";
        };
        String factors = renderHtmlList(normalizeTextList(rs.get("riskFactors")));
        String protective = renderHtmlList(normalizeTextList(rs.get("protectiveFactors")));
        String userHash = escapeHtml(str(task.get("userHash"), ""));
        String completedAt = escapeHtml(str(task.get("completedAt"), LocalDateTime.now().toString()));
        String llmModel = escapeHtml(str(rs.get("llmModel"), ""));

        StringBuilder emoccSection = new StringBuilder();
        if (emocc != null && !emocc.isEmpty()) {
            emoccSection.append("<div class=\"section\"><div class=\"section-title\">Emocc 模型检测详情</div>")
                    .append("<p>原始等级: ").append(escapeHtml(str(emocc.get("riskLevel"), "")))
                    .append(" / 分数: ").append(toDouble(emocc.get("riskScore"), 0))
                    .append(" / 模型: ").append(escapeHtml(str(emocc.get("modelName"), "")))
                    .append("</p></div>");
        }

        return """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="UTF-8"><title>自杀风险临床评估报告</title>
                <style>
                body{font-family:"Microsoft YaHei",Arial,sans-serif;color:#333;margin:0;padding:24px;background:#fff}
                .page{max-width:800px;margin:0 auto}
                .banner{background:%s;color:#fff;border-radius:8px;padding:20px;text-align:center;margin-bottom:16px}
                .section{margin:16px 0}.section-title{font-weight:bold;border-bottom:1px solid #eee;padding-bottom:4px;margin-bottom:8px}
                .meta{color:#888;font-size:12px}
                .footer{margin-top:32px;font-size:11px;color:#aaa;text-align:center}
                </style></head><body><div class="page">
                <h1>自杀风险临床评估报告</h1>
                <div class="meta">用户哈希: %s | 帖子数: %s | 评估时间: %s</div>
                <div class="banner"><div style="font-size:28px;font-weight:bold">%s</div>
                <div>风险分数: %.2f / 置信度: %d%% / 融合模型: %s</div></div>
                <div class="section"><div class="section-title">综合评估摘要</div><p>%s</p></div>
                %s
                <div class="section"><div class="section-title">风险因素</div>%s</div>
                <div class="section"><div class="section-title">保护因素</div>%s</div>
                <div class="footer">本报告由 VIS4SRD 生成，仅供参考，最终诊断应由专业医生做出。</div>
                </div></body></html>
                """.formatted(
                color, userHash, str(task.get("postCount"), "0"), completedAt,
                label, riskScore, confidencePct, llmModel, summary,
                emoccSection, factors.isEmpty() ? "<p>无</p>" : factors,
                protective.isEmpty() ? "<p>无</p>" : protective);
    }

    private String renderHtmlList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<ul>");
        for (String item : items) {
            sb.append("<li>").append(escapeHtml(item)).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String generateTaskCode(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return prefix + "_" + ts + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private ResolvedId resolveTaskId(String taskId, String prefix) {
        String code = taskId == null ? "" : taskId.trim();
        if (prefix != null && code.startsWith(prefix)) {
            code = code.substring(prefix.length());
        }
        return resolveRaw(code);
    }

    private ResolvedId resolveRaw(String code) {
        try {
            return new ResolvedId(Long.parseLong(code), code);
        } catch (NumberFormatException ex) {
            return new ResolvedId(null, code);
        }
    }

    private Map<String, Object> parseJsonObject(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> extractJsonMap(String text) {
        if (!StringUtils.hasText(text)) {
            return new LinkedHashMap<>();
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "").trim();
        try {
            return objectMapper.readValue(cleaned, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            // fall through
        }
        Matcher m = JSON_OBJECT.matcher(cleaned);
        if (m.find()) {
            try {
                return objectMapper.readValue(m.group(), new TypeReference<>() {
                });
            } catch (Exception ignored) {
                // fall through
            }
        }
        return new LinkedHashMap<>();
    }

    private String toJson(Object value) {
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

    private List<String> normalizeTextList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item).trim())) {
                    out.add(String.valueOf(item).trim());
                }
            }
            return out;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? new ArrayList<>(List.of(text)) : new ArrayList<>();
    }

    private String normalizeRiskLevel(Object level, String defaultLevel) {
        if (level == null) {
            return defaultLevel;
        }
        String text = String.valueOf(level).trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return switch (text) {
            case "critical", "very_high", "high" -> "high";
            case "medium_high", "medium", "moderate" -> "medium";
            case "low", "normal", "none" -> "low";
            default -> defaultLevel;
        };
    }

    private String emoccModelName(String dataset) {
        return switch (dataset == null ? "" : dataset.toLowerCase(Locale.ROOT)) {
            case "weibo" -> "Emocc-Weibo";
            case "bigdata" -> "Emocc-Bigdata";
            case "sigir" -> "Emocc-SIGIR";
            default -> "Emocc-Reddit";
        };
    }

    private String fealearnerModelName(String dataset) {
        return "FeaLearner-" + (StringUtils.hasText(dataset) ? dataset : "reddit");
    }

    private String formatTs(Object value) {
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

    private String formatTsSpace(Object value) {
        String iso = formatTs(value);
        if (iso == null) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return iso.replace('T', ' ');
    }

    private Object first(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            if (body.containsKey(key) && body.get(key) != null) {
                return body.get(key);
            }
        }
        return null;
    }

    private String str(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value);
        return s.isBlank() && defaultValue != null ? defaultValue : s;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Long toLongObj(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            if (value instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private record ResolvedId(Long numericId, String code) {
    }
}
