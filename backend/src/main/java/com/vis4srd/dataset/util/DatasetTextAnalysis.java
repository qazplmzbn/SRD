package com.vis4srd.dataset.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 贴文重要性 / 风险证据 / 关键词提取（对齐 Python dataset_csv_service）。
 */
public final class DatasetTextAnalysis {

    private static final Map<String, Double> IMPORTANCE_KEYWORDS = new LinkedHashMap<>();
    private static final List<EvidenceDomain> EVIDENCE_DOMAINS = new ArrayList<>();
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "是", "我", "你", "他", "她", "它", "们", "这", "那",
            "有", "在", "和", "就", "不", "也", "都", "很", "要", "会", "可以",
            "to", "the", "a", "an", "and", "or", "but", "in", "on", "at", "for",
            "of", "with", "by", "from", "is", "it", "be", "as", "are", "was",
            "were", "been", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "shall",
            "i", "you", "he", "she", "we", "they", "me", "him", "her", "us", "them",
            "my", "your", "his", "our", "their", "its", "im", "ive", "ill",
            "what", "which", "who", "whom", "this", "that", "these", "those",
            "am", "if", "so", "not", "no", "all", "any", "some", "one", "two");
    private static final Pattern CHINESE_RE = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final Pattern ENGLISH_RE = Pattern.compile("[a-zA-Z][a-zA-Z'_-]{1,}");

    static {
        putKw("suicide", 3.5); putKw("suicidal", 3.5); putKw("kill myself", 3.5); putKw("end my life", 3.5);
        putKw("want to die", 3.3); putKw("wish i was dead", 3.3); putKw("wish i were dead", 3.3);
        putKw("better off dead", 3.3); putKw("don't want to live", 3.3); putKw("do not want to live", 3.3);
        putKw("not want to wake up", 3.1); putKw("wish i could disappear", 2.8);
        putKw("self-harm", 3.0); putKw("self harm", 3.0); putKw("cut myself", 3.0); putKw("cutting", 2.7);
        putKw("overdose", 3.0); putKw("hang myself", 3.5); putKw("jump off", 3.1); putKw("jumping off", 3.1);
        putKw("blow my head off", 3.5); putKw("take all my pills", 3.3); putKw("hurt myself", 2.8);
        putKw("hopeless", 2.4); putKw("no hope", 2.4); putKw("helpless", 2.0); putKw("trapped", 2.2);
        putKw("stuck forever", 2.0); putKw("burden", 2.3); putKw("worthless", 2.3); putKw("useless", 2.1);
        putKw("hate myself", 2.4); putKw("hate my life", 2.4); putKw("can't anymore", 2.2); putKw("give up", 2.0);
        putKw("alone", 1.5); putKw("lonely", 1.8); putKw("isolated", 1.8); putKw("no one cares", 2.0); putKw("nobody cares", 2.0);
        putKw("depressed", 1.9); putKw("depression", 1.9); putKw("empty", 1.4); putKw("numb", 1.6); putKw("crying", 1.3);
        putKw("sad", 1.0); putKw("anxiety", 1.2); putKw("panic", 1.2); putKw("overwhelmed", 1.5);
        putKw("can't sleep", 1.0); putKw("insomnia", 1.0); putKw("exhausted", 1.0);
        putKw("therapy", -0.4); putKw("medication", -0.4); putKw("counselor", -0.5); putKw("hotline", -0.6);
        putKw("getting help", -0.7); putKw("reach out", -0.5); putKw("stay safe", -0.4);

        EVIDENCE_DOMAINS.add(domain("passive_death_wish", "被动死亡愿望",
                "wish i was dead", "wish i were dead", "better off dead",
                "don't want to live", "do not want to live", "want to die",
                "wish i could disappear", "not want to wake up"));
        EVIDENCE_DOMAINS.add(domain("active_suicidal_ideation", "主动自杀意念",
                "suicide", "suicidal", "kill myself", "end my life"));
        EVIDENCE_DOMAINS.add(domain("self_harm_plan", "自伤/方法线索",
                "self-harm", "self harm", "cut myself", "cutting", "overdose",
                "hang myself", "jump off", "jumping off", "blow my head off",
                "take all my pills", "hurt myself"));
        EVIDENCE_DOMAINS.add(domain("hopelessness_burden", "绝望/负担感",
                "hopeless", "no hope", "helpless", "trapped", "stuck forever",
                "burden", "worthless", "useless", "hate myself", "hate my life",
                "can't anymore", "give up"));
        EVIDENCE_DOMAINS.add(domain("isolation_distress", "孤立/痛苦状态",
                "alone", "lonely", "isolated", "no one cares", "nobody cares",
                "depressed", "depression", "empty", "numb", "crying", "sad",
                "anxiety", "panic", "overwhelmed", "can't sleep", "insomnia", "exhausted"));
    }

    private DatasetTextAnalysis() {
    }

    public static double calculateImportanceScore(String content) {
        if (content == null || content.isBlank()) {
            return 0.0;
        }
        String lower = content.toLowerCase();
        double total = 0.0;
        for (Map.Entry<String, Double> e : IMPORTANCE_KEYWORDS.entrySet()) {
            if (lower.contains(e.getKey())) {
                total += e.getValue();
            }
        }
        double normalized = 1.0 / (1.0 + Math.exp(-total * 0.55));
        return 0.05 + normalized * 0.9;
    }

    public static Map<String, Object> analyzeRiskEvidence(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            result.put("importance_score", 0.0);
            result.put("evidence_domains", List.of());
            result.put("evidence_summary", "未识别到明显风险词汇。");
            result.put("matched_keywords", List.of());
            return result;
        }
        String lower = content.toLowerCase();
        List<Map<String, Object>> domains = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        for (EvidenceDomain domain : EVIDENCE_DOMAINS) {
            List<String> hits = new ArrayList<>();
            for (String kw : domain.keywords) {
                if (lower.contains(kw)) {
                    hits.add(kw);
                    matched.add(kw);
                }
            }
            if (!hits.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", domain.key);
                item.put("label", domain.label);
                item.put("matches", hits.subList(0, Math.min(4, hits.size())));
                item.put("count", hits.size());
                domains.add(item);
            }
        }
        String summary;
        if (domains.isEmpty()) {
            summary = "未识别到明显风险词汇。";
        } else {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < Math.min(3, domains.size()); i++) {
                Map<String, Object> d = domains.get(i);
                @SuppressWarnings("unchecked")
                List<String> matches = (List<String>) d.get("matches");
                parts.add(d.get("label") + "（" + String.join(", ", matches) + "）");
            }
            summary = String.join("；", parts);
        }
        result.put("importance_score", calculateImportanceScore(content));
        result.put("evidence_domains", domains);
        result.put("evidence_summary", summary);
        result.put("matched_keywords", matched);
        return result;
    }

    public static List<Map<String, Object>> extractKeywords(List<String> texts, int topN) {
        Map<String, Integer> freq = new HashMap<>();
        String all = String.join(" ", texts == null ? List.of() : texts);
        Matcher cn = CHINESE_RE.matcher(all);
        while (cn.find()) {
            String w = cn.group();
            if (w.length() >= 2 && !STOPWORDS.contains(w)) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        Matcher en = ENGLISH_RE.matcher(all.toLowerCase());
        while (en.find()) {
            String w = en.group().replaceAll("^['_\\-]+|['_\\-]+$", "");
            if (w.length() >= 3 && !STOPWORDS.contains(w)) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(1, topN))
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("word", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .toList();
    }

    public static String importanceLevel(double score) {
        if (score >= 0.7) {
            return "high";
        }
        if (score >= 0.4) {
            return "medium";
        }
        return "low";
    }

    public static List<Double> normalizeScores(List<Double> raw) {
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

    private static void putKw(String k, double v) {
        IMPORTANCE_KEYWORDS.put(k, v);
    }

    private static EvidenceDomain domain(String key, String label, String... keywords) {
        return new EvidenceDomain(key, label, List.of(keywords));
    }

    private record EvidenceDomain(String key, String label, List<String> keywords) {
    }
}
