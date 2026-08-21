package com.vis4srd.scale.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.scale.catalog.ScaleCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ScaleService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ScaleCatalog scaleCatalog;

    public ScaleService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ScaleCatalog scaleCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.scaleCatalog = scaleCatalog;
    }

    public Map<String, Object> getTasks(
            String status, String userHash, Integer archiveId, String dataSource, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);

        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            where.append(" AND st.status = ?");
            params.add(status);
        }
        if (StringUtils.hasText(userHash)) {
            where.append(" AND st.user_hash = ?");
            params.add(userHash);
        }
        if (archiveId != null) {
            where.append(" AND st.archive_id = ?");
            params.add(archiveId);
        }
        if (StringUtils.hasText(dataSource)) {
            where.append(" AND st.data_source = ?");
            params.add(dataSource);
        }

        long total = 0;
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM scale_tasks st " + where, Long.class, params.toArray());
            total = cnt == null ? 0 : cnt;
        } catch (Exception ex) {
            log.warn("统计量表任务失败: {}", ex.getMessage());
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pending", 0);
        stats.put("inProgress", 0);
        stats.put("completed", 0);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT status, COUNT(*) AS count FROM scale_tasks st " + where + " GROUP BY status",
                    params.toArray());
            for (Map<String, Object> row : rows) {
                String s = String.valueOf(row.get("status"));
                int c = ((Number) row.get("count")).intValue();
                if ("pending".equals(s)) {
                    stats.put("pending", c);
                } else if ("in_progress".equals(s)) {
                    stats.put("inProgress", c);
                } else if ("completed".equals(s)) {
                    stats.put("completed", c);
                }
            }
        } catch (Exception ex) {
            log.debug("统计量表状态失败: {}", ex.getMessage());
        }

        List<Map<String, Object>> tasks = new ArrayList<>();
        try {
            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add((safePage - 1) * safeSize);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT st.*, pa.risk_level AS archive_risk_level
                            FROM scale_tasks st
                            LEFT JOIN psychological_archives pa ON pa.id = st.archive_id
                            """ + where + """
                             ORDER BY st.created_at DESC
                            LIMIT ? OFFSET ?
                            """,
                    queryParams.toArray());
            for (Map<String, Object> row : rows) {
                tasks.add(normalizeTaskRow(row));
            }
        } catch (Exception ex) {
            log.warn("查询量表任务失败: {}", ex.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", tasks.stream().map(this::toResponse).toList());
        data.put("stats", stats);
        return data;
    }

    public Map<String, Object> getTaskById(long taskId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT st.*, pa.risk_level AS archive_risk_level
                            FROM scale_tasks st
                            LEFT JOIN psychological_archives pa ON pa.id = st.archive_id
                            WHERE st.id = ?
                            """,
                    taskId);
            if (rows.isEmpty()) {
                return null;
            }
            return normalizeTaskRow(rows.get(0));
        } catch (Exception ex) {
            log.warn("查询任务详情失败: {}", ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> findArchiveUser(String userHash, Integer archiveId, String dataSource) {
        StringBuilder where = new StringBuilder("WHERE pa.user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userHash);
        if (archiveId != null) {
            where.append(" AND pa.id = ?");
            params.add(archiveId);
        }
        if (StringUtils.hasText(dataSource)) {
            where.append(" AND pa.dataset_source = ?");
            params.add(dataSource);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT pa.id, pa.user_id, pa.dataset_source, pa.risk_level,
                                   dp.display_name AS dataset_display_name
                            FROM psychological_archives pa
                            LEFT JOIN dataset_profile dp ON dp.dataset_key = pa.dataset_source
                            """ + where + """
                             ORDER BY pa.import_timestamp DESC, pa.id DESC
                            LIMIT 1
                            """,
                    params.toArray());
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    @Transactional
    public long createTask(Map<String, Object> taskData) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                            INSERT INTO scale_tasks
                            (task_name, user_id, user_hash, user_alias, archive_id,
                             data_source, data_source_label, scale_id, scale_code,
                             scale_name, scale_full_name, scale_category,
                             scale_color, scale_bg_color, status, progress,
                             total_questions, answered_questions, answers,
                             total_score, risk_level, started_at, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                    'pending', 0, ?, 0, NULL, NULL, NULL, NULL, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, taskData.getOrDefault("task_name", "量表评估任务"));
            ps.setObject(i++, taskData.get("user_id"));
            ps.setObject(i++, taskData.get("user_hash"));
            ps.setObject(i++, taskData.get("user_alias"));
            ps.setObject(i++, taskData.get("archive_id"));
            ps.setObject(i++, taskData.get("data_source"));
            ps.setObject(i++, taskData.get("data_source_label"));
            ps.setObject(i++, taskData.get("scale_id"));
            ps.setObject(i++, taskData.get("scale_code"));
            ps.setObject(i++, taskData.get("scale_name"));
            ps.setObject(i++, taskData.get("scale_full_name"));
            ps.setObject(i++, taskData.get("scale_category"));
            ps.setObject(i++, taskData.get("scale_color"));
            ps.setObject(i++, taskData.get("scale_bg_color"));
            ps.setObject(i++, taskData.getOrDefault("total_questions", 0));
            ps.setObject(i, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("创建量表任务失败");
        }
        return key.longValue();
    }

    @Transactional
    public boolean submitTask(
            long taskId,
            List<Map<String, Object>> answers,
            int totalScore,
            String riskLevel,
            Object assessmentResult) {
        Map<String, Object> task = getTaskById(taskId);
        if (task == null) {
            return false;
        }
        int questionCount = toInt(task.get("total_questions"));
        int answered = answers == null ? 0 : answers.size();
        int progress = questionCount > 0 ? Math.min(100, answered * 100 / questionCount) : 0;
        LocalDateTime now = LocalDateTime.now();
        try {
            String answersJson = objectMapper.writeValueAsString(answers);
            String resultJson = objectMapper.writeValueAsString(assessmentResult);
            int updated = jdbcTemplate.update(
                    """
                            UPDATE scale_tasks SET
                                answers = ?, total_score = ?, risk_level = ?,
                                assessment_result = ?, answered_questions = ?,
                                progress = ?, status = 'completed',
                                started_at = COALESCE(started_at, ?),
                                completed_at = ?
                            WHERE id = ?
                            """,
                    answersJson, totalScore, riskLevel, resultJson, answered, progress,
                    Timestamp.valueOf(now), Timestamp.valueOf(now), taskId);
            return updated > 0;
        } catch (Exception ex) {
            throw new BusinessException("提交量表答案失败: " + ex.getMessage());
        }
    }

    @Transactional
    public boolean deleteTask(long taskId) {
        return jdbcTemplate.update("DELETE FROM scale_tasks WHERE id = ?", taskId) > 0;
    }

    public Map<String, Object> toResponse(Map<String, Object> taskRow) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", taskRow.get("id"));
        r.put("taskName", taskRow.get("task_name"));
        r.put("userId", taskRow.get("user_id"));
        r.put("userHash", taskRow.get("user_hash"));
        r.put("userAlias", taskRow.get("user_alias"));
        r.put("archiveId", taskRow.get("archive_id"));
        r.put("archiveRiskLevel", taskRow.get("archive_risk_level"));
        r.put("dataSource", taskRow.get("data_source"));
        r.put("dataSourceLabel", taskRow.get("data_source_label"));
        r.put("scaleId", taskRow.get("scale_id"));
        r.put("scaleCode", taskRow.get("scale_code"));
        r.put("scaleName", taskRow.get("scale_name"));
        r.put("scaleFullName", taskRow.get("scale_full_name"));
        r.put("scaleCategory", taskRow.get("scale_category"));
        r.put("scaleColor", taskRow.get("scale_color"));
        r.put("scaleBgColor", taskRow.get("scale_bg_color"));
        r.put("status", taskRow.get("status"));
        r.put("progress", taskRow.get("progress") == null ? 0 : taskRow.get("progress"));
        r.put("totalQuestions", taskRow.get("total_questions") == null ? 0 : taskRow.get("total_questions"));
        r.put("answeredQuestions", taskRow.get("answered_questions") == null ? 0 : taskRow.get("answered_questions"));
        r.put("answers", taskRow.get("answers"));
        r.put("totalScore", taskRow.get("total_score"));
        r.put("riskLevel", taskRow.get("risk_level"));
        r.put("assessmentResult", taskRow.get("assessment_result"));
        r.put("startedAt", formatTs(taskRow.get("started_at")));
        r.put("completedAt", formatTs(taskRow.get("completed_at")));
        r.put("expiredAt", formatTs(taskRow.get("expired_at")));
        r.put("createdAt", formatTs(taskRow.get("created_at")));
        return r;
    }

    public ScaleCatalog catalog() {
        return scaleCatalog;
    }

    private Map<String, Object> normalizeTaskRow(Map<String, Object> row) {
        Map<String, Object> task = new LinkedHashMap<>(row);
        if (task.get("id") instanceof Number n) {
            task.put("id", n.longValue());
        }
        task.put("answers", parseJson(task.get("answers"), List.of()));
        Object assessment = task.get("assessment_result");
        if (assessment instanceof String s && StringUtils.hasText(s)) {
            task.put("assessment_result", parseJson(s, assessment));
        }
        return task;
    }

    private Object parseJson(Object raw, Object fallback) {
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof String s)) {
            return raw;
        }
        if (!StringUtils.hasText(s)) {
            return fallback;
        }
        try {
            if (s.trim().startsWith("[")) {
                return objectMapper.readValue(s, new TypeReference<List<Map<String, Object>>>() {
                });
            }
            return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String formatTs(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        return String.valueOf(v);
    }
}
