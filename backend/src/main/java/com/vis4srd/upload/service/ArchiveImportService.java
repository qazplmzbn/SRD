package com.vis4srd.upload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.dataset.util.DatasetTextAnalysis;
import com.vis4srd.home.service.HomeService;
import com.vis4srd.upload.config.ArchiveUploadProperties;
import com.vis4srd.upload.dto.ParsedArchiveRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 档案上传、确认导入、已上传文件列表/删除。
 */
@Slf4j
@Service
public class ArchiveImportService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "txt", "xlsx");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Pattern UNSAFE_NAME = Pattern.compile("[<>:\"|?*]");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ArchiveUploadProperties properties;
    private final ArchiveParseService parseService;
    private final ArchiveTemplateService templateService;
    private final HomeService homeService;

    public ArchiveImportService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ArchiveUploadProperties properties,
            ArchiveParseService parseService,
            ArchiveTemplateService templateService,
            HomeService homeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.parseService = parseService;
        this.templateService = templateService;
        this.homeService = homeService;
    }

    public Map<String, Object> upload(MultipartFile file, String dataSource) {
        String source = ArchiveTemplateService.normalizeKey(dataSource);
        if (!templateService.isKnownSource(source)) {
            throw new BusinessException("请选择有效数据源: "
                    + String.join(", ", ArchiveTemplateService.BUILTIN_TEMPLATE_SPECS.keySet()));
        }
        String originalName = sanitizeFilename(
                file.getOriginalFilename() == null ? "unknown.csv" : file.getOriginalFilename());
        String ext = extension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext + "，仅支持 csv, txt, xlsx");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "文件大小超过 " + (MAX_FILE_SIZE / (1024 * 1024)) + "MB 限制");
        }

        Path archivesDir = properties.resolveArchivesDir();
        try {
            Files.createDirectories(archivesDir);
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "无法创建上传目录");
        }

        String savedName = UUID.randomUUID().toString().substring(0, 8) + "_" + originalName;
        Path savedPath = archivesDir.resolve(savedName).normalize();
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(savedPath)) {
            in.transferTo(out);
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败: " + ex.getMessage());
        }

        ArchiveParseService.ParseResult parsed;
        try {
            parsed = parseService.parse(savedPath, source);
        } catch (BusinessException ex) {
            deleteQuietly(savedPath);
            throw ex;
        } catch (Exception ex) {
            deleteQuietly(savedPath);
            throw new BusinessException("文件解析失败: " + ex.getMessage());
        }
        if (parsed.records().isEmpty()) {
            deleteQuietly(savedPath);
            throw new BusinessException("文件中没有有效数据");
        }

        List<Map<String, Object>> preview = new ArrayList<>();
        for (ParsedArchiveRecord record : parsed.records()) {
            String first = record.getPosts().isEmpty() ? "" : record.getPosts().get(0);
            if (first.length() > 80) {
                first = first.substring(0, 80) + "...";
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", record.getUserId());
            item.put("postCount", record.getPosts().size());
            item.put("riskLabel", parseService.labelToName(record.getLabel(), source));
            item.put("riskValue", record.getLabel());
            item.put("firstPost", first);
            preview.add(item);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) templateService.buildTemplateResponse().get("sources");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasetKey", generateDatasetKey(originalName));
        data.put("fileName", originalName);
        data.put("savedName", savedName);
        data.put("filePath", "/uploads/archives/" + savedName);
        data.put("totalUsers", parsed.stats().get("total_users"));
        data.put("totalPosts", parsed.stats().get("total_posts"));
        data.put("riskDistribution", parsed.stats().get("risk_distribution"));
        data.put("columns", parsed.stats().get("columns"));
        data.put("preview", preview);
        data.put("template", sources.get(source));
        data.put("uploadedAt", LocalDateTime.now().toString());
        return data;
    }

    @Transactional
    public Map<String, Object> confirm(Map<String, Object> importData) {
        String datasetKey = stringVal(importData.get("datasetKey"));
        String filePath = stringVal(importData.get("filePath"));
        String dataSource = ArchiveTemplateService.normalizeKey(
                importData.get("dataSource") == null ? "reddit" : String.valueOf(importData.get("dataSource")));
        boolean isManualAnnotation = Boolean.TRUE.equals(importData.get("isManualAnnotation"))
                || "true".equalsIgnoreCase(String.valueOf(importData.getOrDefault("isManualAnnotation", "false")));

        Set<String> accepted = new HashSet<>();
        Object acceptedRaw = importData.get("acceptedRecords");
        if (acceptedRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    accepted.add(String.valueOf(o));
                }
            }
        }

        if (datasetKey.isBlank() || filePath.isBlank()) {
            throw new BusinessException("缺少必要参数");
        }
        if (!templateService.isKnownSource(dataSource)) {
            throw new BusinessException("未知数据源: " + dataSource);
        }

        Path fullPath = resolveArchiveUploadPath(filePath);
        if (!Files.isRegularFile(fullPath)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文件不存在，请重新上传");
        }

        ArchiveParseService.ParseResult parsed = parseService.parse(fullPath, dataSource);
        List<ParsedArchiveRecord> recordsToImport = accepted.isEmpty()
                ? parsed.records()
                : parsed.records().stream().filter(r -> accepted.contains(r.getUserId())).toList();

        ArchiveParseService.RiskSchema schema = parseService.buildRiskSchema(dataSource, recordsToImport);
        Map<String, Integer> fineRiskDist = new LinkedHashMap<>();
        Map<String, Integer> coarseRiskDist = new LinkedHashMap<>();
        coarseRiskDist.put("low", 0);
        coarseRiskDist.put("medium", 0);
        coarseRiskDist.put("high", 0);
        for (ParsedArchiveRecord record : recordsToImport) {
            String labelName = parseService.labelToName(record.getLabel(), dataSource);
            fineRiskDist.merge(labelName, 1, Integer::sum);
            String coarse = schema.coarseRiskMapping().getOrDefault(String.valueOf(record.getLabel()), "low");
            coarseRiskDist.merge(coarse, 1, Integer::sum);
        }

        boolean hasTimestamp = recordsToImport.stream()
                .anyMatch(r -> r.getTimestamps().stream().anyMatch(t -> t != null && !t.isBlank()));
        boolean hasEmojis = recordsToImport.stream()
                .anyMatch(r -> r.getEmojiSequences().stream().anyMatch(t -> t != null && !t.isBlank()));

        String batchCode = "BATCH_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        try {
            Long batchId = insertBatch(
                    batchCode,
                    dataSource,
                    fullPath.getFileName().toString(),
                    extension(fullPath.getFileName().toString()),
                    parsed.records().size(),
                    toInt(parsed.stats().get("total_users")),
                    toInt(parsed.stats().get("total_posts")),
                    fineRiskDist,
                    schema,
                    coarseRiskDist,
                    isManualAnnotation,
                    hasTimestamp,
                    hasEmojis,
                    recordsToImport.size(),
                    parsed.records().size() - recordsToImport.size());

            for (ParsedArchiveRecord record : recordsToImport) {
                importOneRecord(record, dataSource, batchId, schema, hasTimestamp, hasEmojis);
            }

            jdbcTemplate.update(
                    """
                            UPDATE dataset_profile
                            SET
                                total_users = (
                                    SELECT COUNT(*) FROM psychological_archives
                                    WHERE dataset_source = ?
                                ),
                                total_posts = (
                                    SELECT COALESCE(SUM(post_count), 0) FROM psychological_archives
                                    WHERE dataset_source = ?
                                ),
                                total_archives = (
                                    SELECT COUNT(*) FROM psychological_archives
                                    WHERE dataset_source = ?
                                ),
                                updated_at = NOW()
                            WHERE dataset_key = ? AND is_builtin = 1
                            """,
                    dataSource, dataSource, dataSource, dataSource);

            try {
                homeService.refreshCache();
            } catch (Exception refreshErr) {
                log.warn("刷新首页统计失败: {}", refreshErr.getMessage());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "导入成功");
            data.put("datasetKey", dataSource);
            data.put("importKey", datasetKey);
            data.put("batchCode", batchCode);
            data.put("totalUsers", recordsToImport.size());
            data.put("totalPosts", recordsToImport.stream().mapToInt(r -> r.getPosts().size()).sum());
            data.put("riskDistribution", coarseRiskDist);
            return data;
        } catch (BusinessException ex) {
            markBatchFailed(batchCode, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markBatchFailed(batchCode, ex.getMessage());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "数据库导入失败: " + ex.getMessage());
        }
    }

    public List<Map<String, Object>> listUploadedDatasets() {
        Path archivesDir = properties.resolveArchivesDir();
        if (!Files.isDirectory(archivesDir)) {
            return List.of();
        }
        List<Map<String, Object>> datasets = new ArrayList<>();
        try (var stream = Files.list(archivesDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                Map<String, Object> item = new LinkedHashMap<>();
                String fname = path.getFileName().toString();
                item.put("fileName", fname);
                item.put("filePath", "/uploads/archives/" + fname);
                try {
                    item.put("fileSize", Files.size(path));
                } catch (Exception e) {
                    item.put("fileSize", 0);
                }
                datasets.add(item);
            });
        } catch (Exception ex) {
            throw new BusinessException("列出上传文件失败: " + ex.getMessage());
        }
        return datasets;
    }

    public void deleteUploadedDataset(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException("无效的文件名");
        }
        Path filePath = properties.resolveArchivesDir().resolve(filename).normalize();
        Path root = properties.resolveArchivesDir().normalize();
        if (!filePath.startsWith(root)) {
            throw new BusinessException("无效的文件名");
        }
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        try {
            Files.delete(filePath);
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "删除失败: " + ex.getMessage());
        }
    }

    private void importOneRecord(
            ParsedArchiveRecord record,
            String dataSource,
            Long batchId,
            ArchiveParseService.RiskSchema schema,
            boolean hasTimestamp,
            boolean hasEmojis) throws Exception {
        String riskLevel = schema.coarseRiskMapping().getOrDefault(String.valueOf(record.getLabel()), "low");
        String postTimestampStart = firstNonBlank(record.getTimestamps());
        String postTimestampEnd = lastNonBlank(record.getTimestamps());

        List<Double> rawScores = record.getPosts().stream()
                .map(DatasetTextAnalysis::calculateImportanceScore)
                .toList();
        List<Double> scores = normalizeImportanceScores(rawScores);
        double avgImportance = scores.isEmpty()
                ? 0.0
                : Math.round(scores.stream().mapToDouble(Double::doubleValue).sum() / scores.size() * 10000.0) / 10000.0;

        List<Map<String, Object>> topPostsSummary = new ArrayList<>();
        List<int[]> indexed = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            indexed.add(new int[]{i});
        }
        indexed.sort(Comparator.comparingDouble((int[] a) -> scores.get(a[0])).reversed());
        for (int n = 0; n < Math.min(3, indexed.size()); n++) {
            int idx = indexed.get(n)[0];
            String content = record.getPosts().get(idx);
            if (content.length() > 120) {
                content = content.substring(0, 120);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("postIndex", idx + 1);
            item.put("content", content);
            item.put("importanceScore", round4(scores.get(idx)));
            topPostsSummary.add(item);
        }
        String topPostsJson = objectMapper.writeValueAsString(topPostsSummary);

        Long archiveId = upsertArchive(
                record, dataSource, riskLevel, batchId, hasTimestamp, hasEmojis,
                postTimestampStart, postTimestampEnd, avgImportance, topPostsJson);

        for (int idx = 0; idx < record.getPosts().size(); idx++) {
            double score = idx < scores.size() ? scores.get(idx) : 0.0;
            String importanceLevel = DatasetTextAnalysis.importanceLevel(score);
            String postTimestamp = idx < record.getTimestamps().size() ? record.getTimestamps().get(idx) : null;
            String emojiSequence = idx < record.getEmojiSequences().size()
                    ? record.getEmojiSequences().get(idx) : null;
            int emojiCount = 0;
            if (emojiSequence != null && !emojiSequence.isBlank()) {
                for (String part : emojiSequence.split(",")) {
                    if (!part.isBlank()) {
                        emojiCount++;
                    }
                }
            }
            try {
                jdbcTemplate.update(
                        """
                                INSERT INTO user_posts (
                                    archive_id, user_id, post_index, content, sentiment_score, importance_score,
                                    importance_level, micro_expressions, post_timestamp, emoji_count,
                                    emoji_sequence, fine_risk_value, review_status
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                ON DUPLICATE KEY UPDATE
                                    content = VALUES(content),
                                    importance_score = VALUES(importance_score),
                                    importance_level = VALUES(importance_level),
                                    post_timestamp = VALUES(post_timestamp),
                                    emoji_count = VALUES(emoji_count),
                                    emoji_sequence = VALUES(emoji_sequence),
                                    fine_risk_value = VALUES(fine_risk_value),
                                    review_status = VALUES(review_status)
                                """,
                        archiveId,
                        record.getUserId(),
                        idx + 1,
                        record.getPosts().get(idx),
                        null,
                        score,
                        importanceLevel,
                        null,
                        postTimestamp,
                        emojiCount,
                        emojiSequence,
                        record.getLabel(),
                        "accepted");
            } catch (Exception ex) {
                throw new BusinessException("贴文写入失败 user_id=" + record.getUserId()
                        + ", post_index=" + (idx + 1) + " :: " + ex.getMessage());
            }
        }
    }

    private Long upsertArchive(
            ParsedArchiveRecord record,
            String dataSource,
            String riskLevel,
            Long batchId,
            boolean hasTimestamp,
            boolean hasEmojis,
            String postTimestampStart,
            String postTimestampEnd,
            double avgImportance,
            String topPostsJson) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        """
                                INSERT INTO psychological_archives (
                                    user_id, dataset_source, post_count, risk_level, risk_value,
                                    label, has_timestamp, has_emojis, import_batch_id, import_timestamp,
                                    post_timestamp_start, post_timestamp_end, avg_importance_score, top_posts_summary, status
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                ON DUPLICATE KEY UPDATE
                                    post_count = VALUES(post_count),
                                    risk_level = VALUES(risk_level),
                                    risk_value = VALUES(risk_value),
                                    label = VALUES(label),
                                    has_timestamp = VALUES(has_timestamp),
                                    has_emojis = VALUES(has_emojis),
                                    import_batch_id = VALUES(import_batch_id),
                                    import_timestamp = VALUES(import_timestamp),
                                    post_timestamp_start = VALUES(post_timestamp_start),
                                    post_timestamp_end = VALUES(post_timestamp_end),
                                    avg_importance_score = VALUES(avg_importance_score),
                                    top_posts_summary = VALUES(top_posts_summary),
                                    status = 'ready'
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setString(i++, record.getUserId());
                ps.setString(i++, dataSource);
                ps.setInt(i++, record.getPosts().size());
                ps.setString(i++, riskLevel);
                ps.setInt(i++, record.getLabel());
                ps.setInt(i++, record.getLabel());
                ps.setInt(i++, hasTimestamp ? 1 : 0);
                ps.setInt(i++, hasEmojis ? 1 : 0);
                ps.setLong(i++, batchId);
                ps.setTimestamp(i++, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(i++, postTimestampStart);
                ps.setString(i++, postTimestampEnd);
                ps.setDouble(i++, avgImportance);
                ps.setString(i++, topPostsJson);
                ps.setString(i, "ready");
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key != null && key.longValue() > 0) {
                return key.longValue();
            }
            Long existing = jdbcTemplate.queryForObject(
                    "SELECT id FROM psychological_archives WHERE user_id = ? AND dataset_source = ? LIMIT 1",
                    Long.class, record.getUserId(), dataSource);
            if (existing == null) {
                throw new BusinessException("无法定位导入后的档案ID: " + record.getUserId());
            }
            return existing;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("档案写入失败 user_id=" + record.getUserId()
                    + ", data_source=" + dataSource + " :: " + ex.getMessage());
        }
    }

    private Long insertBatch(
            String batchCode,
            String dataSource,
            String filename,
            String fileFormat,
            int totalRows,
            int uniqueUsers,
            int uniquePosts,
            Map<String, Integer> fineRiskDist,
            ArchiveParseService.RiskSchema schema,
            Map<String, Integer> coarseRiskDist,
            boolean isManualAnnotation,
            boolean hasTimestamp,
            boolean hasEmojis,
            int acceptedRows,
            int rejectedRows) throws Exception {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String fineDistJson = objectMapper.writeValueAsString(fineRiskDist);
        String fineLabelsJson = objectMapper.writeValueAsString(schema.fineLabels());
        String coarseMapJson = objectMapper.writeValueAsString(schema.coarseRiskMapping());
        String coarseDistJson = objectMapper.writeValueAsString(coarseRiskDist);

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                            INSERT INTO archive_import_batch (
                                batch_code, dataset_key, original_filename, file_format,
                                total_rows, unique_users, unique_posts,
                                fine_risk_distribution, fine_class_count, fine_labels,
                                coarse_risk_mapping, coarse_risk_distribution,
                                post_count, is_manual_annotation, has_timestamp, has_emojis,
                                accepted_rows, rejected_rows, status, committed_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, batchCode);
            ps.setString(i++, dataSource);
            ps.setString(i++, filename);
            ps.setString(i++, fileFormat);
            ps.setInt(i++, totalRows);
            ps.setInt(i++, uniqueUsers);
            ps.setInt(i++, uniquePosts);
            ps.setString(i++, fineDistJson);
            ps.setInt(i++, schema.classCount());
            ps.setString(i++, fineLabelsJson);
            ps.setString(i++, coarseMapJson);
            ps.setString(i++, coarseDistJson);
            ps.setInt(i++, 1);
            ps.setBoolean(i++, isManualAnnotation);
            ps.setBoolean(i++, hasTimestamp);
            ps.setBoolean(i++, hasEmojis);
            ps.setInt(i++, acceptedRows);
            ps.setInt(i++, rejectedRows);
            ps.setString(i++, "committed");
            ps.setTimestamp(i, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("写入导入批次失败");
        }
        return key.longValue();
    }

    private void markBatchFailed(String batchCode, String message) {
        try {
            jdbcTemplate.update(
                    "UPDATE archive_import_batch SET status = 'failed', error_message = ? WHERE batch_code = ?",
                    message, batchCode);
        } catch (Exception ignored) {
            // 事务回滚时批次可能不存在，忽略
        }
    }

    private Path resolveArchiveUploadPath(String filePath) {
        try {
            Path archivesRoot = properties.resolveArchivesDir().toAbsolutePath().normalize();
            Files.createDirectories(archivesRoot);
            String name = Path.of(filePath).getFileName().toString();
            Path resolved = archivesRoot.resolve(name).toAbsolutePath().normalize();
            if (!resolved.startsWith(archivesRoot)) {
                throw new BusinessException("无效的文件路径");
            }
            return resolved;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("无效的文件路径");
        }
    }

    private List<Double> normalizeImportanceScores(List<Double> rawScores) {
        if (rawScores == null || rawScores.isEmpty()) {
            return List.of();
        }
        if (rawScores.size() == 1) {
            return List.of(1.0);
        }
        double total = rawScores.stream().mapToDouble(s -> s > 0 ? s : 0).sum();
        if (total <= 0) {
            double equal = 1.0 / rawScores.size();
            return rawScores.stream().map(s -> round4(equal)).toList();
        }
        List<Double> normalized = new ArrayList<>();
        for (Double score : rawScores) {
            normalized.add(round4(Math.max(score, 0.0) / total));
        }
        double drift = round4(1.0 - normalized.stream().mapToDouble(Double::doubleValue).sum());
        if (!normalized.isEmpty()) {
            int last = normalized.size() - 1;
            normalized.set(last, round4(normalized.get(last) + drift));
        }
        return normalized;
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String firstNonBlank(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String lastNonBlank(List<String> values) {
        if (values == null) {
            return null;
        }
        for (int i = values.size() - 1; i >= 0; i--) {
            String v = values.get(i);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String generateDatasetKey(String filename) {
        String name = filename;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        String ts = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return "custom_" + sb + "_" + ts;
    }

    private String sanitizeFilename(String filename) {
        String name = filename.replace("/", "_").replace("\\", "_");
        name = UNSAFE_NAME.matcher(name).replaceAll("_");
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot) : "";
            String base = dot >= 0 ? name.substring(0, dot) : name;
            name = base.substring(0, Math.max(1, 200 - ext.length())) + ext;
        }
        return name;
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private String stringVal(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ex) {
            return 0;
        }
    }
}
