package com.vis4srd.chat.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对齐 Python {@code SuiAgent-main/rag_skill_tool.RAGSkillTool.retrieve_evidence}
 * 与 {@code agent.SuicideAgent._prepare_response_context} 的轻量证据检索：
 * knowledge_catalog 选文件 → 关键词窗口抽片段 → 组装 sources / evidence / mindMap。
 */
@Slf4j
@Service
public class SuiAgentRagService {

    private static final int CONTEXT_WINDOW = 300;
    private static final int TOP_K_FRAGMENTS = 5;
    private static final int MAX_FILES_PER_QUERY = 2;
    private static final Pattern CJK_TOKEN = Pattern.compile("[\\u4e00-\\u9fff]{2,}");
    private static final Pattern EN_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]{1,}");
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "和", "是", "我", "你", "他", "她", "它", "在", "有", "就", "不", "及", "与",
            "关于", "什么", "怎么", "如何", "一个", "这个", "那个", "可以", "应该", "请问", "帮忙");

    private final KnowledgeProperties knowledgeProperties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, String> fileContentCache = new ConcurrentHashMap<>();
    private volatile Map<String, Object> catalogCache;

    public SuiAgentRagService(KnowledgeProperties knowledgeProperties, ObjectMapper objectMapper) {
        this.knowledgeProperties = knowledgeProperties;
        this.objectMapper = objectMapper;
    }

    public RagBundle retrieveForChat(String userInput, String aiMode) {
        List<Map<String, String>> retrievalQueries = buildRetrievalQueries(userInput, aiMode);
        List<Map<String, Object>> evidenceItems = new ArrayList<>();
        List<Map<String, String>> targetFiles = new ArrayList<>();
        List<String> contextSources = new ArrayList<>();

        for (Map<String, String> rq : retrievalQueries) {
            String query = rq.getOrDefault("query", "").trim();
            if (!StringUtils.hasText(query)) {
                continue;
            }
            contextSources.add(query);
            Map<String, Object> result = retrieveEvidence(query, MAX_FILES_PER_QUERY);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hits = result.get("hits") instanceof List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
            for (Map<String, Object> hit : hits) {
                @SuppressWarnings("unchecked")
                Map<String, String> file = hit.get("file") instanceof Map<?, ?> m
                        ? castStringMap(m)
                        : null;
                String snippet = str(hit.get("fragment")).trim();
                if (!StringUtils.hasText(snippet)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("snippet", snippet);
                row.put("file", file == null ? Map.of() : file);
                evidenceItems.add(row);
                if (file != null && StringUtils.hasText(file.get("path"))) {
                    boolean exists = targetFiles.stream().anyMatch(f -> file.get("path").equals(f.get("path")));
                    if (!exists) {
                        targetFiles.add(file);
                    }
                }
            }
        }

        Map<String, String> docMap = new LinkedHashMap<>();
        int docIndex = 1;
        for (Map<String, String> file : targetFiles) {
            docMap.put(file.get("path"), "doc_" + String.format("%03d", docIndex++));
        }

        List<Map<String, Object>> evidenceObjects = new ArrayList<>();
        for (int i = 0; i < evidenceItems.size(); i++) {
            Map<String, Object> item = evidenceItems.get(i);
            @SuppressWarnings("unchecked")
            Map<String, String> file = item.get("file") instanceof Map<?, ?> m ? castStringMap(m) : Map.of();
            String fileName = str(file.get("name"));
            String title;
            String ext;
            if (StringUtils.hasText(fileName) && fileName.contains(".")) {
                int dot = fileName.lastIndexOf('.');
                title = fileName.substring(0, dot);
                ext = fileName.substring(dot + 1);
            } else if (StringUtils.hasText(fileName)) {
                title = fileName;
                ext = "";
            } else {
                title = "未知来源";
                ext = "";
            }
            String path = str(file.get("path"));
            String docId = docMap.getOrDefault(path, "doc_000");
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("id", "evidence_" + String.format("%03d", i + 1));
            ev.put("title", title);
            ev.put("sourceType", StringUtils.hasText(ext) ? ext : "doc");
            ev.put("snippet", truncate(str(item.get("snippet")), 500));
            ev.put("claim", "用于支撑当前回答中的对应判断。");
            ev.put("docId", docId);
            evidenceObjects.add(ev);
        }

        List<Map<String, Object>> references = new ArrayList<>();
        int refIdx = 0;
        for (Map<String, String> file : targetFiles) {
            String path = file.get("path");
            String name = file.getOrDefault("name", "");
            String title = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "md";
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("id", docMap.get(path));
            ref.put("title", title);
            ref.put("type", ext);
            ref.put("topic", "");
            ref.put("subTopic", "");
            ref.put("relevanceScore", refIdx == 0 ? 1.0 : Math.max(0.5, 1.0 - refIdx * 0.1));
            references.add(ref);
            refIdx++;
        }

        // 前端 DocSource 兼容：rag_sources 使用 references 结构
        List<Map<String, Object>> ragSources = new ArrayList<>(references);

        Map<String, Object> mindMap = buildMindMapFromEvidence(userInput, evidenceObjects, contextSources);

        StringBuilder contextBlock = new StringBuilder();
        for (Map<String, Object> ev : evidenceObjects) {
            contextBlock.append("- [").append(ev.get("title")).append("] ")
                    .append(ev.get("snippet")).append("\n");
        }

        return new RagBundle(ragSources, references, evidenceObjects, contextSources, mindMap, contextBlock.toString());
    }

    public Map<String, Object> retrieveEvidence(String query, int maxFiles) {
        Map<String, Object> empty = Map.of(
                "sourceFiles", List.of(),
                "fragments", List.of(),
                "hits", List.of());
        if (!StringUtils.hasText(query)) {
            return empty;
        }
        Path kbRoot = knowledgeProperties.resolveKnowledgeRoot();
        if (!Files.isDirectory(kbRoot)) {
            log.warn("知识库目录不存在: {}", kbRoot);
            return empty;
        }
        List<ScoredPath> ranked = candidateFilesFromCatalog(query, Math.max(maxFiles, 4));
        List<Path> targetFiles;
        Map<Path, String> catalogSnippets = new LinkedHashMap<>();
        if (!ranked.isEmpty()) {
            ranked.sort(Comparator
                    .comparingInt((ScoredPath sp) -> sp.path().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md") ? 1 : 0)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(ScoredPath::score).reversed()));
            for (ScoredPath sp : ranked) {
                if (StringUtils.hasText(sp.catalogSnippet())) {
                    catalogSnippets.put(sp.path(), sp.catalogSnippet());
                }
            }
            targetFiles = ranked.stream().map(ScoredPath::path).limit(maxFiles).toList();
        } else {
            targetFiles = fallbackScanMarkdown(query, maxFiles);
        }
        if (targetFiles.isEmpty()) {
            log.info("RAG 未找到目标文件 query={} kb={}", truncate(query, 40), kbRoot);
            return empty;
        }
        List<String> keywords = extractKeywords(query);
        List<String> fragments = new ArrayList<>();
        List<Map<String, Object>> hits = new ArrayList<>();
        List<Map<String, String>> fileInfos = new ArrayList<>();

        for (Path filePath : targetFiles) {
            Map<String, String> info = Map.of("name", filePath.getFileName().toString(), "path", filePath.toString());
            fileInfos.add(info);
            List<String> fileFrags = collectFragmentsLightweight(filePath, query, keywords);
            if (fileFrags.isEmpty()) {
                String fallback = catalogSnippets.get(filePath);
                if (StringUtils.hasText(fallback)) {
                    fileFrags = List.of(fallback);
                }
            }
            for (String frag : fileFrags.stream().limit(TOP_K_FRAGMENTS).toList()) {
                fragments.add(frag);
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("file", info);
                hit.put("fragment", frag);
                hits.add(hit);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceFiles", fileInfos);
        out.put("fragments", fragments);
        out.put("hits", hits);
        return out;
    }

    private List<Map<String, String>> buildRetrievalQueries(String userInput, String aiMode) {
        String preset = toPresetIntent(aiMode);
        List<Map<String, String>> queries = new ArrayList<>();
        queries.add(Map.of("type", "knowledge", "query", userInput == null ? "" : userInput.trim(), "priority", "1"));
        if ("intervention_query".equals(preset)) {
            queries.add(Map.of("type", "safety_plan", "query", "危机干预 5行动步骤 安全计划", "priority", "2"));
            queries.add(Map.of("type", "knowledge", "query", "心理危机援助热线 988 求助资源", "priority", "3"));
        } else if ("emotional_support".equals(preset)) {
            // 原逻辑在高风险时插入 safety_plan；无 Emocc 时用关键词粗判
            if (looksHighRisk(userInput)) {
                queries.add(0, Map.of("type", "safety_plan", "query", "suicide crisis help", "priority", "1"));
            }
        }
        // professional_query / deep_think：保留用户问题单查询
        return queries;
    }

    private static String toPresetIntent(String aiMode) {
        if ("risk_assessment".equals(aiMode)) {
            return "emotional_support";
        }
        if ("intervention".equals(aiMode)) {
            return "intervention_query";
        }
        if ("scale_interpret".equals(aiMode)) {
            return "professional_query";
        }
        return "";
    }

    private static boolean looksHighRisk(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("自杀") || t.contains("自伤") || t.contains("不想活")
                || t.contains("结束生命") || t.contains("suicide") || t.contains("kill myself");
    }

    private List<Path> locateTargetFiles(String query) {
        List<ScoredPath> ranked = candidateFilesFromCatalog(query, 4);
        if (!ranked.isEmpty()) {
            ranked.sort(Comparator
                    .comparingInt((ScoredPath sp) -> sp.path().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md") ? 1 : 0)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(ScoredPath::score).reversed()));
            return ranked.stream().map(ScoredPath::path).toList();
        }
        return fallbackScanMarkdown(query, 4);
    }

    @SuppressWarnings("unchecked")
    private List<ScoredPath> candidateFilesFromCatalog(String query, int topK) {
        Map<String, Object> catalog = loadCatalog();
        Object themesObj = catalog.get("themes");
        if (!(themesObj instanceof List<?> themes)) {
            return List.of();
        }
        Path kbRoot = knowledgeProperties.resolveKnowledgeRoot();
        List<ScoredPath> ranked = new ArrayList<>();

        for (Object themeObj : themes) {
            if (!(themeObj instanceof Map<?, ?> themeRaw)) {
                continue;
            }
            Map<String, Object> theme = (Map<String, Object>) themeRaw;
            int themeScore = scoreCandidate(query, List.of(
                    str(theme.get("theme_name_zh")),
                    str(theme.get("theme_name_en")),
                    str(theme.get("description")),
                    joinList(theme.get("theme_aliases"))
            ));
            String themeDir = str(theme.get("theme_dir"));
            Object subthemesObj = theme.get("subthemes");
            if (!StringUtils.hasText(themeDir) || !(subthemesObj instanceof List<?> subthemes)) {
                continue;
            }
            for (Object subObj : subthemes) {
                if (!(subObj instanceof Map<?, ?> subRaw)) {
                    continue;
                }
                Map<String, Object> sub = (Map<String, Object>) subRaw;
                int subScore = scoreCandidate(query, List.of(
                        str(sub.get("name")),
                        str(sub.get("description")),
                        joinList(sub.get("keywords"))
                ));
                Object docsObj = sub.get("documents");
                if (!(docsObj instanceof List<?> docs)) {
                    continue;
                }
                for (Object docObj : docs) {
                    if (!(docObj instanceof Map<?, ?> docRaw)) {
                        continue;
                    }
                    Map<String, Object> doc = (Map<String, Object>) docRaw;
                    String filename = str(doc.get("filename"));
                    if (!StringUtils.hasText(filename)) {
                        continue;
                    }
                    int fileScore = scoreCandidate(query, List.of(
                            str(doc.get("title")),
                            str(doc.get("summary")),
                            joinList(doc.get("keywords")),
                            joinList(doc.get("aliases")),
                            str(doc.get("audience")),
                            str(doc.get("source"))
                    ));
                    int total = themeScore + subScore + fileScore;
                    if (total <= 0) {
                        continue;
                    }
                    Path path = kbRoot.resolve(themeDir).resolve(filename);
                    if (Files.isRegularFile(path)) {
                        // md 可读性更好，略加权
                        if (filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
                            total += 2;
                        }
                        String snippet = firstNonBlank(
                                str(doc.get("summary")),
                                str(doc.get("title")),
                                joinList(doc.get("keywords")));
                        ranked.add(new ScoredPath(total, path, snippet));
                    }
                }
            }
        }
        ranked.sort(Comparator.comparingInt(ScoredPath::score).reversed());
        List<ScoredPath> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScoredPath sp : ranked) {
            String key = sp.path().toString();
            if (!seen.add(key)) {
                continue;
            }
            selected.add(sp);
            if (selected.size() >= topK) {
                break;
            }
        }
        return selected;
    }

    private List<Path> fallbackScanMarkdown(String query, int topK) {
        Path kbRoot = knowledgeProperties.resolveKnowledgeRoot();
        if (!Files.isDirectory(kbRoot)) {
            return List.of();
        }
        List<String> keywords = extractKeywords(query);
        List<ScoredPath> ranked = new ArrayList<>();
        try (var walk = Files.walk(kbRoot)) {
            walk.filter(p -> Files.isRegularFile(p))
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".md") || n.endsWith(".txt");
                    })
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("data_structure.md"))
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("knowledge_schema.md"))
                    .forEach(p -> {
                        String content = readFileContent(p);
                        if (!StringUtils.hasText(content)) {
                            return;
                        }
                        int score = 0;
                        String lower = content.toLowerCase(Locale.ROOT);
                        for (String kw : keywords) {
                            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                                score += Math.min(kw.length(), 4);
                            }
                        }
                        if (score > 0) {
                            ranked.add(new ScoredPath(score, p, ""));
                        }
                    });
        } catch (Exception ex) {
            log.warn("扫描知识库 md 失败: {}", ex.getMessage());
        }
        ranked.sort(Comparator.comparingInt(ScoredPath::score).reversed());
        return ranked.stream().limit(topK).map(ScoredPath::path).toList();
    }

    private List<String> collectFragmentsLightweight(Path filePath, String query, List<String> keywords) {
        List<String> frags = new ArrayList<>();
        if (keywords != null && !keywords.isEmpty()) {
            frags.addAll(searchContextInFile(filePath, keywords));
        }
        if (frags.isEmpty()) {
            // 轻量退化：用文件名前缀/摘要窗口；若能读正文则取含查询词的首段
            String content = readFileContent(filePath);
            if (StringUtils.hasText(content)) {
                List<String> qk = extractKeywords(query);
                List<String> found = searchContextInContent(content, qk.isEmpty() ? List.of(query) : qk);
                frags.addAll(found);
                if (frags.isEmpty() && content.length() > 80) {
                    frags.add(truncate(content.replaceAll("\\s+", " ").trim(), CONTEXT_WINDOW));
                }
            }
        }
        // 去重
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (String f : frags) {
            if (StringUtils.hasText(f)) {
                uniq.add(f.trim());
            }
        }
        return new ArrayList<>(uniq);
    }

    private List<String> searchContextInFile(Path filePath, List<String> keywords) {
        String content = readFileContent(filePath);
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        return searchContextInContent(content, keywords);
    }

    private List<String> searchContextInContent(String content, List<String> keywords) {
        List<Integer> positions = new ArrayList<>();
        for (String kw : keywords) {
            if (!StringUtils.hasText(kw)) {
                continue;
            }
            String lowerContent = content.toLowerCase(Locale.ROOT);
            String lowerKw = kw.toLowerCase(Locale.ROOT);
            int from = 0;
            while (from < lowerContent.length()) {
                int idx = lowerContent.indexOf(lowerKw, from);
                if (idx < 0) {
                    break;
                }
                positions.add(idx);
                from = idx + Math.max(1, lowerKw.length());
            }
        }
        if (positions.isEmpty()) {
            return List.of();
        }
        positions = positions.stream().distinct().sorted().toList();
        int half = CONTEXT_WINDOW / 2;
        List<String> fragments = new ArrayList<>();
        Integer mergedStart = null;
        Integer mergedEnd = null;
        for (int pos : positions) {
            int start = Math.max(0, pos - half);
            int end = Math.min(content.length(), pos + half);
            if (mergedStart == null) {
                mergedStart = start;
                mergedEnd = end;
            } else if (start <= mergedEnd) {
                mergedEnd = Math.max(mergedEnd, end);
            } else {
                fragments.add(content.substring(mergedStart, mergedEnd).trim());
                mergedStart = start;
                mergedEnd = end;
            }
        }
        if (mergedStart != null) {
            fragments.add(content.substring(mergedStart, mergedEnd).trim());
        }
        return fragments;
    }

    private String readFileContent(Path filePath) {
        String key = filePath.toAbsolutePath().normalize().toString();
        return fileContentCache.computeIfAbsent(key, k -> {
            try {
                String name = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".md") || name.endsWith(".txt")) {
                    return Files.readString(filePath, StandardCharsets.UTF_8);
                }
                if (name.endsWith(".pdf")) {
                    return readPdf(filePath);
                }
                return "";
            } catch (Exception ex) {
                log.warn("读取知识文件失败 {}: {}", filePath.getFileName(), ex.getMessage());
                return "";
            }
        });
    }

    private String readPdf(Path filePath) {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (Exception ex) {
            log.warn("PDF 文本提取失败 {}: {}", filePath.getFileName(), ex.getMessage());
            return "";
        }
    }

    private Map<String, Object> loadCatalog() {
        Map<String, Object> cached = catalogCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (catalogCache != null) {
                return catalogCache;
            }
            Path catalogPath = knowledgeProperties.resolveKnowledgeRoot().resolve("knowledge_catalog.json");
            try {
                if (!Files.isRegularFile(catalogPath)) {
                    catalogCache = Map.of();
                    return catalogCache;
                }
                catalogCache = objectMapper.readValue(catalogPath.toFile(), new TypeReference<>() {
                });
            } catch (Exception ex) {
                log.warn("读取 knowledge_catalog.json 失败: {}", ex.getMessage());
                catalogCache = Map.of();
            }
            return catalogCache;
        }
    }

    private int scoreCandidate(String query, List<String> texts) {
        String normalizedQuery = normalizeText(query);
        if (!StringUtils.hasText(normalizedQuery)) {
            return 0;
        }
        int score = 0;
        for (String raw : texts) {
            String normalizedText = normalizeText(raw);
            if (!StringUtils.hasText(normalizedText)) {
                continue;
            }
            if (normalizedQuery.contains(normalizedText)) {
                score += Math.max(6, normalizedText.length());
            } else if (normalizedText.contains(normalizedQuery)) {
                score += 4;
            } else {
                for (String kw : extractKeywords(raw)) {
                    if (normalizedQuery.contains(normalizeText(kw))) {
                        score += Math.min(kw.length(), 3);
                    }
                }
            }
        }
        return score;
    }

    private List<String> extractKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        LinkedHashSet<String> words = new LinkedHashSet<>();
        Matcher cjk = CJK_TOKEN.matcher(text);
        while (cjk.find()) {
            String w = cjk.group();
            if (!STOPWORDS.contains(w)) {
                words.add(w);
            }
        }
        Matcher en = EN_TOKEN.matcher(text);
        while (en.find()) {
            String w = en.group().toLowerCase(Locale.ROOT);
            if (w.length() > 1) {
                words.add(w);
            }
        }
        // 再切 2-gram for longer CJK spans
        for (String w : List.copyOf(words)) {
            if (w.length() >= 4 && w.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                for (int i = 0; i + 2 <= w.length(); i++) {
                    words.add(w.substring(i, i + 2));
                }
            }
        }
        return words.stream().limit(12).toList();
    }

    private Map<String, Object> buildMindMapFromEvidence(
            String userInput, List<Map<String, Object>> evidenceObjects, List<String> contextSources) {
        String questionLabel = userInput != null && userInput.length() > 18
                ? userInput.substring(0, 18) + "..." : (userInput == null ? "当前问题" : userInput);
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", "question");
        q.put("label", StringUtils.hasText(questionLabel) ? questionLabel : "当前问题");
        q.put("group", "question");
        q.put("description", "当前问答中心问题，图谱围绕它展开风险判断、事实依据与行动建议。");
        q.put("relatedEvidenceIds", evidenceObjects.stream().limit(3).map(e -> str(e.get("id"))).toList());
        nodes.add(q);

        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add("question");

        int added = 0;
        for (int i = 0; i < evidenceObjects.size() && added < 4; i++) {
            Map<String, Object> evidence = evidenceObjects.get(i);
            String label = str(evidence.get("title"));
            if (label.length() > 18) {
                label = label.substring(0, 18);
            }
            if (!seen.add(label)) {
                continue;
            }
            String nodeId = "evidence_" + added;
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", nodeId);
            node.put("label", label);
            node.put("group", added < 2 ? "core" : "support");
            node.put("description", truncate(str(evidence.get("snippet")), 120));
            node.put("relatedEvidenceIds", List.of(str(evidence.get("id"))));
            nodes.add(node);
            edges.add(Map.of("source", "question", "target", nodeId, "label", "证据支撑"));
            added++;
        }

        if (added == 0) {
            for (int i = 0; i < Math.min(contextSources.size(), 3); i++) {
                String topic = contextSources.get(i);
                String nodeId = "context_" + i;
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", nodeId);
                node.put("label", topic.length() > 20 ? topic.substring(0, 20) + "..." : topic);
                node.put("group", i < 2 ? "core" : "support");
                node.put("description", "围绕“" + topic + "”补充当前问题的判断依据或处置线索。");
                node.put("relatedEvidenceIds", List.of());
                nodes.add(node);
                edges.add(Map.of("source", "question", "target", nodeId, "label", "关联线索"));
            }
        }

        Map<String, Object> mindMap = new LinkedHashMap<>();
        mindMap.put("nodes", nodes);
        mindMap.put("edges", edges);
        mindMap.put("summary", "图谱展示当前问题与检索证据之间的核心关系，可用于解释回答依据与后续处置方向。");
        mindMap.put("focusNodeId", "question");
        return mindMap;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String joinList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (o != null) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(o);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> castStringMap(Map<?, ?> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record ScoredPath(int score, Path path, String catalogSnippet) {
    }

    public record RagBundle(
            List<Map<String, Object>> ragSources,
            List<Map<String, Object>> references,
            List<Map<String, Object>> evidence,
            List<String> contextSources,
            Map<String, Object> mindMap,
            String contextBlock
    ) {
    }
}
