package com.vis4srd.knowledge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.knowledge.config.KnowledgeProperties;
import com.vis4srd.knowledge.util.KnowledgePathResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeService {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5a-zA-Z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "是", "在", "我", "有", "个", "上", "也", "就", "不", "人", "都", "一", "中", "大", "为", "与", "或",
            "the", "a", "an", "is", "are", "was", "were", "and", "or", "but", "in", "on", "at", "to", "for");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final KnowledgePathResolver pathResolver;
    private final KnowledgeProperties properties;

    public KnowledgeService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            KnowledgePathResolver pathResolver,
            KnowledgeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.pathResolver = pathResolver;
        this.properties = properties;
    }

    public Map<String, Object> getTopics() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT t.id, t.topic_name, t.topic_code, t.description,
                                   t.icon, t.color, t.sort_order, t.is_active,
                                   (SELECT COUNT(*) FROM knowledge_documents d
                                    WHERE d.topic_id = t.id AND d.is_deleted = FALSE) AS document_count
                            FROM knowledge_topics t
                            WHERE t.is_active = TRUE
                            ORDER BY t.sort_order, t.id
                            """);
        } catch (Exception ex) {
            log.warn("查询知识主题失败: {}", ex.getMessage());
            rows = List.of();
        }
        List<Map<String, Object>> topics = rows.stream().map(this::toTopicResponse).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("topics", topics);
        data.put("total", topics.size());
        return data;
    }

    public List<Map<String, Object>> getSubTopics(Integer topicId) {
        StringBuilder sql = new StringBuilder(
                """
                        SELECT id, topic_id, sub_topic_name, sub_topic_code, description, sort_order
                        FROM knowledge_sub_topics
                        WHERE 1=1
                        """);
        List<Object> params = new ArrayList<>();
        if (topicId != null) {
            sql.append(" AND topic_id = ?");
            params.add(topicId);
        }
        sql.append(" ORDER BY sort_order, id");

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception ex) {
            log.warn("查询子主题失败: {}", ex.getMessage());
            rows = List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", toInt(row.get("id")));
            item.put("topicId", toInt(row.get("topic_id")));
            item.put("subTopicName", str(row.get("sub_topic_name")));
            item.put("subTopicCode", str(row.get("sub_topic_code")));
            item.put("description", str(row.get("description")));
            item.put("sortOrder", toInt(row.get("sort_order"), 0));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getDocuments(
            Integer topicId,
            Integer subTopicId,
            String keyword,
            String format,
            String status,
            int page,
            int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);

        StringBuilder where = new StringBuilder(" WHERE d.is_deleted = FALSE");
        List<Object> params = new ArrayList<>();
        if (topicId != null) {
            where.append(" AND d.topic_id = ?");
            params.add(topicId);
        }
        if (subTopicId != null) {
            where.append(" AND d.sub_topic_id = ?");
            params.add(subTopicId);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (d.title LIKE ? OR d.description LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (StringUtils.hasText(format)) {
            where.append(" AND d.format = ?");
            params.add(format.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND d.upload_status = ?");
            params.add(status.trim());
        }

        long total = 0;
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_documents d" + where,
                    Long.class,
                    params.toArray());
            total = cnt == null ? 0 : cnt;
        } catch (Exception ex) {
            log.warn("统计知识文档失败: {}", ex.getMessage());
        }

        List<Map<String, Object>> documents = new ArrayList<>();
        if (total > 0) {
            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add((safePage - 1L) * safeSize);
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        """
                                SELECT d.id, d.title, d.topic_id, d.sub_topic_id, d.keywords,
                                       d.format, d.file_name, d.file_path, d.file_size, d.size_display,
                                       d.description, d.upload_status, d.progress, d.is_indexed,
                                       d.usage_count, d.uploaded_at, d.created_at, d.rag_path,
                                       t.topic_name AS topic,
                                       st.sub_topic_name AS sub_topic
                                FROM knowledge_documents d
                                LEFT JOIN knowledge_topics t ON d.topic_id = t.id
                                LEFT JOIN knowledge_sub_topics st ON d.sub_topic_id = st.id
                                """ + where + """
                                 ORDER BY d.uploaded_at DESC, d.id DESC
                                LIMIT ? OFFSET ?
                                """,
                        queryParams.toArray());
                documents = rows.stream().map(this::toDocResponse).toList();
            } catch (Exception ex) {
                log.warn("查询知识文档失败: {}", ex.getMessage());
            }
        }

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("total", total);
        pagination.put("page", safePage);
        pagination.put("page_size", safeSize);
        pagination.put("limit", safeSize);
        pagination.put("totalPages", total == 0 ? 0 : (total + safeSize - 1) / safeSize);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("documents", documents);
        data.put("pagination", pagination);
        return data;
    }

    public Map<String, Object> getDocumentDetail(String docId) {
        Map<String, Object> row = findDocumentRow(docId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "文档不存在"));
        bumpUsage(toLong(row.get("id")));
        return toDocResponse(row);
    }

    public Map<String, Object> getDocumentPreview(String docId, int maxLength) {
        int safeMax = Math.min(Math.max(maxLength, 100), 5000);
        Map<String, Object> row = findDocumentRow(docId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "文档不存在"));

        String docFormat = str(row.get("format")).toLowerCase(Locale.ROOT);
        String filePath = str(row.get("file_path"));
        String previewText = "";
        Path actual = pathResolver.resolve(filePath);
        if (actual != null && Files.isRegularFile(actual)) {
            previewText = extractPreviewText(actual, docFormat);
        }
        if (!StringUtils.hasText(previewText)) {
            previewText = str(row.get("description"));
            if (("pdf".equals(docFormat) || "docx".equals(docFormat) || "doc".equals(docFormat))
                    && !StringUtils.hasText(previewText)) {
                previewText = String.format(
                        "[%s 文档] 当前环境未启用二进制文本抽取，请使用下载或流式预览查看完整内容。文件：%s",
                        docFormat.toUpperCase(Locale.ROOT),
                        str(row.get("file_name")));
            }
        }

        String snippet = previewText.length() > safeMax
                ? previewText.substring(0, safeMax) + "\n\n... [内容已截断，仅显示前 " + safeMax + " 字符] ..."
                : previewText;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.get("id"));
        data.put("title", str(row.get("title")));
        data.put("summary", str(row.get("description")));
        data.put("preview", snippet);
        data.put("content", previewText);
        data.put("format", StringUtils.hasText(docFormat) ? docFormat : "txt");
        data.put("filePath", filePath);
        data.put("fileName", str(row.get("file_name")));
        data.put("fileSize", toLong(row.get("file_size"), 0L));
        data.put("isTruncated", previewText.length() > safeMax);
        data.put("totalLength", previewText.length());
        data.put("uploadedAt", formatTime(row.get("uploaded_at") != null ? row.get("uploaded_at") : row.get("created_at")));
        return data;
    }

    public ResourceFile loadDocumentFile(String docId, boolean streamOnly) {
        Map<String, Object> row = findDocumentRow(docId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "文档不存在"));
        String filePath = str(row.get("file_path"));
        if (!StringUtils.hasText(filePath)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文件路径不存在");
        }
        Path actual = pathResolver.resolve(filePath);
        if (actual == null || !Files.isRegularFile(actual)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文件不存在或已被删除");
        }

        String ext = extension(actual.getFileName().toString());
        if (streamOnly) {
            Set<String> allowed = Set.of(".pdf", ".docx", ".doc");
            if (!allowed.contains(ext)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "当前格式不支持流式预览");
            }
        }

        String mediaType = mimeType(ext);
        boolean inline = streamOnly || Set.of(".pdf", ".png", ".jpg", ".jpeg", ".gif").contains(ext);
        String fileName = StringUtils.hasText(str(row.get("file_name")))
                ? str(row.get("file_name"))
                : actual.getFileName().toString();
        return new ResourceFile(new FileSystemResource(actual), fileName, mediaType, inline);
    }

    public Map<String, Object> getDocumentBase64(String docId) {
        ResourceFile file = loadDocumentFile(docId, false);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.resource().getFile().toPath());
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文件失败");
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("base64", base64);
        data.put("dataUrl", "data:" + file.mediaType() + ";base64," + base64);
        data.put("mediaType", file.mediaType());
        data.put("fileName", file.fileName());
        data.put("size", bytes.length);
        return data;
    }

    public boolean deleteDocument(long docId) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE knowledge_documents
                        SET is_deleted = TRUE, deleted_at = NOW()
                        WHERE id = ? AND is_deleted = FALSE
                        """,
                docId);
        return updated > 0;
    }

    public Map<String, Object> updateDocument(long docId, Map<String, Object> body) {
        Map<String, Object> existing = findById(docId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "文档不存在"));

        List<String> fields = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        if (body.containsKey("title") && body.get("title") != null) {
            fields.add("title = ?");
            values.add(String.valueOf(body.get("title")));
        }
        Integer topicId = firstInt(body, "topic_id", "topicId");
        if (topicId != null) {
            fields.add("topic_id = ?");
            values.add(topicId);
        }
        Integer subTopicId = firstInt(body, "sub_topic_id", "subTopicId");
        if (body.containsKey("sub_topic_id") || body.containsKey("subTopicId")) {
            fields.add("sub_topic_id = ?");
            values.add(subTopicId);
        }
        if (body.containsKey("description") && body.get("description") != null) {
            fields.add("description = ?");
            values.add(String.valueOf(body.get("description")));
        }
        if (body.containsKey("keywords")) {
            fields.add("keywords = ?");
            values.add(toKeywordsJson(body.get("keywords")));
        }

        if (!fields.isEmpty()) {
            values.add(docId);
            jdbcTemplate.update(
                    "UPDATE knowledge_documents SET " + String.join(", ", fields) + " WHERE id = ?",
                    values.toArray());
        }

        return toDocResponse(findById(docId).orElse(existing));
    }

    public Map<String, Object> createDocument(Map<String, Object> body, String uploadedBy) {
        String title = str(body.get("title"));
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        Integer topicId = firstInt(body, "topic_id", "topicId");
        if (topicId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "主题不能为空");
        }
        Integer subTopicId = firstInt(body, "sub_topic_id", "subTopicId");
        String description = firstStr(body, "description", "summary");
        String format = firstStr(body, "format");
        if (!StringUtils.hasText(format)) {
            format = "md";
        }
        String filePath = firstStr(body, "file_path", "filePath");
        String fileName = firstStr(body, "file_name", "fileName");
        if (!StringUtils.hasText(fileName)) {
            fileName = title;
        }
        long fileSize = firstLong(body, "file_size", "fileSize");
        List<String> keywords = parseKeywords(body.get("keywords"));

        long id = insertDocument(
                title,
                topicId,
                subTopicId,
                keywords,
                format,
                fileName,
                filePath,
                fileSize,
                formatSize(fileSize),
                description,
                uploadedBy);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("title", title);
        data.put("topicId", topicId);
        data.put("subTopicId", subTopicId);
        data.put("keywords", keywords);
        data.put("format", format);
        data.put("fileName", fileName);
        data.put("filePath", filePath);
        data.put("fileSize", fileSize);
        data.put("sizeDisplay", formatSize(fileSize));
        data.put("description", description);
        data.put("uploadStatus", "uploaded");
        return data;
    }

    public Map<String, Object> uploadDocument(
            MultipartFile file,
            String title,
            int topicId,
            Integer subTopicId,
            String summary,
            String keywordsRaw,
            String formatOverride,
            String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择要上传的文件");
        }
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档标题不能为空");
        }

        byte[] contentBytes;
        try {
            contentBytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "读取上传文件失败");
        }
        if (contentBytes.length > MAX_UPLOAD_BYTES) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小超过 10MB 限制");
        }

        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("document");
        String ext = extension(originalName).replace(".", "");
        Map<String, String> formatMap = Map.of(
                "txt", "txt",
                "md", "md",
                "markdown", "md",
                "pdf", "pdf",
                "docx", "docx",
                "doc", "docx");
        String docFormat = StringUtils.hasText(formatOverride)
                ? formatOverride.trim().toLowerCase(Locale.ROOT)
                : formatMap.getOrDefault(ext.toLowerCase(Locale.ROOT), "txt");

        String contentText = extractUploadText(contentBytes, docFormat);
        List<String> keywords = StringUtils.hasText(keywordsRaw)
                ? parseKeywords(keywordsRaw)
                : extractKeywordsFromContent(contentText);

        Path uploadDir = properties.resolveUploadDir();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建上传目录失败");
        }

        String safeFilename = Instant.now().getEpochSecond() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_"
                + originalName.replaceAll("[\\\\/]+", "_");
        Path target = uploadDir.resolve(safeFilename);
        try {
            Files.write(target, contentBytes);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败: " + ex.getMessage());
        }

        String relativePath = "/uploads/knowledge/" + safeFilename;
        long fileSize = contentBytes.length;
        String sizeDisplay = formatSize(fileSize);
        long id = insertDocument(
                title.trim(),
                topicId,
                subTopicId,
                keywords,
                docFormat,
                originalName,
                relativePath,
                fileSize,
                sizeDisplay,
                summary == null ? "" : summary,
                uploadedBy);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("title", title.trim());
        data.put("topicId", topicId);
        data.put("subTopicId", subTopicId);
        data.put("keywords", keywords);
        data.put("format", docFormat);
        data.put("fileName", originalName);
        data.put("filePath", relativePath);
        data.put("fileSize", fileSize);
        data.put("sizeDisplay", sizeDisplay);
        data.put("description", summary == null ? "" : summary);
        data.put("uploadStatus", "uploaded");
        return data;
    }

    public Map<String, Object> searchDocuments(String query, int topK) {
        String q = query == null ? "" : query.trim();
        int safeTopK = Math.min(Math.max(topK, 1), 50);
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT d.id, d.title, d.topic_id, d.sub_topic_id, d.keywords,
                                   d.format, d.file_name, d.file_path, d.file_size, d.size_display,
                                   d.description, d.upload_status, d.progress, d.is_indexed,
                                   d.usage_count, d.uploaded_at, d.created_at, d.rag_path,
                                   t.topic_name AS topic,
                                   st.sub_topic_name AS sub_topic
                            FROM knowledge_documents d
                            LEFT JOIN knowledge_topics t ON d.topic_id = t.id
                            LEFT JOIN knowledge_sub_topics st ON d.sub_topic_id = st.id
                            WHERE d.is_deleted = FALSE
                              AND (d.title LIKE ? OR d.description LIKE ? OR CAST(d.keywords AS CHAR) LIKE ?)
                            ORDER BY d.usage_count DESC, d.id DESC
                            LIMIT ?
                            """,
                    "%" + q + "%",
                    "%" + q + "%",
                    "%" + q + "%",
                    safeTopK);
        } catch (Exception ex) {
            log.warn("搜索知识文档失败: {}", ex.getMessage());
            rows = List.of();
        }

        List<Map<String, Object>> results = rows.stream().map(this::toDocResponse).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", q);
        data.put("results", results);
        data.put("total", results.size());
        return data;
    }

    public Map<String, Object> getRelated(String riskLevel) {
        Map<String, Object> topicsData = getTopics();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) topicsData.getOrDefault("topics", List.of());
        Map<String, Map<String, Object>> topicMap = new HashMap<>();
        for (Map<String, Object> t : topics) {
            topicMap.put(str(t.get("topicCode")), t);
        }

        List<String> codes;
        String level = riskLevel == null ? "" : riskLevel.trim().toLowerCase(Locale.ROOT);
        if ("high".equals(level)) {
            codes = List.of("suicide_self_harm", "crisis_intervention", "depression");
        } else if ("medium".equals(level)) {
            codes = List.of("depression", "anxiety", "emotion");
        } else {
            codes = List.of("emotion", "sleep_physiology", "scale_screening");
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (String code : codes) {
            Map<String, Object> topic = topicMap.get(code);
            if (topic != null) {
                recommendations.add(topic);
            }
        }

        Map<String, Object> docsPage = getDocuments(null, null, null, null, null, 1, 6);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> docs = (List<Map<String, Object>>) docsPage.getOrDefault("documents", List.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("risk_level", StringUtils.hasText(riskLevel) ? riskLevel : "unknown");
        data.put("recommendations", recommendations);
        data.put("documentRecommendations", docs);
        return data;
    }

    public List<Map<String, Object>> getKeywords(Integer topicId, boolean isHot) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            String sql = """
                    SELECT keyword, category, color_class, is_hot, usage_count, sort_order
                    FROM knowledge_keywords
                    WHERE is_active = TRUE
                    """;
            List<Object> params = new ArrayList<>();
            if (isHot) {
                sql += " AND is_hot = TRUE";
            }
            sql += " ORDER BY sort_order, keyword LIMIT 100";
            rows = jdbcTemplate.queryForList(sql, params.toArray());
        } catch (Exception ex) {
            log.warn("查询 knowledge_keywords 失败: {}", ex.getMessage());
        }

        if (rows.isEmpty()) {
            rows = aggregateKeywordsFromDocuments(topicId);
        }

        List<Map<String, Object>> compact = compactKeywords(rows, isHot ? 18 : 36);
        if (compact.isEmpty()) {
            compact = rows.stream().limit(isHot ? 18L : 36L).toList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < compact.size(); i++) {
            Map<String, Object> kw = compact.get(i);
            int usage = toInt(kw.get("usage_count") != null ? kw.get("usage_count") : kw.get("weight"), 1);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("keyword", str(kw.get("keyword")));
            item.put("category", str(kw.get("category")));
            item.put("colorClass", firstNonBlank(str(kw.get("color_class")), "bg-orange-100 text-orange-700"));
            item.put("isHot", isHot || usage > 5 || Boolean.TRUE.equals(kw.get("is_hot")));
            item.put("usageCount", usage);
            item.put("sortOrder", i);
            item.put("isActive", true);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getSystemKeywords(Boolean isHot) {
        return getKeywords(null, Boolean.TRUE.equals(isHot));
    }

    private List<Map<String, Object>> aggregateKeywordsFromDocuments(Integer topicId) {
        StringBuilder sql = new StringBuilder(
                "SELECT keywords FROM knowledge_documents WHERE is_deleted = FALSE AND keywords IS NOT NULL");
        List<Object> params = new ArrayList<>();
        if (topicId != null) {
            sql.append(" AND topic_id = ?");
            params.add(topicId);
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception ex) {
            return List.of();
        }

        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            for (String kw : parseKeywords(row.get("keywords"))) {
                if (StringUtils.hasText(kw)) {
                    counts.merge(kw, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(100)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("keyword", e.getKey());
                    item.put("weight", e.getValue());
                    item.put("usage_count", e.getValue());
                    item.put("category", "");
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> compactKeywords(List<Map<String, Object>> rows, int limit) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> noise = Set.of(
                "情绪障碍", "干预与求助资源", "心理健康数据", "趋势报告",
                "自杀与自伤", "自杀风险预防", "危机预防", "儿童心理健康");

        for (Map<String, Object> row : rows) {
            String keyword = str(row.get("keyword")).trim();
            int usage = toInt(row.get("usage_count") != null ? row.get("usage_count") : row.get("weight"), 0);
            if (!StringUtils.hasText(keyword) || seen.contains(keyword) || usage < 2 || noise.contains(keyword)) {
                continue;
            }
            if (keyword.length() > 12 && !keyword.matches("[A-Za-z0-9\\-+/]{1,12}")) {
                continue;
            }
            if (keyword.chars().filter(ch -> ch == ' ').count() >= 2) {
                continue;
            }
            seen.add(keyword);
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("keyword", keyword);
            copy.put("usage_count", usage);
            normalized.add(copy);
        }

        normalized.sort(Comparator
                .comparingInt((Map<String, Object> m) -> -toInt(m.get("usage_count"), 0))
                .thenComparingInt(m -> str(m.get("keyword")).length())
                .thenComparing(m -> str(m.get("keyword"))));
        return normalized.stream().limit(limit).toList();
    }

    private Optional<Map<String, Object>> findDocumentRow(String docId) {
        if (!StringUtils.hasText(docId)) {
            return Optional.empty();
        }
        String raw = URLDecoder.decode(docId.trim(), StandardCharsets.UTF_8);
        try {
            long id = Long.parseLong(raw);
            Optional<Map<String, Object>> byId = findById(id);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // fall through to title lookup
        }
        return findByTitle(raw);
    }

    private Optional<Map<String, Object>> findById(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT d.*, t.topic_name, st.sub_topic_name
                        FROM knowledge_documents d
                        LEFT JOIN knowledge_topics t ON d.topic_id = t.id
                        LEFT JOIN knowledge_sub_topics st ON d.sub_topic_id = st.id
                        WHERE d.id = ? AND d.is_deleted = FALSE
                        """,
                id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Optional<Map<String, Object>> findByTitle(String title) {
        String normalized = normalizeLookupKey(title);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT d.*, t.topic_name, st.sub_topic_name
                        FROM knowledge_documents d
                        LEFT JOIN knowledge_topics t ON d.topic_id = t.id
                        LEFT JOIN knowledge_sub_topics st ON d.sub_topic_id = st.id
                        WHERE d.is_deleted = FALSE
                          AND (
                            d.title = ?
                            OR d.file_name = ?
                            OR LOWER(REPLACE(d.title, ' ', '')) = ?
                            OR LOWER(REPLACE(d.file_name, ' ', '')) = ?
                            OR LOWER(REPLACE(SUBSTRING_INDEX(d.file_name, '.', 1), ' ', '')) = ?
                          )
                        ORDER BY
                          CASE
                            WHEN d.title = ? THEN 1
                            WHEN d.file_name = ? THEN 2
                            WHEN LOWER(REPLACE(SUBSTRING_INDEX(d.file_name, '.', 1), ' ', '')) = ? THEN 3
                            WHEN LOWER(REPLACE(d.title, ' ', '')) = ? THEN 4
                            WHEN LOWER(REPLACE(d.file_name, ' ', '')) = ? THEN 5
                            ELSE 99
                          END,
                          d.id DESC
                        LIMIT 1
                        """,
                title, title, normalized, normalized, normalized,
                title, title, normalized, normalized, normalized);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private void bumpUsage(Long id) {
        if (id == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE knowledge_documents SET usage_count = COALESCE(usage_count, 0) + 1 WHERE id = ?",
                    id);
        } catch (Exception ex) {
            log.debug("更新文档使用次数失败: {}", ex.getMessage());
        }
    }

    private long insertDocument(
            String title,
            Integer topicId,
            Integer subTopicId,
            List<String> keywords,
            String format,
            String fileName,
            String filePath,
            long fileSize,
            String sizeDisplay,
            String description,
            String uploadedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                            INSERT INTO knowledge_documents
                              (title, topic_id, sub_topic_id, keywords, format,
                               file_name, file_path, file_size, size_display, description,
                               upload_status, uploaded_at, uploaded_by)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'uploaded', NOW(), ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setObject(2, topicId);
            ps.setObject(3, subTopicId);
            ps.setString(4, toKeywordsJson(keywords));
            ps.setString(5, format);
            ps.setString(6, fileName);
            ps.setString(7, filePath);
            ps.setLong(8, fileSize);
            ps.setString(9, sizeDisplay);
            ps.setString(10, description == null ? "" : description);
            ps.setString(11, uploadedBy == null ? "" : uploadedBy);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "创建文档失败");
        }
        return key.longValue();
    }

    private Map<String, Object> toTopicResponse(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        Object id = row.get("id");
        item.put("id", id == null ? null : String.valueOf(id));
        item.put("topicId", toInt(id));
        item.put("topicName", str(row.get("topic_name")));
        item.put("topicCode", str(row.get("topic_code")));
        item.put("name", str(row.get("topic_name")));
        item.put("description", str(row.get("description")));
        item.put("icon", row.get("icon"));
        item.put("color", row.get("color"));
        item.put("documentCount", toInt(row.get("document_count"), 0));
        item.put("sortOrder", toInt(row.get("sort_order"), 0));
        item.put("isActive", toBool(row.get("is_active"), true));
        return item;
    }

    private Map<String, Object> toDocResponse(Map<String, Object> row) {
        List<String> keywords = parseKeywords(row.get("keywords"));
        String topicName = firstNonBlank(
                str(row.get("topic")),
                str(row.get("topic_name")),
                str(row.get("topicName")));
        String subTopicName = firstNonBlank(
                str(row.get("sub_topic")),
                str(row.get("sub_topic_name")),
                str(row.get("subTopicName")));

        Map<String, Object> item = new LinkedHashMap<>();
        Object id = row.get("id");
        item.put("id", id == null ? null : String.valueOf(id));
        item.put("topicId", row.get("topic_id"));
        item.put("subTopicId", row.get("sub_topic_id"));
        item.put("title", str(row.get("title")));
        String description = firstNonBlank(str(row.get("description")), str(row.get("summary")));
        item.put("description", description);
        item.put("summary", description);
        item.put("keywords", keywords);
        item.put("format", row.get("format"));
        item.put("fileName", row.get("file_name"));
        item.put("filePath", row.get("file_path"));
        item.put("fileSize", toLong(row.get("file_size"), 0L));
        item.put("sizeDisplay", row.get("size_display"));
        item.put("uploadStatus", row.get("upload_status"));
        item.put("progress", toInt(row.get("progress"), 0));
        item.put("isIndexed", toBool(row.get("is_indexed"), false));
        item.put("usageCount", toInt(row.get("usage_count"), 0));
        item.put("uploadedAt", formatTime(row.get("uploaded_at")));
        item.put("createdAt", formatTime(row.get("created_at")));
        item.put("ragPath", row.get("rag_path"));
        if (StringUtils.hasText(topicName)) {
            item.put("topic", Map.of("topicName", topicName));
        } else {
            item.put("topic", null);
        }
        if (StringUtils.hasText(subTopicName)) {
            item.put("subTopic", Map.of("subTopicName", subTopicName));
        } else {
            item.put("subTopic", null);
        }
        return item;
    }

    private String extractPreviewText(Path actual, String docFormat) {
        String format = docFormat == null ? "" : docFormat.toLowerCase(Locale.ROOT);
        try {
            if ("txt".equals(format) || "md".equals(format) || "markdown".equals(format)) {
                return Files.readString(actual, StandardCharsets.UTF_8);
            }
            if ("pdf".equals(format) || "docx".equals(format) || "doc".equals(format)) {
                long size = Files.size(actual);
                return String.format(
                        "[%s 文档，大小 %d 字节] 当前 Java 后端未内置 PDF/DOCX 文本抽取库，请使用 preview-stream 或 download 查看。",
                        format.toUpperCase(Locale.ROOT),
                        size);
            }
            // 未知格式：尝试按文本读取
            return Files.readString(actual, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.debug("读取预览文本失败: {}", ex.getMessage());
            return "";
        }
    }

    private String extractUploadText(byte[] contentBytes, String docFormat) {
        if ("txt".equals(docFormat) || "md".equals(docFormat)) {
            try {
                return new String(contentBytes, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                try {
                    return new String(contentBytes, java.nio.charset.Charset.forName("GBK"));
                } catch (Exception ignored) {
                    return new String(contentBytes, StandardCharsets.ISO_8859_1);
                }
            }
        }
        if ("pdf".equals(docFormat)) {
            return "[PDF 文档，大小 " + contentBytes.length + " 字节]";
        }
        if ("docx".equals(docFormat)) {
            return "[DOCX 文档，大小 " + contentBytes.length + " 字节]";
        }
        return "";
    }

    private List<String> extractKeywordsFromContent(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        Map<String, Integer> freq = new HashMap<>();
        Matcher matcher = WORD_PATTERN.matcher(content);
        while (matcher.find()) {
            String w = matcher.group().toLowerCase(Locale.ROOT);
            if (w.length() >= 2 && !STOP_WORDS.contains(w)) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .map(Map.Entry::getKey)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseKeywords(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        if (raw instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf)
                    .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toCollection(ArrayList::new));
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }
        if (text.startsWith("[")) {
            try {
                return objectMapper.readValue(text, new TypeReference<List<String>>() {
                });
            } catch (Exception ignored) {
                // fall through
            }
        }
        String[] parts = text.split("[,，;；\\s]+");
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                result.add(p.trim());
            }
        }
        return result;
    }

    private String toKeywordsJson(Object raw) {
        try {
            return objectMapper.writeValueAsString(parseKeywords(raw));
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static String normalizeLookupKey(String text) {
        String value = text == null ? "" : text.trim();
        value = value.replaceAll("\\.[A-Za-z0-9]+$", "");
        value = value.replaceAll("\\s+", "");
        return value.toLowerCase(Locale.ROOT);
    }

    private static String formatSize(long fileSize) {
        if (fileSize < 1024) {
            return fileSize + " B";
        }
        if (fileSize < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", fileSize / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", fileSize / 1024.0 / 1024.0);
    }

    private static String extension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return name.substring(idx).toLowerCase(Locale.ROOT);
    }

    private static String mimeType(String ext) {
        return switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".doc" -> "application/msword";
            case ".txt" -> "text/plain; charset=utf-8";
            case ".md" -> "text/markdown; charset=utf-8";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    private static String formatTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toString();
        }
        return String.valueOf(value);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return "";
    }

    private static String firstStr(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key) && body.get(key) != null) {
                return String.valueOf(body.get(key));
            }
        }
        return "";
    }

    private static Integer firstInt(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key) && body.get(key) != null && StringUtils.hasText(String.valueOf(body.get(key)))) {
                return toInt(body.get(key));
            }
        }
        return null;
    }

    private static long firstLong(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key) && body.get(key) != null) {
                return toLong(body.get(key), 0L);
            }
        }
        return 0L;
    }

    private static Integer toInt(Object value) {
        return toInt(value, null);
    }

    private static Integer toInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static Long toLong(Object value) {
        return toLong(value, null);
    }

    private static Long toLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static boolean toBool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public record ResourceFile(Resource resource, String fileName, String mediaType, boolean inline) {
    }
}
