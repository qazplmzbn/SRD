package com.vis4srd.dataset.service;

import com.vis4srd.dataset.util.DatasetTextAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置 datasets/*.csv 读取（对齐 Python dataset_csv_service），
 * 在 MySQL 档案/帖子为空时供风险检测与档案列表回退。
 */
@Slf4j
@Service
public class BuiltinCsvDatasetService {

    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']+)[\"']");

    private record DatasetSpec(
            String csvRelativePath,
            String userIdColumn,
            String postColumn,
            String riskColumn,
            Map<String, String> coarseRiskMapping) {
    }

    private static final Map<String, DatasetSpec> SPECS = Map.of(
            "reddit", new DatasetSpec(
                    "reddit/reddit_500.csv", "User", "Post", "Label",
                    Map.of("0", "low", "1", "low", "2", "low", "3", "medium", "4", "high")),
            "bigdata", new DatasetSpec(
                    "bigdata/bigdata.csv", "user_id", "post_sequence", "suicide_risk",
                    Map.of("0", "low", "1", "low", "2", "medium", "3", "high")),
            "sigir", new DatasetSpec(
                    "sigir/sigir.csv", null, "Post", "Label",
                    Map.of("0", "low", "1", "high")),
            "weibo", new DatasetSpec(
                    "weibo/weibo_1000.csv", "user_id", "Post", "label",
                    Map.of("0", "low", "1", "high"))
    );

    private final Path datasetsDir;

    public BuiltinCsvDatasetService(@Value("${app.datasets.dir:../datasets}") String datasetsDir) {
        this.datasetsDir = Paths.get(datasetsDir).toAbsolutePath().normalize();
    }

    public boolean supports(String datasetKey) {
        return SPECS.containsKey(normalizeKey(datasetKey));
    }

    public List<String> supportedKeys() {
        return List.copyOf(SPECS.keySet());
    }

    public Path resolveCsvPath(String datasetKey) {
        String key = normalizeKey(datasetKey);
        DatasetSpec spec = SPECS.get(key);
        if (spec == null) {
            return null;
        }
        return datasetsDir.resolve(spec.csvRelativePath()).toAbsolutePath().normalize();
    }

    /**
     * 读取内置 CSV 全量用户（userId 已为 md5 哈希），供同步写入 MySQL。
     */
    public List<ImportArchive> loadImportArchives(String datasetKey) {
        String key = normalizeKey(datasetKey);
        DatasetSpec spec = SPECS.get(key);
        if (spec == null) {
            return List.of();
        }
        Path csv = datasetsDir.resolve(spec.csvRelativePath());
        if (!Files.isRegularFile(csv)) {
            log.warn("内置 CSV 不存在，无法导入: {}", csv);
            return List.of();
        }

        List<ImportArchive> archives = new ArrayList<>();
        try (Reader reader = openCsvReader(csv);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            int rowIndex = 0;
            for (CSVRecord record : parser) {
                rowIndex++;
                Map<String, String> row = normalizeRow(record);
                String uid = resolveUserId(spec, row, rowIndex);
                String hash = generateUserHash(key, uid);
                int riskVal = parseRisk(row.get(spec.riskColumn()));
                String level = spec.coarseRiskMapping().getOrDefault(String.valueOf(riskVal), "low");
                List<String> contents = parsePosts(row.get(spec.postColumn())).stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .toList();
                if (contents.isEmpty()) {
                    continue;
                }
                archives.add(new ImportArchive(hash, key, riskVal, level, contents));
            }
        } catch (Exception ex) {
            log.warn("读取内置 CSV 失败 dataset={}: {}", key, ex.getMessage());
            return List.of();
        }
        return archives;
    }

    public record ImportArchive(
            String userHash,
            String datasetKey,
            int riskValue,
            String riskLevel,
            List<String> posts) {
    }

    public Map<String, Object> getUserPosts(String datasetKey, String userHash, int page, int pageSize) {
        String key = normalizeKey(datasetKey);
        DatasetSpec spec = SPECS.get(key);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 500);
        Map<String, Object> empty = emptyPosts(safePage, safeSize);
        if (spec == null || !StringUtils.hasText(userHash)) {
            return empty;
        }

        Path csv = datasetsDir.resolve(spec.csvRelativePath());
        if (!Files.isRegularFile(csv)) {
            log.warn("内置 CSV 不存在: {}", csv);
            return empty;
        }

        List<Map<String, Object>> posts = new ArrayList<>();
        try (Reader reader = openCsvReader(csv);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            int rowIndex = 0;
            for (CSVRecord record : parser) {
                rowIndex++;
                Map<String, String> row = normalizeRow(record);
                String uid = resolveUserId(spec, row, rowIndex);
                if (!userHash.equals(generateUserHash(key, uid))) {
                    continue;
                }
                int riskVal = parseRisk(row.get(spec.riskColumn()));
                String riskLevel = spec.coarseRiskMapping().getOrDefault(String.valueOf(riskVal), "low");
                List<String> contents = parsePosts(row.get(spec.postColumn()));
                int idx = 0;
                for (String content : contents) {
                    idx++;
                    if (!StringUtils.hasText(content)) {
                        continue;
                    }
                    Map<String, Object> evidence = DatasetTextAnalysis.analyzeRiskEvidence(content);
                    Map<String, Object> post = new LinkedHashMap<>();
                    post.put("id", userHash + "_" + idx);
                    post.put("userId", userHash);
                    post.put("postIndex", idx);
                    post.put("content", content);
                    post.put("riskLevel", riskLevel);
                    post.put("riskValue", riskVal);
                    post.put("importanceScore", evidence.get("importance_score"));
                    post.put("importanceLevel", null);
                    post.put("timestamp", null);
                    post.put("hasTimestamp", false);
                    post.put("hasEmojis", false);
                    post.put("emojiSequence", null);
                    post.put("status", "accepted");
                    post.put("evidenceDomains", evidence.get("evidence_domains"));
                    post.put("evidenceSummary", evidence.get("evidence_summary"));
                    posts.add(post);
                }
                break; // 一个用户通常对应一行
            }
        } catch (Exception ex) {
            log.warn("读取内置 CSV 贴文失败 dataset={} hash={}: {}", key, userHash, ex.getMessage());
            return empty;
        }

        if (!posts.isEmpty()) {
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
        data.put("total", posts.size());
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        data.put("source", "builtin_csv");
        return data;
    }

    public Map<String, Object> listArchives(
            String datasetKey, String riskLevel, String keyword, int page, int pageSize) {
        String key = normalizeKey(datasetKey);
        DatasetSpec spec = SPECS.get(key);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Map<String, Object> empty = emptyArchives(safePage, safeSize);
        if (spec == null) {
            return empty;
        }
        Path csv = datasetsDir.resolve(spec.csvRelativePath());
        if (!Files.isRegularFile(csv)) {
            return empty;
        }

        List<Map<String, Object>> archives = new ArrayList<>();
        try (Reader reader = openCsvReader(csv);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            int rowIndex = 0;
            for (CSVRecord record : parser) {
                rowIndex++;
                Map<String, String> row = normalizeRow(record);
                String uid = resolveUserId(spec, row, rowIndex);
                String hash = generateUserHash(key, uid);
                int riskVal = parseRisk(row.get(spec.riskColumn()));
                String level = spec.coarseRiskMapping().getOrDefault(String.valueOf(riskVal), "low");
                if (StringUtils.hasText(riskLevel) && !riskLevel.equalsIgnoreCase(level)) {
                    continue;
                }
                if (StringUtils.hasText(keyword) && !hash.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                List<String> contents = parsePosts(row.get(spec.postColumn()));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", hash);
                item.put("userId", hash);
                item.put("userHash", hash);
                item.put("datasetSource", key);
                item.put("datasetKey", key);
                item.put("postCount", contents.size());
                item.put("riskValue", riskVal);
                item.put("riskLevel", level);
                item.put("status", "active");
                item.put("hasTimestamp", false);
                item.put("hasEmojis", false);
                item.put("importTimestamp", null);
                item.put("source", "builtin_csv");
                archives.add(item);
            }
        } catch (Exception ex) {
            log.warn("读取内置 CSV 档案失败 dataset={}: {}", key, ex.getMessage());
            return empty;
        }

        long total = archives.size();
        int offset = (safePage - 1) * safeSize;
        int end = Math.min(offset + safeSize, archives.size());
        List<Map<String, Object>> paged = offset >= archives.size() ? List.of() : archives.subList(offset, end);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archives", paged);
        data.put("total", total);
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        data.put("totalPages", total == 0 ? 0 : (total + safeSize - 1) / safeSize);
        data.put("source", "builtin_csv");
        return data;
    }

    private static Map<String, Object> emptyPosts(int page, int pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("posts", List.of());
        data.put("total", 0);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    private static Map<String, Object> emptyArchives(int page, int pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archives", List.of());
        data.put("total", 0);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("totalPages", 0);
        return data;
    }

    /** 容错读取 CSV：非法 UTF-8 字节替换为 �，避免 bigdata 等文件直接失败。 */
    private static Reader openCsvReader(Path csv) throws Exception {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new BufferedReader(new InputStreamReader(Files.newInputStream(csv), decoder));
    }

    private static String normalizeKey(String datasetKey) {
        return datasetKey == null ? "" : datasetKey.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> normalizeRow(CSVRecord record) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String name : record.getParser().getHeaderNames()) {
            if (name == null) {
                continue;
            }
            String key = name.replace("\uFEFF", "").trim();
            row.put(key, record.get(name));
        }
        return row;
    }

    private static String resolveUserId(DatasetSpec spec, Map<String, String> row, int rowIndex) {
        if (StringUtils.hasText(spec.userIdColumn())) {
            String value = row.get(spec.userIdColumn());
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "row_" + rowIndex;
    }

    static String generateUserHash(String datasetKey, String userId) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest((datasetKey + "_" + userId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 12);
        } catch (Exception ex) {
            throw new IllegalStateException("MD5 unavailable", ex);
        }
    }

    private static int parseRisk(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    static List<String> parsePosts(String postStr) {
        if (!StringUtils.hasText(postStr)) {
            return List.of();
        }
        String text = postStr.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            Matcher m = QUOTED.matcher(text);
            List<String> items = new ArrayList<>();
            while (m.find()) {
                String part = m.group(1).trim();
                if (!part.isEmpty()) {
                    items.add(part);
                }
            }
            if (!items.isEmpty()) {
                return items;
            }
        }
        if (text.contains("\n")) {
            List<String> parts = new ArrayList<>();
            for (String p : text.split("\\R")) {
                if (StringUtils.hasText(p)) {
                    parts.add(p.trim());
                }
            }
            if (parts.size() > 1) {
                return parts;
            }
        }
        return List.of(text);
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ex) {
            return 0;
        }
    }
}
