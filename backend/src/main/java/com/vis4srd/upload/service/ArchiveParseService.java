package com.vis4srd.upload.service;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.upload.dto.ParsedArchiveRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 档案 CSV/TXT 解析（xlsx 暂不支持）。
 */
@Service
public class ArchiveParseService {

    private static final Pattern QUOTED_ITEM = Pattern.compile("[\"']([^\"']*)[\"']");
    private static final Pattern TIMESTAMP_ITEM = Pattern.compile("Timestamp\\('([^']+)'\\)");

    public ParseResult parse(Path filePath, String dataSource) {
        String source = ArchiveTemplateService.normalizeKey(dataSource);
        if (!ArchiveTemplateService.BUILTIN_TEMPLATE_SPECS.containsKey(source)) {
            throw new BusinessException("不支持的数据源: " + dataSource);
        }

        String ext = extension(filePath.getFileName().toString());
        if ("xlsx".equals(ext)) {
            throw new BusinessException("暂不支持 xlsx，请先转为 CSV/TXT");
        }
        if (!"csv".equals(ext) && !"txt".equals(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext);
        }

        RowsAndHeaders loaded = readDelimitedRows(filePath);
        validateRequiredColumns(source, loaded.headers());

        List<ParsedArchiveRecord> records = new ArrayList<>();
        int rowIndex = 0;
        for (Map<String, String> rawRow : loaded.rows()) {
            rowIndex++;
            Map<String, String> row = normalizeRowKeys(rawRow);
            ParsedArchiveRecord record = switch (source) {
                case "reddit" -> parseReddit(row, rowIndex);
                case "bigdata" -> parseBigdata(row, rowIndex);
                case "sigir" -> parseSigir(row, rowIndex);
                case "weibo" -> parseWeibo(row, rowIndex);
                default -> throw new BusinessException("不支持的数据源: " + dataSource);
            };
            if (record.getPosts() == null || record.getPosts().isEmpty()) {
                throw new BusinessException("第 " + rowIndex + " 行没有解析出任何贴文");
            }
            records.add(record);
        }

        int totalPosts = records.stream().mapToInt(r -> r.getPosts().size()).sum();
        Map<String, Integer> riskDist = new LinkedHashMap<>();
        for (ParsedArchiveRecord record : records) {
            String name = labelToName(record.getLabel(), source);
            riskDist.merge(name, 1, Integer::sum);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_users", records.size());
        stats.put("total_posts", totalPosts);
        stats.put("risk_distribution", riskDist);
        stats.put("columns", loaded.headers());
        return new ParseResult(records, stats);
    }

    public String labelToName(int label, String dataSource) {
        RiskSchema schema = buildRiskSchema(dataSource, List.of(
                ParsedArchiveRecord.builder().userId("").posts(List.of("")).label(label).build()));
        return schema.fineLabels().getOrDefault(String.valueOf(label), "风险" + label);
    }

    public RiskSchema buildRiskSchema(String dataSource, List<ParsedArchiveRecord> records) {
        int maxLabel = records.stream().mapToInt(ParsedArchiveRecord::getLabel).max().orElse(0);
        String source = ArchiveTemplateService.normalizeKey(dataSource);
        if ("sigir".equals(source) || "weibo".equals(source) || maxLabel <= 1) {
            return new RiskSchema(
                    Map.of("0", "无风险", "1", "高风险"),
                    Map.of("0", "low", "1", "high"),
                    2);
        }
        if ("bigdata".equals(source)) {
            return new RiskSchema(
                    Map.of("0", "无风险", "1", "低风险", "2", "中风险", "3", "高风险"),
                    Map.of("0", "low", "1", "low", "2", "medium", "3", "high"),
                    4);
        }
        return new RiskSchema(
                Map.of("0", "无风险", "1", "极低风险", "2", "低风险", "3", "中风险", "4", "高风险"),
                Map.of("0", "low", "1", "low", "2", "low", "3", "medium", "4", "high"),
                5);
    }

    private ParsedArchiveRecord parseReddit(Map<String, String> row, int rowIndex) {
        String userId = value(row, "user");
        if (userId.isBlank()) {
            throw new BusinessException("第 " + rowIndex + " 行缺少 User");
        }
        List<String> posts = parsePythonList(value(row, "post"));
        int label = parseLabel(value(row, "label"), "Label", rowIndex);
        List<String> emojis = parseEmojiSequence(value(row, "emojisequence"));
        return buildRecord(userId, posts, label, null, emojis);
    }

    private ParsedArchiveRecord parseBigdata(Map<String, String> row, int rowIndex) {
        String userId = value(row, "user_id");
        if (userId.isBlank()) {
            throw new BusinessException("第 " + rowIndex + " 行缺少 user_id");
        }
        List<String> posts = parsePythonList(value(row, "post_sequence"));
        int label = parseLabel(value(row, "suicide_risk"), "suicide_risk", rowIndex);
        List<String> timestamps = parseTimestampList(value(row, "created_utc"));
        String emojiRaw = value(row, "emjio_sequenc");
        if (emojiRaw.isBlank()) {
            emojiRaw = value(row, "emjio_sequence");
        }
        List<String> emojis = parseEmojiSequence(emojiRaw);
        return buildRecord(userId, posts, label, timestamps, emojis);
    }

    private ParsedArchiveRecord parseSigir(Map<String, String> row, int rowIndex) {
        String postText = value(row, "post");
        int label = parseLabel(value(row, "label"), "Label", rowIndex);
        String digest = md5Hex(postText).substring(0, 8);
        String userId = "sigir_row_" + rowIndex + "_" + digest;
        return buildRecord(userId, List.of(postText), label, null, null);
    }

    private ParsedArchiveRecord parseWeibo(Map<String, String> row, int rowIndex) {
        String userId = value(row, "user_id");
        if (userId.isBlank()) {
            throw new BusinessException("第 " + rowIndex + " 行缺少 user_id");
        }
        List<String> posts = parseNewlinePosts(value(row, "post"));
        int label = parseLabel(value(row, "label"), "label", rowIndex);
        List<String> emojis = parseEmojiSequence(value(row, "emoji_sequence"));
        return buildRecord(userId, posts, label, null, emojis);
    }

    private ParsedArchiveRecord buildRecord(
            String userId,
            List<String> posts,
            int label,
            List<String> timestamps,
            List<String> emojis) {
        List<String> cleanPosts = new ArrayList<>();
        for (String post : posts == null ? List.<String>of() : posts) {
            if (post != null && !post.isBlank()) {
                cleanPosts.add(post.trim());
            }
        }
        return ParsedArchiveRecord.builder()
                .userId(userId)
                .posts(cleanPosts)
                .label(label)
                .timestamps(alignValues(timestamps, cleanPosts.size()))
                .emojiSequences(alignValues(emojis, cleanPosts.size()))
                .build();
    }

    private List<String> alignValues(List<String> values, int totalPosts) {
        if (totalPosts <= 0) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        if (values != null) {
            for (String v : values) {
                normalized.add(v == null || v.isBlank() ? null : v);
            }
        }
        if (normalized.isEmpty()) {
            List<String> empty = new ArrayList<>(totalPosts);
            for (int i = 0; i < totalPosts; i++) {
                empty.add(null);
            }
            return empty;
        }
        if (normalized.size() == totalPosts) {
            return normalized;
        }
        if (normalized.size() == 1) {
            List<String> repeated = new ArrayList<>(totalPosts);
            for (int i = 0; i < totalPosts; i++) {
                repeated.add(normalized.get(0));
            }
            return repeated;
        }
        if (normalized.size() < totalPosts) {
            while (normalized.size() < totalPosts) {
                normalized.add(null);
            }
            return normalized;
        }
        return new ArrayList<>(normalized.subList(0, totalPosts));
    }

    /** strip []，按引号或逗号拆分；失败则整段当一条。 */
    List<String> parsePythonList(String rawText) {
        if (rawText == null) {
            return List.of();
        }
        String text = rawText.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            String inner = text.substring(1, text.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            List<String> quoted = new ArrayList<>();
            Matcher matcher = QUOTED_ITEM.matcher(inner);
            while (matcher.find()) {
                String item = matcher.group(1).trim();
                if (!item.isEmpty()) {
                    quoted.add(item);
                }
            }
            if (!quoted.isEmpty()) {
                return quoted;
            }
            List<String> byComma = new ArrayList<>();
            for (String part : inner.split(",")) {
                String s = part.trim();
                if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                    s = s.substring(1, s.length() - 1).trim();
                }
                if (!s.isEmpty()) {
                    byComma.add(s);
                }
            }
            if (!byComma.isEmpty()) {
                return byComma;
            }
            return List.of(text);
        }
        return List.of(text);
    }

