package com.vis4srd.upload.service;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.upload.config.ArchiveUploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置档案导入模板元数据与文件下载（对齐 Python BUILTIN_TEMPLATE_SPECS）。
 */
@Service
@RequiredArgsConstructor
public class ArchiveTemplateService {

    public static final Map<String, Map<String, Object>> BUILTIN_TEMPLATE_SPECS = buildSpecs();

    private final ArchiveUploadProperties properties;

    public Map<String, Object> buildTemplateResponse() {
        Map<String, Object> sources = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : BUILTIN_TEMPLATE_SPECS.entrySet()) {
            String source = e.getKey();
            Map<String, Object> spec = e.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", source);
            item.put("displayName", spec.get("displayName"));
            item.put("description", spec.get("description"));
            item.put("requiredColumns", spec.get("requiredColumns"));
            item.put("optionalColumns", spec.get("optionalColumns"));
            item.put("postSplitRule", spec.get("postSplitRule"));
            item.put("riskLabels", spec.get("riskLabels"));
            item.put("sampleRows", spec.get("sampleRows"));
            Map<String, String> downloads = new LinkedHashMap<>();
            downloads.put("excel", "/api/upload/archive/templates/" + source + "/excel");
            downloads.put("csv", "/api/upload/archive/templates/" + source + "/csv");
            downloads.put("txt", "/api/upload/archive/templates/" + source + "/txt");
            item.put("downloads", downloads);
            sources.put(source, item);
        }
        return Map.of("sources", sources);
    }

    @SuppressWarnings("unchecked")
    public TemplateFile resolveTemplateFile(String dataSource, String templateType) {
        String source = normalizeKey(dataSource);
        Map<String, Object> spec = BUILTIN_TEMPLATE_SPECS.get(source);
        if (spec == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "未知数据源: " + dataSource);
        }
        String kind = normalizeKey(templateType);
        String fileKey = "excel".equals(kind) ? "excel" : ("txt".equals(kind) ? "txt" : "csv");
        Map<String, String> files = (Map<String, String>) spec.get("templateFiles");
        String fileName = files.get(fileKey);
        Path path = properties.resolveTemplatesDir().resolve(fileName).normalize();
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "模板文件不存在: " + fileName);
        }
        Resource resource = new FileSystemResource(path);
        String mediaType = fileName.endsWith(".xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/plain; charset=utf-8";
        return new TemplateFile(fileName, mediaType, resource);
    }

    public boolean isKnownSource(String dataSource) {
        return BUILTIN_TEMPLATE_SPECS.containsKey(normalizeKey(dataSource));
    }

    public static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public record TemplateFile(String fileName, String mediaType, Resource resource) {
    }

    private static Map<String, Map<String, Object>> buildSpecs() {
        Map<String, Map<String, Object>> specs = new LinkedHashMap<>();
        specs.put("reddit", Map.of(
                "displayName", "Reddit系列",
                "description", "对应 reddit_500.csv 的主贴文格式。",
                "requiredColumns", List.of(
                        col("User", "用户ID"),
                        col("Post", "Python list 字符串；列表每个元素视为 1 帖"),
                        col("Label", "五分类风险标签 0..4")),
                "optionalColumns", List.of(col("EmojiSequence", "可选；与帖子顺序对齐的 emoji 信息")),
                "postSplitRule", "读取 Post 列的 Python list 字符串，列表中的每个元素都作为一条独立贴文导入。",
                "riskLabels", Map.of("0", "无风险", "1", "极低风险", "2", "低风险", "3", "中风险", "4", "高风险"),
                "sampleRows", List.of(Map.of(
                        "User", "user-001",
                        "Post", "['first reddit post', 'second reddit post']",
                        "Label", "2",
                        "EmojiSequence", "calm,worried")),
                "templateFiles", Map.of(
                        "excel", "reddit_导入模板.xlsx",
                        "csv", "reddit_导入模板.csv",
                        "txt", "reddit_导入模板.txt")));
        specs.put("bigdata", Map.of(
                "displayName", "Bigdata系列",
                "description", "对应 bigdata.csv 的主贴文格式。",
                "requiredColumns", List.of(
                        col("user_id", "用户ID"),
                        col("created_utc", "与帖子顺序对齐的时间戳列表"),
                        col("post_sequence", "Python list 字符串；列表每个元素视为 1 帖"),
                        col("suicide_risk", "四分类风险标签 0..3")),
                "optionalColumns", List.of(
                        col("emjio_sequenc", "与原始 bigdata_emoji_batch.csv 保持一致的拼写"),
                        col("emjio_sequence", "兼容拼写；与帖子顺序对齐的 emoji 信息")),
                "postSplitRule",
                "读取 post_sequence 列的列表字符串，列表中的每个元素都作为一条独立贴文导入；created_utc 按相同位置对齐。",
                "riskLabels", Map.of("0", "无风险", "1", "低风险", "2", "中风险", "3", "高风险"),
                "sampleRows", List.of(Map.of(
                        "user_id", "user_001",
                        "created_utc", "[Timestamp('2021-04-26 06:44:18'), Timestamp('2021-05-01 05:57:38')]",
                        "post_sequence", "[\"first bigdata post\", \"second bigdata post\"]",
                        "suicide_risk", "2",
                        "emjio_sequenc", "sad,panic")),
                "templateFiles", Map.of(
                        "excel", "bigdata_导入模板.xlsx",
                        "csv", "bigdata_导入模板.csv",
                        "txt", "bigdata_导入模板.txt")));
        specs.put("sigir", Map.of(
                "displayName", "SIGIR系列",
                "description", "对应 sigir.csv 的主贴文格式。",
                "requiredColumns", List.of(
                        col("Post", "单条贴文文本；每行就是 1 个用户、1 条贴文"),
                        col("Label", "二分类风险标签 0/1")),
                "optionalColumns", List.of(col("EmojiSequence", "可选；该条贴文的 emoji 信息")),
                "postSplitRule", "SIGIR 不提供显式用户ID；导入时按“行号 + 贴文内容哈希”生成稳定 user_id。每行只导入 1 帖。",
                "riskLabels", Map.of("0", "无风险", "1", "高风险"),
                "sampleRows", List.of(Map.of(
                        "Post", "what's wrong with me",
                        "Label", "1",
                        "EmojiSequence", "sad")),
                "templateFiles", Map.of(
                        "excel", "sigir_导入模板.xlsx",
                        "csv", "sigir_导入模板.csv",
                        "txt", "sigir_导入模板.txt")));
        specs.put("weibo", Map.of(
                "displayName", "Weibo系列",
                "description", "对应 weibo_1000.csv 的主贴文格式。",
                "requiredColumns", List.of(
                        col("user_id", "用户ID"),
                        col("Post", "多行文本；按换行拆分，每行视为 1 帖"),
                        col("label", "二分类风险标签 0/1")),
                "optionalColumns", List.of(col("emoji_sequence", "按逗号分段，与换行拆出的帖子顺序对齐")),
                "postSplitRule", "读取 Post 列中的多行文本，按换行拆分，每一行都作为一条独立贴文导入。",
                "riskLabels", Map.of("0", "无风险", "1", "高风险"),
                "sampleRows", List.of(Map.of(
                        "user_id", "n-0002",
                        "Post", "第一条微博\\n第二条微博",
                        "label", "1",
                        "emoji_sequence", "calm,worried")),
                "templateFiles", Map.of(
                        "excel", "weibo_导入模板.xlsx",
                        "csv", "weibo_导入模板.csv",
                        "txt", "weibo_导入模板.txt")));
        return specs;
    }

    private static Map<String, String> col(String name, String description) {
        return Map.of("name", name, "description", description);
    }
}
