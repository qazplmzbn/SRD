package com.vis4srd.dataset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.dataset.util.DatasetTextAnalysis;
import com.vis4srd.home.service.HomeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
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
public class DatasetService {

    private static final Pattern SAFE_COL = Pattern.compile("[^a-zA-Z0-9_\\u4e00-\\u9fff]");
    private static final Pattern SAFE_TABLE = Pattern.compile("[^a-zA-Z0-9_]");
    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HomeService homeService;
    private final BuiltinCsvDatasetService builtinCsvDatasetService;

    public DatasetService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            HomeService homeService,
            BuiltinCsvDatasetService builtinCsvDatasetService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.homeService = homeService;
        this.builtinCsvDatasetService = builtinCsvDatasetService;
    }

    public List<Map<String, Object>> getDatasets() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT dataset_key, display_name, description, icon, color, bg_color, text_color,
                                   language, class_system, class_count, fine_labels, coarse_risk_mapping,
                                   total_users, total_posts, total_archives, is_builtin, is_active, sort_order
                            FROM dataset_profile
                            ORDER BY sort_order ASC
                            """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                long totalUsers = toLong(row.get("total_users"));
                long totalArchives = toLong(row.get("total_archives"));
                if (totalArchives <= 0) {
                    totalArchives = totalUsers;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", String.valueOf(row.get("sort_order") == null ? 0 : row.get("sort_order")));
                item.put("datasetKey", row.get("dataset_key"));
                item.put("displayName", row.get("display_name"));
                item.put("description", row.get("description") == null ? "" : row.get("description"));
                item.put("icon", row.get("icon"));
                item.put("color", row.get("color"));
                item.put("bgColor", row.get("bg_color"));
                item.put("textColor", row.get("text_color"));
                item.put("language", row.get("language"));
                item.put("classSystem", row.get("class_system"));
                item.put("classCount", row.get("class_count") == null ? 2 : row.get("class_count"));
                item.put("fineLabels", parseJsonMap(row.get("fine_labels")));
                item.put("coarseRiskMapping", parseJsonMap(row.get("coarse_risk_mapping")));
                item.put("totalUsers", totalUsers);
                item.put("totalPosts", toLong(row.get("total_posts")));
                item.put("totalArchives", totalArchives);
                item.put("isBuiltin", toBool(row.get("is_builtin")));
                Object active = row.get("is_active");
                item.put("isActive", active == null || toBool(active));
                item.put("sortOrder", row.get("sort_order") == null ? 0 : row.get("sort_order"));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            log.warn("读取数据集列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getArchivesPage(String datasetKey, String riskLevel, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder("WHERE dataset_source = ?");
        List<Object> params = new ArrayList<>();
        params.add(datasetKey);
        if (StringUtils.hasText(riskLevel)) {
            where.append(" AND risk_level = ?");
            params.add(riskLevel);
        }

        long total = 0;
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM psychological_archives " + where,
                    Long.class,
                    params.toArray());
            total = cnt == null ? 0 : cnt;
        } catch (Exception ex) {
            log.warn("统计档案失败: {}", ex.getMessage());
        }

        List<Map<String, Object>> archives = new ArrayList<>();
        try {
            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add(offset);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT user_id, dataset_source, post_count, risk_level, risk_value, has_timestamp, has_emojis,
                                   import_timestamp, status
                            FROM psychological_archives
                            """ + where + """
                             ORDER BY import_timestamp DESC, id DESC
                            LIMIT ? OFFSET ?
                            """,
                    queryParams.toArray());
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                Object userId = row.get("user_id");
                item.put("id", userId);
                item.put("userId", userId);
                item.put("userHash", userId);
                item.put("datasetSource", row.get("dataset_source"));
                item.put("postCount", row.get("post_count"));
                item.put("riskLevel", row.get("risk_level"));
                item.put("riskValue", row.get("risk_value"));
                item.put("hasTimestamp", toBool(row.get("has_timestamp")));
                item.put("hasEmojis", toBool(row.get("has_emojis")));
                item.put("importTime", formatTs(row.get("import_timestamp")));
                item.put("source", "db");
                item.put("status", row.get("status"));
                archives.add(item);
            }
        } catch (Exception ex) {
            log.warn("查询档案分页失败: {}", ex.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archives", archives);
        data.put("total", total);
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        data.put("totalPages", total == 0 ? 1 : (total + safeSize - 1) / safeSize);
        return data;
    }

    public Map<String, Object> getUserPosts(String datasetKey, String userHash, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 500);

        long total = 0;
        List<Map<String, Object>> rows = List.of();
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM user_posts up
                            INNER JOIN psychological_archives pa ON up.archive_id = pa.id
                            WHERE pa.dataset_source = ? AND pa.user_id = ?
                            """,
                    Long.class, datasetKey, userHash);
            total = cnt == null ? 0 : cnt;
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT up.user_id, up.post_index, up.content, up.importance_score, up.importance_level,
                                   up.post_timestamp, up.emoji_sequence, up.fine_risk_value, up.review_status,
                                   pa.risk_level
                            FROM user_posts up
                            INNER JOIN psychological_archives pa ON up.archive_id = pa.id
                            WHERE pa.dataset_source = ? AND pa.user_id = ?
                            ORDER BY up.post_index ASC
                            """,
                    datasetKey, userHash);
        } catch (Exception ex) {
            log.warn("查询用户贴文失败: {}", ex.getMessage());
        }

        List<Map<String, Object>> posts = new ArrayList<>();
        boolean needsNormalize = false;
        for (Map<String, Object> row : rows) {
            String content = row.get("content") == null ? "" : String.valueOf(row.get("content"));
            Map<String, Object> evidence = DatasetTextAnalysis.analyzeRiskEvidence(content);
            Object stored = row.get("importance_score");
            double importance;
            if (stored == null) {
                importance = ((Number) evidence.get("importance_score")).doubleValue();
                needsNormalize = true;
            } else {
                importance = toDouble(stored);
            }
            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", row.get("user_id") + "_" + row.get("post_index"));
            post.put("userId", row.get("user_id"));
            post.put("postIndex", row.get("post_index"));
            post.put("content", content);
            post.put("riskLevel", row.get("risk_level"));
            post.put("riskValue", row.get("fine_risk_value"));
            post.put("sentimentScore", null);
            post.put("importanceScore", importance);
            post.put("importanceLevel", row.get("importance_level"));
            post.put("timestamp", formatTsSpace(row.get("post_timestamp")));
            post.put("hasTimestamp", row.get("post_timestamp") != null);
            post.put("hasEmojis", row.get("emoji_sequence") != null
                    && StringUtils.hasText(String.valueOf(row.get("emoji_sequence"))));
            post.put("emojiSequence", row.get("emoji_sequence"));
            post.put("status", row.get("review_status"));
            post.put("evidenceDomains", evidence.get("evidence_domains"));
            post.put("evidenceSummary", evidence.get("evidence_summary"));
            posts.add(post);
        }

        if (!posts.isEmpty() && needsNormalize) {
            double sum = posts.stream().mapToDouble(p -> toDouble(p.get("importanceScore"))).sum();
            if (sum <= 0) {
                double equal = 1.0 / posts.size();
                posts.forEach(p -> p.put("importanceScore", equal));
            } else {
                posts.forEach(p -> p.put("importanceScore", toDouble(p.get("importanceScore")) / sum));
            }
        }

        int offset = (safePage - 1) * safeSize;
        int end = Math.min(offset + safeSize, posts.size());
        List<Map<String, Object>> paged = offset >= posts.size() ? List.of() : posts.subList(offset, end);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("posts", paged);
        data.put("total", total);
        data.put("page", safePage);
        data.put("pageSize", safeSize);

        // MySQL 无帖子时回退内置 CSV（对齐 Python dataset_csv_service）
        if (total <= 0 && paged.isEmpty() && builtinCsvDatasetService.supports(datasetKey)) {
            return builtinCsvDatasetService.getUserPosts(datasetKey, userHash, safePage, safeSize);
        }
        return data;
    }

    public Map<String, Object> getUserKeywords(String datasetKey, String userHash, int topN) {
        Map<String, Object> postsResult = getUserPosts(datasetKey, userHash, 1, 500);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) postsResult.get("posts");
        List<String> texts = new ArrayList<>();
        if (posts != null) {
            for (Map<String, Object> post : posts) {
                Object c = post.get("content");
                if (c != null && StringUtils.hasText(String.valueOf(c))) {
                    texts.add(String.valueOf(c));
                }
            }
        }
        List<Map<String, Object>> keywords = DatasetTextAnalysis.extractKeywords(texts, topN);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keywords", keywords);
        data.put("total", keywords.size());
        return data;
    }

    public List<Map<String, Object>> getExternalDatasets() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT id, name, table_name, mode_type, row_count, columns_info, created_at
                            FROM custom_dataset_meta ORDER BY created_at DESC
                            """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "ext_" + r.get("id"));
                item.put("meta_id", r.get("id"));
                item.put("name", r.get("name"));
                item.put("table_name", r.get("table_name"));
                item.put("mode_type", r.get("mode_type"));
                item.put("row_count", r.get("row_count"));
                item.put("columns_info", r.get("columns_info"));
                item.put("created_at", formatTs(r.get("created_at")));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            log.warn("读取外部数据集失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getDatasetsCompare() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT dataset_key, display_name, language, class_system, class_count,
                                   total_users, total_archives, sort_order
                            FROM dataset_profile ORDER BY sort_order ASC
                            """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                long total = toLong(row.get("total_users"));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row.get("dataset_key"));
                item.put("name", row.get("display_name"));
                item.put("type", "builtin");
                item.put("total", total);
                item.put("user_count", total);
                item.put("timestamp", true);
                item.put("language", row.get("language"));
                item.put("structure", "social_media");
                item.put("balance", "balanced");
                item.put("balance_ratio", 0.5);
                item.put("task", "risk_detection");
                item.put("task_type", row.get("class_system"));
                item.put("class_count", row.get("class_count") == null ? 2 : row.get("class_count"));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> getDatasetsStats() {
        List<Map<String, Object>> datasets = getDatasets();
        long totalUsers = 0;
        long totalPosts = 0;
        Map<String, Object> perDataset = new LinkedHashMap<>();
        for (Map<String, Object> d : datasets) {
            long users = toLong(d.get("totalUsers"));
            long posts = toLong(d.get("totalPosts"));
            totalUsers += users;
            totalPosts += posts;
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("users", users);
            stats.put("posts", posts);
            perDataset.put(String.valueOf(d.get("datasetKey")), stats);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("totalPosts", totalPosts);
        data.put("datasets", perDataset);
        return data;
    }

    @Transactional
    public Map<String, Object> uploadDataset(byte[] content, String filename, String name, String modeType) {
        if (content == null || content.length == 0) {
            throw new BusinessException("文件内容为空");
        }
        if (content.length > MAX_UPLOAD_SIZE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小超过 50MB 限制");
        }
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("仅支持 CSV 文件");
        }
        ensureCustomMetaTable();

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {

            List<String> fieldnames = new ArrayList<>(parser.getHeaderNames());
            if (fieldnames.isEmpty()) {
                throw new BusinessException("CSV 缺少表头");
            }
            Map<String, String> colMap = new LinkedHashMap<>();
            for (String f : fieldnames) {
                colMap.put(f, sanitizeCol(f));
            }
            List<String> safeCols = new ArrayList<>(colMap.values());
            String tableName = "ext_" + sanitizeTable(name) + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

            String colsDdl = safeCols.stream().map(c -> "`" + c + "` TEXT").reduce((a, b) -> a + ", " + b).orElse("");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS `" + tableName + "` (id INT PRIMARY KEY AUTO_INCREMENT, "
                            + colsDdl + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            String placeholders = String.join(", ", safeCols.stream().map(c -> "?").toList());
            String insertSql = "INSERT INTO `" + tableName + "` ("
                    + String.join(", ", safeCols.stream().map(c -> "`" + c + "`").toList())
                    + ") VALUES (" + placeholders + ")";

            int rowCount = 0;
            for (CSVRecord record : parser) {
                Object[] vals = new Object[fieldnames.size()];
                for (int i = 0; i < fieldnames.size(); i++) {
                    vals[i] = record.isMapped(fieldnames.get(i)) ? record.get(fieldnames.get(i)) : null;
                }
                jdbcTemplate.update(insertSql, vals);
                rowCount++;
            }

            String columnsInfo = objectMapper.writeValueAsString(colMap);
            jdbcTemplate.update(
                    """
                            INSERT INTO custom_dataset_meta (name, table_name, mode_type, row_count, columns_info)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    name, tableName, modeType == null ? "all" : modeType, rowCount, columnsInfo);

            Long metaId = jdbcTemplate.queryForObject(
                    "SELECT id FROM custom_dataset_meta WHERE table_name = ? ORDER BY id DESC LIMIT 1",
                    Long.class, tableName);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", metaId);
            data.put("name", name);
            data.put("table_name", tableName);
            data.put("row_count", rowCount);
            data.put("columns", safeCols);
            data.put("mode_type", modeType == null ? "all" : modeType);
            return data;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("数据集上传失败: " + ex.getMessage());
        }
    }

    /**
     * 将内置 datasets/*.csv 全量写入 MySQL（psychological_archives / user_posts），
     * 作为统一主存储。user_id 使用与 CSV 回退一致的 md5 哈希。
     */
    public Map<String, Object> syncBuiltinDatasets(List<String> datasetKeys) {
        List<String> targets = (datasetKeys == null || datasetKeys.isEmpty())
                ? builtinCsvDatasetService.supportedKeys()
                : datasetKeys.stream()
                .filter(StringUtils::hasText)
                .map(k -> k.trim().toLowerCase())
                .distinct()
                .toList();

        List<Map<String, Object>> synced = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        for (String key : targets) {
            if (!builtinCsvDatasetService.supports(key)) {
                failed.add(Map.of("datasetKey", key, "reason", "不支持的数据集"));
                continue;
            }
            Path csv = builtinCsvDatasetService.resolveCsvPath(key);
            if (csv == null || !Files.isRegularFile(csv)) {
                failed.add(Map.of("datasetKey", key, "reason", "CSV 文件不存在: " + csv));
                continue;
            }
            try {
                Map<String, Object> one = syncOneBuiltinDataset(key, csv);
                synced.add(one);
            } catch (Exception ex) {
                log.error("同步内置数据集失败 dataset={}", key, ex);
                failed.add(Map.of("datasetKey", key, "reason", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }

        try {
            homeService.refreshCache();
        } catch (Exception ignored) {
            // ignore
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("synced", synced);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }

    @Transactional
    protected Map<String, Object> syncOneBuiltinDataset(String datasetKey, Path csv) throws Exception {
        List<BuiltinCsvDatasetService.ImportArchive> archives =
                builtinCsvDatasetService.loadImportArchives(datasetKey);
        if (archives.isEmpty()) {
            throw new BusinessException("CSV 无有效用户数据: " + datasetKey);
        }

        upsertDatasetProfile(datasetKey, archives);

        jdbcTemplate.update(
                """
                        DELETE up FROM user_posts up
                        INNER JOIN psychological_archives pa ON up.archive_id = pa.id
                        WHERE pa.dataset_source = ?
                        """,
                datasetKey);
        jdbcTemplate.update("DELETE FROM psychological_archives WHERE dataset_source = ?", datasetKey);

        Long batchId = insertBuiltinSyncBatch(datasetKey, csv.getFileName().toString(), archives);

        int totalPosts = 0;
        for (BuiltinCsvDatasetService.ImportArchive archive : archives) {
            Long archiveId = insertArchive(archive, batchId);
            insertPosts(archiveId, archive);
            totalPosts += archive.posts().size();
        }

        jdbcTemplate.update(
                """
                        UPDATE dataset_profile
                        SET total_users = ?, total_posts = ?, total_archives = ?, updated_at = NOW()
                        WHERE dataset_key = ?
                        """,
                archives.size(), totalPosts, archives.size(), datasetKey);

        Map<String, Object> one = new LinkedHashMap<>();
        one.put("datasetKey", datasetKey);
        one.put("archives", archives.size());
        one.put("posts", totalPosts);
        one.put("csv", csv.toString());
        one.put("batchId", batchId);
        return one;
    }

    private void upsertDatasetProfile(String datasetKey, List<BuiltinCsvDatasetService.ImportArchive> archives) {
        Map<String, Object> meta = builtinDatasetUiMeta(datasetKey);
        int classCount = switch (datasetKey) {
            case "reddit" -> 5;
            case "bigdata" -> 4;
            default -> 2;
        };
        String language = "weibo".equals(datasetKey) ? "zh" : "en";
        String display = switch (datasetKey) {
            case "reddit" -> "Reddit";
            case "bigdata" -> "BigData";
            case "sigir" -> "SIGIR";
            case "weibo" -> "Weibo";
            default -> datasetKey;
        };
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO dataset_profile (
                                dataset_key, display_name, description, icon, color, bg_color, text_color,
                                language, class_system, class_count, total_users, total_posts, total_archives,
                                is_builtin, is_active, sort_order
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?)
                            ON DUPLICATE KEY UPDATE
                                display_name = VALUES(display_name),
                                description = VALUES(description),
                                icon = VALUES(icon),
                                color = VALUES(color),
                                bg_color = VALUES(bg_color),
                                text_color = VALUES(text_color),
                                language = VALUES(language),
                                class_system = VALUES(class_system),
                                class_count = VALUES(class_count),
                                is_builtin = 1,
                                is_active = 1,
                                sort_order = VALUES(sort_order)
                            """,
                    datasetKey,
                    display,
                    "内置数据集（已由 CSV 同步至 MySQL）",
                    meta.get("icon"),
                    meta.get("color"),
                    meta.get("bg_color"),
                    meta.get("text_color"),
                    language,
                    classCount <= 2 ? "binary" : "multi-class",
                    classCount,
                    archives.size(),
                    archives.stream().mapToInt(a -> a.posts().size()).sum(),
                    archives.size(),
                    meta.get("sort_order"));
        } catch (Exception ex) {
            log.warn("更新 dataset_profile 失败 dataset={}: {}", datasetKey, ex.getMessage());
        }
    }

    private static Map<String, Object> builtinDatasetUiMeta(String datasetKey) {
        return switch (datasetKey) {
            case "reddit" -> Map.of("icon", "database", "color", "#2F6BFF", "bg_color", "bg-blue-100",
                    "text_color", "text-blue-700", "sort_order", 1);
            case "bigdata" -> Map.of("icon", "database-zap", "color", "#0F6CBD", "bg_color", "bg-cyan-100",
                    "text_color", "text-cyan-700", "sort_order", 2);
            case "sigir" -> Map.of("icon", "flask-conical", "color", "#7C3AED", "bg_color", "bg-violet-100",
                    "text_color", "text-violet-700", "sort_order", 3);
            case "weibo" -> Map.of("icon", "messages-square", "color", "#EA580C", "bg_color", "bg-orange-100",
                    "text_color", "text-orange-700", "sort_order", 4);
            default -> Map.of("icon", "database", "color", "#C19A83", "bg_color", "bg-orange-100",
                    "text_color", "text-orange-700", "sort_order", 99);
        };
    }

    private Long insertBuiltinSyncBatch(
            String datasetKey, String fileName, List<BuiltinCsvDatasetService.ImportArchive> archives) {
        String batchCode = "builtin_sync_" + datasetKey;
        int totalPosts = archives.stream().mapToInt(a -> a.posts().size()).sum();
        try {
            jdbcTemplate.update("DELETE FROM archive_import_batch WHERE batch_code = ?", batchCode);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        """
                                INSERT INTO archive_import_batch (
                                    batch_code, dataset_key, original_filename, file_format,
                                    total_rows, unique_users, unique_posts,
                                    post_count, is_manual_annotation, has_timestamp, has_emojis,
                                    accepted_rows, rejected_rows, status, committed_at
                                ) VALUES (?, ?, ?, 'csv', ?, ?, ?, 1, 0, 0, 0, ?, 0, 'committed', NOW())
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setString(i++, batchCode);
                ps.setString(i++, datasetKey);
                ps.setString(i++, fileName);
                ps.setInt(i++, archives.size());
                ps.setInt(i++, archives.size());
                ps.setInt(i++, totalPosts);
                ps.setInt(i, archives.size());
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return key == null ? null : key.longValue();
        } catch (Exception ex) {
            log.warn("写入 archive_import_batch 失败（可忽略）: {}", ex.getMessage());
            return null;
        }
    }

    private Long insertArchive(BuiltinCsvDatasetService.ImportArchive archive, Long batchId) throws Exception {
        List<Double> rawScores = archive.posts().stream()
                .map(DatasetTextAnalysis::calculateImportanceScore)
                .toList();
        List<Double> scores = normalizeScores(rawScores);
        double avg = scores.isEmpty() ? 0.0
                : Math.round(scores.stream().mapToDouble(Double::doubleValue).sum() / scores.size() * 10000.0) / 10000.0;

        List<Map<String, Object>> top = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        for (int n = 0; n < Math.min(3, order.size()); n++) {
            int idx = order.get(n);
            String content = archive.posts().get(idx);
            if (content.length() > 120) {
                content = content.substring(0, 120);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("postIndex", idx + 1);
            item.put("content", content);
            item.put("importanceScore", scores.get(idx));
            top.add(item);
        }
        String topJson = objectMapper.writeValueAsString(top);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Long finalBatchId = batchId;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                            INSERT INTO psychological_archives (
                                user_id, dataset_source, post_count, risk_level, risk_value, label,
                                has_timestamp, has_emojis, import_batch_id, import_timestamp,
                                avg_importance_score, top_posts_summary, status
                            ) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, NOW(), ?, ?, 'ready')
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, archive.userHash());
            ps.setString(i++, archive.datasetKey());
            ps.setInt(i++, archive.posts().size());
            ps.setString(i++, archive.riskLevel());
            ps.setInt(i++, archive.riskValue());
            ps.setInt(i++, archive.riskValue());
            if (finalBatchId == null) {
                ps.setObject(i++, null);
            } else {
                ps.setLong(i++, finalBatchId);
            }
            ps.setDouble(i++, avg);
            ps.setString(i, topJson);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null || key.longValue() <= 0) {
            throw new BusinessException("写入档案失败: " + archive.userHash());
        }
        return key.longValue();
    }

    private void insertPosts(Long archiveId, BuiltinCsvDatasetService.ImportArchive archive) {
        List<Double> rawScores = archive.posts().stream()
                .map(DatasetTextAnalysis::calculateImportanceScore)
                .toList();
        List<Double> scores = normalizeScores(rawScores);
        List<Object[]> batch = new ArrayList<>();
        for (int idx = 0; idx < archive.posts().size(); idx++) {
            double score = idx < scores.size() ? scores.get(idx) : 0.0;
            batch.add(new Object[]{
                    archiveId,
                    archive.userHash(),
                    idx + 1,
                    archive.posts().get(idx),
                    score,
                    DatasetTextAnalysis.importanceLevel(score),
                    archive.riskValue(),
                    "accepted"
            });
        }
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO user_posts (
                            archive_id, user_id, post_index, content, importance_score,
                            importance_level, fine_risk_value, review_status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                batch);
    }

    private static List<Double> normalizeScores(List<Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        double sum = raw.stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            double equal = 1.0 / raw.size();
            return raw.stream().map(v -> equal).toList();
        }
        return raw.stream().map(v -> v / sum).toList();
    }


    private void ensureCustomMetaTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS custom_dataset_meta (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    table_name VARCHAR(100) NOT NULL,
                    mode_type VARCHAR(50) DEFAULT 'all',
                    row_count INT DEFAULT 0,
                    columns_info JSON,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private Map<String, Object> parseJsonMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        try {
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> out = new HashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String sanitizeCol(String name) {
        String cleaned = SAFE_COL.matcher(name == null ? "" : name.trim()).replaceAll("_");
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "col_" + cleaned;
        }
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    private static String sanitizeTable(String name) {
        String cleaned = SAFE_TABLE.matcher(name == null ? "" : name.trim().toLowerCase()).replaceAll("_");
        cleaned = cleaned.replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (cleaned.isEmpty()) {
            cleaned = "dataset";
        }
        return cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned;
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static double toDouble(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
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

    private static String formatTsSpace(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString().replace('T', ' ');
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString().replace('T', ' ');
        }
        return String.valueOf(v);
    }
}
