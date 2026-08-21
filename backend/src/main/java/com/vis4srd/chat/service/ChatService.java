package com.vis4srd.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 智能问答：会话 / 消息 CRUD、推荐问题（对齐 Python chat_service.py）。
 */
@Slf4j
@Service
public class ChatService {

    private static final Map<String, String> UI_AI_MODE_MAP = Map.of(
            "深度思考", "deep_think",
            "风险评估", "risk_assessment",
            "干预建议", "intervention",
            "量表解读", "scale_interpret"
    );

    private static final Map<String, List<Map<String, String>>> DEFAULT_RECOMMENDED = Map.of(
            "deep_think", List.of(
                    Map.of("question", "如何缓解焦虑情绪？请先帮我判断我现在更像压力反应、焦虑还是抑郁倾向。", "category", "情绪梳理"),
                    Map.of("question", "如果一个人连续两周情绪低落、失眠、注意力下降，应该如何系统判断严重程度？", "category", "严重程度判断"),
                    Map.of("question", "抑郁和焦虑经常同时出现时，日常最先该处理的三个问题是什么？", "category", "共病处理"),
                    Map.of("question", "当我说不清自己为什么难受时，你可以用提问的方式一步步帮我梳理吗？", "category", "引导式支持")
            ),
            "risk_assessment", List.of(
                    Map.of("question", "请帮我识别这段表达里是否存在自伤或自杀风险信号，并说明判断依据。", "category", "风险识别"),
                    Map.of("question", "如果一个人说“活着没什么意思，但我不会真的去做”，风险等级通常怎么判断？", "category", "风险分级"),
                    Map.of("question", "评估危机风险时，最关键要核实的危险细节有哪些？", "category", "风险核查"),
                    Map.of("question", "请给我一套适合辅导员或家长使用的风险识别提问清单。", "category", "提问清单")
            ),
            "intervention", List.of(
                    Map.of("question", "如果对方今晚情绪非常差，我现在最优先该做哪三步干预？", "category", "即时干预"),
                    Map.of("question", "请给我一份“接下来1小时、今晚、24小时内”的陪伴与干预清单。", "category", "行动清单"),
                    Map.of("question", "当对方拒绝沟通、又让我担心安全时，现实中应该怎样升级处置？", "category", "升级处置"),
                    Map.of("question", "如果需要联系家属、老师或医院，沟通时该怎么说更稳妥？", "category", "沟通支持")
            ),
            "scale_interpret", List.of(
                    Map.of("question", "量表分数出来后，应该怎样结合最近状态做解释，而不是只看高低？", "category", "结果解释"),
                    Map.of("question", "PHQ-9 或 GAD-7 分数偏高时，通常意味着什么，还需要结合哪些信息？", "category", "结果解释"),
                    Map.of("question", "请帮我把量表结果翻译成家长、老师也能听懂的说明。", "category", "对外说明"),
                    Map.of("question", "量表提示有风险时，后续适合补做哪些问题核查或支持动作？", "category", "后续行动")
            )
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String normalizeAiMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return "deep_think";
        }
        String trimmed = mode.trim();
        if (UI_AI_MODE_MAP.containsKey(trimmed)) {
            return UI_AI_MODE_MAP.get(trimmed);
        }
        if (Set.of("deep_think", "risk_assessment", "intervention", "scale_interpret").contains(trimmed)) {
            return trimmed;
        }
        return "deep_think";
    }

    public Map<String, Object> listSessions(String userHash, int page, int pageSize, boolean includeArchived) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);

        List<Object> params = new ArrayList<>();
        String where;
        if (includeArchived) {
            where = "status IN ('active', 'archived')";
        } else {
            where = "status = ?";
            params.add("active");
        }
        if (StringUtils.hasText(userHash)) {
            where += " AND user_hash = ?";
            params.add(userHash.trim());
        }

        try {
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_sessions WHERE " + where, Integer.class, params.toArray());
            int totalCount = total == null ? 0 : total;

            int offset = (safePage - 1) * safeSize;
            List<Object> listParams = new ArrayList<>(params);
            listParams.add(safeSize);
            listParams.add(offset);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT id, session_code, user_id, user_hash, title, archive_id,
                                   data_source, ai_mode, context_type, message_count,
                                   total_tokens, status, is_pinned, last_message_at,
                                   last_ai_response_at, created_at, updated_at,
                                   knowledge_sources, rag_keywords
                            FROM chat_sessions
                            WHERE %s
                            ORDER BY is_pinned DESC, last_message_at DESC
                            LIMIT ? OFFSET ?
                            """.formatted(where),
                    listParams.toArray());

            List<Map<String, Object>> sessions = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                sessions.add(toSessionResponse(row));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessions", sessions);
            data.put("total", totalCount);
            data.put("page", safePage);
            data.put("page_size", safeSize);
            return data;
        } catch (Exception ex) {
            log.warn("查询会话列表失败，返回空列表: {}", ex.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessions", List.of());
            data.put("total", 0);
            data.put("page", safePage);
            data.put("page_size", safeSize);
            return data;
        }
    }

    public Map<String, Object> getSession(long sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM chat_sessions WHERE id = ?", sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return toSessionResponse(rows.get(0));
    }

    public Map<String, Object> getSessionWithMessages(long sessionId) {
        Map<String, Object> session = getSession(sessionId);
        Map<String, Object> msgs = listMessages(sessionId, 1, 100);
        session.put("messages", msgs.get("messages"));
        return session;
    }

    public Map<String, Object> createSession(Map<String, Object> body) {
        String sessionCode = "chat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String userHash = str(first(body, "userHash", "user_hash"));
        String title = str(first(body, "title"));
        Object archiveId = first(body, "archiveId", "archive_id");
        String dataSource = str(first(body, "dataSource", "data_source"));
        String aiMode = normalizeAiMode(str(first(body, "aiMode", "ai_mode")));
        String contextType = str(first(body, "contextType", "context_type"));
        if (!StringUtils.hasText(contextType)) {
            contextType = "general";
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        String finalContextType = contextType;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                            INSERT INTO chat_sessions
                            (session_code, user_id, user_hash, title, archive_id, data_source,
                             ai_mode, context_type, knowledge_sources, rag_keywords)
                            VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionCode);
            ps.setString(2, blankToNull(userHash));
            ps.setString(3, blankToNull(title));
            if (archiveId == null || !StringUtils.hasText(String.valueOf(archiveId))) {
                ps.setObject(4, null);
            } else {
                ps.setObject(4, Long.parseLong(String.valueOf(archiveId)));
            }
            ps.setString(5, blankToNull(dataSource));
            ps.setString(6, aiMode);
            ps.setString(7, finalContextType);
            ps.setString(8, "[]");
            ps.setString(9, "[]");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建会话失败");
        }
        return getSession(key.longValue());
    }

    public Map<String, Object> updateSession(long sessionId, Map<String, Object> body) {
        getSession(sessionId);

        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (body.containsKey("title") || body.containsKey("Title")) {
            String title = str(first(body, "title"));
            if (title.length() > 255) {
                throw new BusinessException("会话标题长度不能超过255个字符");
            }
            sets.add("title = ?");
            params.add(blankToNull(title));
        }
        if (body.containsKey("aiMode") || body.containsKey("ai_mode")) {
            sets.add("ai_mode = ?");
            params.add(normalizeAiMode(str(first(body, "aiMode", "ai_mode"))));
        }
        if (body.containsKey("contextType") || body.containsKey("context_type")) {
            sets.add("context_type = ?");
            params.add(str(first(body, "contextType", "context_type")));
        }
        if (body.containsKey("status")) {
            String status = str(body.get("status"));
            if (!Set.of("active", "archived", "deleted").contains(status)) {
                throw new BusinessException("非法会话状态");
            }
            sets.add("status = ?");
            params.add(status);
        }
        if (body.containsKey("isPinned") || body.containsKey("is_pinned")) {
            Object pinned = first(body, "isPinned", "is_pinned");
            sets.add("is_pinned = ?");
            params.add(toBool(pinned));
        }

        if (sets.isEmpty()) {
            return getSession(sessionId);
        }

        params.add(sessionId);
        jdbcTemplate.update(
                "UPDATE chat_sessions SET " + String.join(", ", sets) + " WHERE id = ?",
                params.toArray());
        return getSession(sessionId);
    }

    public void deleteSession(long sessionId) {
        int updated = jdbcTemplate.update(
                "UPDATE chat_sessions SET status = 'deleted', archived_at = NOW() WHERE id = ?",
                sessionId);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
        }
    }

    public Map<String, Object> listMessages(long sessionId, int page, int pageSize) {
        getSession(sessionId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_messages WHERE session_id = ?", Integer.class, sessionId);
        int totalCount = total == null ? 0 : total;
        int offset = (safePage - 1) * safeSize;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT id, session_id, role, content, content_type,
                               attachments, has_image, has_file, ai_model, ai_mode,
                               tokens_used, processing_time_ms, rag_context,
                               retrieval_sources, is_generating, is_error,
                               error_message, references_json, parent_message_id,
                               created_at
                        FROM chat_messages
                        WHERE session_id = ?
                        ORDER BY id ASC
                        LIMIT ? OFFSET ?
                        """,
                sessionId, safeSize, offset);

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            messages.add(toMessageResponse(row));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messages", messages);
        data.put("total", totalCount);
        data.put("page", safePage);
        data.put("page_size", safeSize);
        return data;
    }

    public long createMessage(Map<String, Object> messageData) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                            INSERT INTO chat_messages
                            (session_id, role, content, content_type, attachments,
                             has_image, has_file, ai_model, ai_mode, tokens_used,
                             processing_time_ms, rag_context, retrieval_sources,
                             is_generating, is_error, error_message, references_json,
                             parent_message_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, messageData.get("session_id"));
            ps.setString(i++, str(messageData.get("role")));
            ps.setString(i++, str(messageData.get("content")));
            ps.setString(i++, str(messageData.getOrDefault("content_type", "text")));
            ps.setString(i++, toJson(messageData.getOrDefault("attachments", List.of())));
            ps.setBoolean(i++, toBool(messageData.get("has_image")));
            ps.setBoolean(i++, toBool(messageData.get("has_file")));
            ps.setString(i++, blankToNull(str(messageData.get("ai_model"))));
            ps.setString(i++, blankToNull(str(messageData.get("ai_mode"))));
            ps.setObject(i++, messageData.get("tokens_used"));
            ps.setObject(i++, messageData.get("processing_time_ms"));
            ps.setString(i++, messageData.get("rag_context") == null ? null : toJson(messageData.get("rag_context")));
            ps.setString(i++, messageData.get("retrieval_sources") == null
                    ? null : toJson(messageData.get("retrieval_sources")));
            ps.setBoolean(i++, toBool(messageData.get("is_generating")));
            ps.setBoolean(i++, toBool(messageData.get("is_error")));
            ps.setString(i++, blankToNull(str(messageData.get("error_message"))));
            ps.setString(i++, messageData.get("references_json") == null
                    ? null : toJson(messageData.get("references_json")));
            ps.setObject(i, messageData.get("parent_message_id"));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建消息失败");
        }
        return key.longValue();
    }

    public void bumpSessionAfterReply(long sessionId, int messageCountDelta) {
        jdbcTemplate.update(
                """
                        UPDATE chat_sessions
                        SET status = 'active',
                            message_count = message_count + ?,
                            last_message_at = NOW(),
                            last_ai_response_at = NOW()
                        WHERE id = ?
                        """,
                messageCountDelta, sessionId);
    }

    public Map<String, Object> getMessageById(long messageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM chat_messages WHERE id = ?", messageId);
        if (rows.isEmpty()) {
            return null;
        }
        return toMessageResponse(rows.get(0));
    }

    public List<Map<String, Object>> getRecommendedQuestions(String aiModeRaw, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        String aiMode;
        if (!StringUtils.hasText(aiModeRaw) || "all".equalsIgnoreCase(aiModeRaw.trim())) {
            aiMode = "all";
        } else {
            aiMode = normalizeAiMode(aiModeRaw);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            if ("all".equals(aiMode)) {
                rows = jdbcTemplate.queryForList(
                        """
                                SELECT id, question, ai_mode, category, keywords, sort_order, usage_count
                                FROM chat_recommended_questions
                                WHERE is_active = TRUE
                                ORDER BY sort_order ASC, usage_count DESC
                                LIMIT ?
                                """,
                        safeLimit);
            } else {
                rows = jdbcTemplate.queryForList(
                        """
                                SELECT id, question, ai_mode, category, keywords, sort_order, usage_count
                                FROM chat_recommended_questions
                                WHERE is_active = TRUE AND ai_mode = ?
                                ORDER BY sort_order ASC, usage_count DESC
                                LIMIT ?
                                """,
                        aiMode, safeLimit);
                if (rows.size() < safeLimit) {
                    List<Map<String, Object>> extras = jdbcTemplate.queryForList(
                            """
                                    SELECT id, question, ai_mode, category, keywords, sort_order, usage_count
                                    FROM chat_recommended_questions
                                    WHERE is_active = TRUE AND ai_mode = 'all'
                                    ORDER BY sort_order ASC, usage_count DESC
                                    LIMIT ?
                                    """,
                            Math.max(safeLimit * 2, 16));
                    rows = new ArrayList<>(rows);
                    rows.addAll(extras);
                }
            }
        } catch (Exception ex) {
            log.warn("查询推荐问题失败，使用默认问题: {}", ex.getMessage());
            rows = List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String question = str(row.get("question")).trim();
            if (!StringUtils.hasText(question) || seen.contains(question)) {
                continue;
            }
            seen.add(question);
            result.add(toRecommendedRow(row));
            if (result.size() >= safeLimit) {
                break;
            }
        }

        if (!"all".equals(aiMode)) {
            List<Map<String, Object>> prioritized = new ArrayList<>();
            Set<String> prioritizedSeen = new LinkedHashSet<>();
            for (Map<String, Object> row : result) {
                String question = str(row.get("question")).trim();
                if (!StringUtils.hasText(question) || prioritizedSeen.contains(question)) {
                    continue;
                }
                if (aiMode.equals(str(row.get("aiMode")))) {
                    prioritized.add(row);
                    prioritizedSeen.add(question);
                }
            }
            List<Map<String, String>> defaults = DEFAULT_RECOMMENDED.getOrDefault(aiMode, List.of());
            int index = 1;
            for (Map<String, String> item : defaults) {
                String question = item.get("question").trim();
                if (!StringUtils.hasText(question) || prioritizedSeen.contains(question)) {
                    continue;
                }
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("id", "default-" + aiMode + "-" + index);
                q.put("question", question);
                q.put("aiMode", aiMode);
                q.put("category", item.get("category"));
                q.put("keywords", List.of());
                q.put("sortOrder", 10_000 + index);
                q.put("usageCount", 0);
                prioritized.add(q);
                prioritizedSeen.add(question);
                index++;
                if (prioritized.size() >= safeLimit) {
                    break;
                }
            }
            for (Map<String, Object> row : result) {
                if (prioritized.size() >= safeLimit) {
                    break;
                }
                String question = str(row.get("question")).trim();
                if (!StringUtils.hasText(question) || prioritizedSeen.contains(question)) {
                    continue;
                }
                prioritized.add(row);
                prioritizedSeen.add(question);
            }
            result = prioritized;
        }

        if (result.isEmpty()) {
            // all 模式且库空：拼几种默认
            int idx = 1;
            for (var entry : DEFAULT_RECOMMENDED.entrySet()) {
                for (Map<String, String> item : entry.getValue()) {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("id", "default-" + entry.getKey() + "-" + idx);
                    q.put("question", item.get("question"));
                    q.put("aiMode", entry.getKey());
                    q.put("category", item.get("category"));
                    q.put("keywords", List.of());
                    result.add(q);
                    idx++;
                    if (result.size() >= safeLimit) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    public Map<String, Object> toSessionResponse(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", str(row.get("id")));
        out.put("sessionCode", row.get("session_code"));
        out.put("userId", row.get("user_id") == null ? null : str(row.get("user_id")));
        out.put("userHash", row.get("user_hash"));
        out.put("title", row.get("title"));
        out.put("archiveId", row.get("archive_id") == null ? null : str(row.get("archive_id")));
        out.put("dataSource", row.get("data_source"));
        out.put("aiMode", row.get("ai_mode"));
        out.put("contextType", row.get("context_type"));
        out.put("knowledgeSources", parseJson(row.get("knowledge_sources"), List.of()));
        out.put("ragKeywords", parseJson(row.get("rag_keywords"), List.of()));
        out.put("messageCount", toInt(row.get("message_count")));
        out.put("totalTokens", toInt(row.get("total_tokens")));
        out.put("status", row.get("status"));
        out.put("isPinned", toBool(row.get("is_pinned")));
        out.put("lastMessageAt", ts(row.get("last_message_at")));
        out.put("lastAiResponseAt", ts(row.get("last_ai_response_at")));
        out.put("createdAt", ts(row.get("created_at")));
        out.put("updatedAt", ts(row.get("updated_at")));
        if (row.containsKey("messages")) {
            out.put("messages", row.get("messages"));
        }
        return out;
    }

    public Map<String, Object> toMessageResponse(Map<String, Object> row) {
        Object refsRaw = row.get("references_json");
        Object refsParsed = parseJson(refsRaw, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", str(row.get("id")));
        out.put("sessionId", str(row.get("session_id")));
        out.put("role", row.get("role"));
        out.put("content", row.get("content"));
        out.put("contentType", row.get("content_type"));
        out.put("aiModel", row.get("ai_model"));
        out.put("aiMode", row.get("ai_mode"));
        out.put("tokensUsed", row.get("tokens_used"));
        out.put("processingTimeMs", row.get("processing_time_ms"));
        out.put("ragContext", parseJson(row.get("rag_context"), null));
        out.put("retrievalSources", parseJson(row.get("retrieval_sources"), null));
        out.put("isGenerating", toBool(row.get("is_generating")));
        out.put("isStreaming", toBool(row.get("is_streaming")));
        out.put("isError", toBool(row.get("is_error")));
        out.put("errorMessage", row.get("error_message"));
        out.put("referencesJson", refsParsed);
        out.put("references", refsParsed);
        out.put("parentMessageId", row.get("parent_message_id") == null ? null : str(row.get("parent_message_id")));
        out.put("createdAt", ts(row.get("created_at")));
        return out;
    }

    private Map<String, Object> toRecommendedRow(Map<String, Object> row) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", str(row.get("id")));
        q.put("question", row.get("question"));
        q.put("aiMode", row.get("ai_mode"));
        q.put("category", row.get("category"));
        q.put("keywords", parseJson(row.get("keywords"), List.of()));
        q.put("sortOrder", row.get("sort_order"));
        q.put("usageCount", row.get("usage_count"));
        return q;
    }

    private Object parseJson(Object raw, Object defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Map || raw instanceof List) {
            return raw;
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        try {
            if (text.startsWith("[")) {
                return objectMapper.readValue(text, new TypeReference<List<Object>>() {});
            }
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static Object first(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body != null && body.containsKey(key) && body.get(key) != null) {
                return body.get(key);
            }
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String ts(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        return String.valueOf(v);
    }
}