    private List<String> parseNewlinePosts(String rawText) {
        if (rawText == null) {
            return List.of();
        }
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : text.split("\n")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                parts.add(s);
            }
        }
        return parts.isEmpty() ? List.of(text) : parts;
    }

    private List<String> parseEmojiSequence(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String text = rawText.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            return parsePythonList(text);
        }
        List<String> parts = new ArrayList<>();
        for (String part : text.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                parts.add(s);
            }
        }
        return parts;
    }

    private List<String> parseTimestampList(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String text = rawText.trim();
        List<String> matches = new ArrayList<>();
        Matcher matcher = TIMESTAMP_ITEM.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        if (!matches.isEmpty()) {
            return matches;
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            return parsePythonList(text);
        }
        return List.of(text);
    }

    private int parseLabel(String value, String fieldName, int rowIndex) {
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (Exception ex) {
            throw new BusinessException(
                    "第 " + rowIndex + " 行的 " + fieldName + " 不是合法数字标签: " + value);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateRequiredColumns(String dataSource, List<String> headers) {
        Map<String, Object> spec = ArchiveTemplateService.BUILTIN_TEMPLATE_SPECS.get(dataSource);
        List<Map<String, String>> requiredCols = (List<Map<String, String>>) spec.get("requiredColumns");
        List<String> required = new ArrayList<>();
        for (Map<String, String> col : requiredCols) {
            required.add(ArchiveTemplateService.normalizeKey(col.get("name")));
        }
        List<String> actual = headers.stream().map(ArchiveTemplateService::normalizeKey).toList();
        List<String> missing = required.stream().filter(r -> !actual.contains(r)).sorted().toList();
        if (!missing.isEmpty()) {
            String pretty = requiredCols.stream().map(c -> c.get("name")).reduce((a, b) -> a + ", " + b).orElse("");
            throw new BusinessException(
                    spec.get("displayName") + " 导入格式不匹配，缺少字段: " + String.join(", ", missing)
                            + "。 该系列要求字段为: " + pretty);
        }
    }

    private RowsAndHeaders readDelimitedRows(Path filePath) {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            reader.mark(8192);
            String firstLine = reader.readLine();
            reader.reset();
            char delimiter = (firstLine != null && firstLine.contains("\t")) ? '\t' : ',';

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .setDelimiter(delimiter)
                    .setIgnoreSurroundingSpaces(true)
                    .build();

            try (CSVParser parser = format.parse(reader)) {
                List<String> headers = new ArrayList<>(parser.getHeaderNames());
                // 去掉 UTF-8 BOM
                if (!headers.isEmpty() && headers.get(0).startsWith("\uFEFF")) {
                    headers.set(0, headers.get(0).substring(1));
                }
                List<Map<String, String>> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String header : parser.getHeaderNames()) {
                        String key = header.startsWith("\uFEFF") ? header.substring(1) : header;
                        String val = record.isMapped(header) ? record.get(header) : "";
                        row.put(key, val == null ? "" : val);
                    }
                    rows.add(row);
                }
                return new RowsAndHeaders(rows, headers);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取文件失败: " + ex.getMessage());
        }
    }

    private Map<String, String> normalizeRowKeys(Map<String, String> row) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : row.entrySet()) {
            normalized.put(ArchiveTemplateService.normalizeKey(e.getKey()),
                    e.getValue() == null ? "" : e.getValue());
        }
        return normalized;
    }

    private String value(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.trim();
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String md5Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new BusinessException("生成行哈希失败");
        }
    }

    public record ParseResult(List<ParsedArchiveRecord> records, Map<String, Object> stats) {
    }

    public record RiskSchema(Map<String, String> fineLabels, Map<String, String> coarseRiskMapping, int classCount) {
    }

    private record RowsAndHeaders(List<Map<String, String>> rows, List<String> headers) {
    }
}
