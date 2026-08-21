package com.vis4srd.scale.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 心理量表目录与评分（对应 Python {@code backend/src/services/scale_catalog.py}）。
 * 定义从项目根 {@code scales/*.json} 加载；classpath {@code /scales/} 作为回退。
 */
@Component
public class ScaleCatalog {

    private static final Map<String, String> CODE_ALIASES = Map.of(
            "PHQ9", "PHQ-9",
            "CSSRS", "C-SSRS",
            "GAD7", "GAD-7",
            "DASS21", "DASS-21"
    );

    private static final Map<String, Integer> RISK_ORDER = Map.of(
            "normal", 0,
            "low", 1,
            "medium", 2,
            "high", 3
    );

    private static final Pattern SIMPLE_COMPARE = Pattern.compile(
            "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final AtomicReference<Map<String, Map<String, Object>>> CATALOG_CACHE =
            new AtomicReference<>();

    public String normalizeScaleCode(String scaleCode) {
        String code = scaleCode == null ? "" : scaleCode.strip();
        String compact = code.replace("-", "").replace("－", "").toUpperCase(Locale.ROOT);
        return CODE_ALIASES.getOrDefault(compact, code);
    }

    public List<Map<String, Object>> getAllScaleDefinitions() {
        return new ArrayList<>(loadScaleCatalog().values());
    }

    public Map<String, Object> getScaleDefinition(String code) {
        return loadScaleCatalog().get(normalizeScaleCode(code));
    }

    public Map<String, Object> getScaleMeta(String code) {
        Map<String, Object> definition = getScaleDefinition(code);
        if (definition == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> thresholds =
                (List<Map<String, Object>>) definition.getOrDefault("thresholds", List.of());
        Object defaultThreshold = null;
        for (Map<String, Object> item : thresholds) {
            String risk = asString(item.get("risk_level"));
            if ("medium".equals(risk) || "high".equals(risk)) {
                defaultThreshold = item.get("min");
                break;
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions =
                (List<Map<String, Object>>) definition.getOrDefault("questions", List.of());
        int questionCount = asInt(definition.get("total_questions"), questions.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> scoring =
                (Map<String, Object>) definition.getOrDefault("scoring", Map.of());
        Object maxScore = scoring.get("max_standard_score");
        if (maxScore == null) {
            maxScore = scoring.get("max_score");
        }
        if (maxScore == null) {
            maxScore = questionCount * 3;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", definition.get("code"));
        meta.put("name", firstNonBlank(definition.get("name"), definition.get("code")));
        meta.put("full_name", firstNonBlank(
                definition.get("full_name"), definition.get("name"), definition.get("code")));
        meta.put("category", firstNonBlank(definition.get("category"), "general"));
        meta.put("system_classification",
                definition.getOrDefault("system_classification", Map.of()));
        meta.put("question_count", questionCount);
        meta.put("max_score", maxScore);
        meta.put("threshold", defaultThreshold == null ? 0 : defaultThreshold);
        meta.put("description",
                firstNonBlank(definition.get("purpose"), definition.get("description"), ""));
        meta.put("estimated_minutes", asInt(definition.get("estimated_minutes"), 5));
        meta.put("source_url", definition.get("source_url"));
        meta.put("original_paper", definition.get("original_paper"));
        meta.put("license_note", definition.get("license_note"));
        meta.put("validated_population", definition.get("validated_population"));
        meta.put("screening_only", asBoolean(definition.get("screening_only")));
        return meta;
    }

    public Map<String, Object> evaluateScaleAnswers(String scaleCode, List<Map<String, Object>> answers) {
        Map<String, Object> definition = getScaleDefinition(scaleCode);
        if (definition == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "量表 '" + scaleCode + "' 不存在");
        }

        String normalizedCode = asString(definition.get("code"));
        if ("C-SSRS".equals(normalizedCode)) {
            return evaluateCssrs(definition, answers == null ? List.of() : answers);
        }

        Map<Integer, Map<String, Object>> questionLookup = questionMap(definition);
        List<Map<String, Object>> normalizedAnswers = new ArrayList<>();
        Map<Integer, Integer> answerMap = new HashMap<>();
        int totalScore = 0;

        for (Map<String, Object> item : answers == null ? List.<Map<String, Object>>of() : answers) {
            int qid = asInt(item.get("qId"), 0);
            int raw = asInt(item.get("score"), 0);
            answerMap.put(qid, raw);
            Map<String, Object> question = questionLookup.getOrDefault(qid, Map.of());
            int normalized = normalizeAnswerScore(normalizedCode, question, raw);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("qId", qid);
            row.put("score", raw);
            row.put("normalizedScore", normalized);
            normalizedAnswers.add(row);
            totalScore += normalized;
        }

        if ("DASS-21".equals(normalizedCode)) {
            totalScore = 0;
        } else if ("SDS".equals(normalizedCode)) {
            totalScore = roundHalfEven(totalScore * 1.25);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> thresholds =
                (List<Map<String, Object>>) definition.getOrDefault("thresholds", List.of());
        Map<String, Object> thresholdHit = thresholdHit(thresholds, totalScore);
        String overallRisk = thresholdHit != null
                ? asString(thresholdHit.getOrDefault("risk_level", "low"))
                : "low";
        String overallLabel = thresholdHit != null
                ? asString(thresholdHit.getOrDefault("label", "总分 " + totalScore))
                : "总分 " + totalScore;
        String overallSuggestion = thresholdHit != null ? asString(thresholdHit.get("suggestion")) : null;
        if (overallSuggestion != null && overallSuggestion.isBlank()) {
            overallSuggestion = null;
        }

        List<Map<String, Object>> dimensionResults = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> scoring =
                (Map<String, Object>) definition.getOrDefault("scoring", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions =
                (List<Map<String, Object>>) scoring.getOrDefault("dimensions", List.of());
        for (Map<String, Object> dimension : dimensions) {
            @SuppressWarnings("unchecked")
            List<Object> qids = (List<Object>) dimension.getOrDefault("questions", List.of());
            Set<Integer> questionIds = qids.stream().map(ScaleCatalog::asIntObj).collect(Collectors.toSet());
            int rawDimScore = 0;
            for (Map<String, Object> answer : normalizedAnswers) {
                if (questionIds.contains(asInt(answer.get("qId"), -1))) {
                    rawDimScore += asInt(answer.get("normalizedScore"), 0);
                }
            }
            int multiplier = asInt(dimension.get("multiplier"), 1);
            int dimScore = rawDimScore * multiplier;
            totalScore += dimScore;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dimThresholds =
                    (List<Map<String, Object>>) dimension.getOrDefault("thresholds", List.of());
            Map<String, Object> dimThreshold = thresholdHit(dimThresholds, dimScore);
            String dimRisk = dimThreshold != null
                    ? asString(dimThreshold.getOrDefault("risk_level", "low"))
                    : "low";
            overallRisk = bestRisk(overallRisk, dimRisk);

            Map<String, Object> dimResult = new LinkedHashMap<>();
            dimResult.put("id", dimension.get("id"));
            dimResult.put("name", dimension.get("name"));
            dimResult.put("rawScore", rawDimScore);
            dimResult.put("score", dimScore);
            dimResult.put("label", dimThreshold != null
                    ? dimThreshold.get("label")
                    : dimScore + "分");
            dimResult.put("riskLevel", dimRisk);
            dimensionResults.add(dimResult);
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> specialRules =
                (List<Map<String, Object>>) definition.getOrDefault("special_rules", List.of());
        for (Map<String, Object> rule : specialRules) {
            int itemId = asInt(rule.get("item_id"), 0);
            Map<String, Object> answer = null;
            for (Map<String, Object> a : normalizedAnswers) {
                if (asInt(a.get("qId"), -1) == itemId) {
                    answer = a;
                    break;
                }
            }
            if (answer == null) {
                continue;
            }
            String expr = Objects.toString(rule.getOrDefault("condition", ""), "")
                    .replace("score", String.valueOf(asInt(answer.get("score"), 0)));
            if (evalSimpleCondition(expr)) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("itemId", itemId);
                alert.put("action", rule.get("action"));
                alert.put("message", rule.get("message"));
                alerts.add(alert);
            }
        }

        WorkflowBundle workflow = applyScaleWorkflowRules(
                definition, totalScore, answerMap, overallRisk, alerts);
        overallRisk = workflow.overallRisk();

        String summary;
        if (!dimensionResults.isEmpty()) {
            if (thresholds == null || thresholds.isEmpty()) {
                overallLabel = "多维情绪画像";
            }
            String dimText = dimensionResults.stream()
                    .map(item -> item.get("name") + " " + item.get("score") + "分（" + item.get("label") + "）")
                    .collect(Collectors.joining("；"));
            summary = firstNonBlank(definition.get("name"), normalizedCode) + "评估完成，" + dimText;
        } else {
            summary = firstNonBlank(definition.get("name"), normalizedCode) + "评估完成，" + overallLabel;
        }

        if (Boolean.TRUE.equals(workflow.workflowSignals().get("needsFollowUp"))
                && overallSuggestion == null) {
            overallSuggestion = "建议进入中风险随访，并结合专业评估进一步确认。";
        }
        if (Boolean.TRUE.equals(workflow.workflowSignals().get("sleepSignal"))
                && overallSuggestion == null) {
            overallSuggestion = "建议关注睡眠异常线索，并结合情绪状态继续观察。";
        }
        if (Boolean.TRUE.equals(workflow.workflowSignals().get("profileOnly"))) {
            overallSuggestion = "该量表用于情绪状态画像，不单独作为危机判定依据。";
        }

        Map<String, Object> assessment = baseAssessmentPayload(definition);
        assessment.put("summary", summary);
        assessment.put("label", overallLabel);
        assessment.put("suggestion", overallSuggestion);
        assessment.put("dimensions", dimensionResults);
        assessment.put("alerts", alerts);
        assessment.put("recommendedActions", workflow.recommendedActions());
        assessment.put("recommendedNextScales", workflow.nextScales());
        assessment.put("workflowSignals", workflow.workflowSignals());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scaleCode", normalizedCode);
        result.put("totalScore", totalScore);
        result.put("riskLevel", overallRisk);
        result.put("assessmentResult", assessment);
        result.put("normalizedAnswers", normalizedAnswers);
        return result;
    }

    /** 解析并缓存量表目录（线程安全懒加载）。 */
    public Map<String, Map<String, Object>> loadScaleCatalog() {
        Map<String, Map<String, Object>> cached = CATALOG_CACHE.get();
        if (cached != null) {
            return cached;
        }
        synchronized (CATALOG_CACHE) {
            cached = CATALOG_CACHE.get();
            if (cached != null) {
                return cached;
            }
            Map<String, Map<String, Object>> catalog = new LinkedHashMap<>();
            Path scalesDir = resolveScalesDir();
            if (scalesDir != null && Files.isDirectory(scalesDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(scalesDir, "*.json")) {
                    List<Path> files = StreamSupport.stream(stream.spliterator(), false)
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .collect(Collectors.toList());
                    for (Path filePath : files) {
                        Map<String, Object> definition = MAPPER.readValue(filePath.toFile(), MAP_TYPE);
                        String code = normalizeScaleCode(
                                asString(definition.getOrDefault("code", filePath.getFileName().toString()
                                        .replaceFirst("\\.json$", ""))));
                        definition.put("code", code);
                        catalog.put(code, definition);
                    }
                } catch (IOException e) {
                    throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "加载量表目录失败: " + e.getMessage());
                }
            } else {
                loadFromClasspath(catalog);
            }
            cached = Collections.unmodifiableMap(catalog);
            CATALOG_CACHE.set(cached);
            return cached;
        }
    }

    /** 测试或热更新时可清空缓存。 */
    public void clearCache() {
        CATALOG_CACHE.set(null);
    }

    private void loadFromClasspath(Map<String, Map<String, Object>> catalog) {
        String[] known = {"BHS", "C-SSRS", "DASS-21", "GAD-7", "ISI", "PHQ-9", "SDS"};
        for (String name : known) {
            String resource = "/scales/" + name + ".json";
            try (InputStream in = ScaleCatalog.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                Map<String, Object> definition = MAPPER.readValue(in, MAP_TYPE);
                String code = normalizeScaleCode(
                        asString(definition.getOrDefault("code", name)));
                definition.put("code", code);
                catalog.put(code, definition);
            } catch (IOException e) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "加载 classpath 量表失败: " + resource);
            }
        }
        if (catalog.isEmpty()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "未找到量表 JSON（期望项目根 scales/ 或 classpath:/scales/）");
        }
    }

    /**
     * 与 Python 一致：仓库根目录下的 {@code scales/}。
     * 依次尝试 user.dir、user.dir 的父目录（从 backend 启动时）、以及相对路径 ../scales。
     */
    public static Path resolveScalesDir() {
        List<Path> candidates = new ArrayList<>();
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        candidates.add(cwd.resolve("scales"));
        candidates.add(cwd.getParent() == null ? null : cwd.getParent().resolve("scales"));
        candidates.add(cwd.resolve("../scales").normalize());
        candidates.add(cwd.resolve("../../scales").normalize());

        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate) && hasJsonFiles(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasJsonFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private Map<String, Object> evaluateCssrs(
            Map<String, Object> definition, List<Map<String, Object>> answers) {
        List<Map<String, Object>> normalizedAnswers = new ArrayList<>();
        Map<Integer, Integer> answerMap = new HashMap<>();
        int ideationScore = 0;
        int highestPositiveItem = 0;

        for (Map<String, Object> item : answers) {
            int qid = asInt(item.get("qId"), 0);
            int raw = asInt(item.get("score"), 0);
            answerMap.put(qid, raw);
            if (raw > 0) {
                highestPositiveItem = Math.max(highestPositiveItem, qid);
                if (qid <= 4) {
                    ideationScore = Math.max(ideationScore, qid);
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("qId", qid);
            row.put("score", raw);
            row.put("normalizedScore", raw);
            normalizedAnswers.add(row);
        }

        boolean behaviorPreparatory = answerMap.getOrDefault(5, 0) > 0;
        boolean behaviorAttempt = answerMap.getOrDefault(6, 0) > 0;
        boolean highRiskPositive = Stream.of(3, 4, 5, 6)
                .anyMatch(qid -> answerMap.getOrDefault(qid, 0) > 0);

        String riskLabel = "低风险";
        String riskLevel = "low";
        String suggestion = "常规随访，提供心理健康支持。";

        if (behaviorAttempt || behaviorPreparatory) {
            riskLabel = "极高风险";
            riskLevel = "high";
            suggestion = "存在自杀相关行为信号，需立即启动危机干预流程并联系专业人员。";
        } else if (answerMap.getOrDefault(4, 0) > 0 || answerMap.getOrDefault(3, 0) > 0) {
            riskLabel = "高风险";
            riskLevel = "high";
            suggestion = "存在高危自杀意念信号，需立即进行人工复核与临床评估。";
        } else if (answerMap.getOrDefault(2, 0) > 0 || answerMap.getOrDefault(1, 0) > 0) {
            riskLabel = "中风险";
            riskLevel = "medium";
            suggestion = "存在自杀相关想法，建议加强随访并评估保护因素。";
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        if (highRiskPositive) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("itemId", highestPositiveItem);
            alert.put("action", "crisis_intervention");
            alert.put("message", "C-SSRS 高危条目阳性，建议立即进入危机干预流程。");
            alerts.add(alert);
        }

        List<String> recommendedActions = new ArrayList<>();
        if (highRiskPositive) {
            recommendedActions.add("立即启动危机干预流，并安排人工复核/临床评估。");
        } else if ("medium".equals(riskLevel)) {
            recommendedActions.add("进入中风险随访，结合保护因素与现实支持系统继续评估。");
        } else {
            recommendedActions.add("保留常规随访与心理支持。");
        }

        Map<String, Object> assessment = baseAssessmentPayload(definition);
        assessment.put("summary",
                firstNonBlank(definition.get("name"), "C-SSRS") + "评估完成，当前判定为" + riskLabel + "。");
        assessment.put("label", riskLabel);
        assessment.put("suggestion", suggestion);
        assessment.put("alerts", alerts);

        List<Map<String, Object>> dimensions = new ArrayList<>();
        Map<String, Object> ideation = new LinkedHashMap<>();
        ideation.put("id", "ideation");
        ideation.put("name", "自杀意念");
        ideation.put("score", ideationScore);
        ideation.put("label", ideationScore > 0 ? ideationScore + "级" : "未见阳性");
        ideation.put("riskLevel",
                ideationScore >= 3 ? "high" : (ideationScore >= 1 ? "medium" : "low"));
        dimensions.add(ideation);

        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("id", "behavior");
        behavior.put("name", "自杀行为");
        behavior.put("score", (behaviorPreparatory ? 1 : 0) + (behaviorAttempt ? 1 : 0));
        behavior.put("label",
                (behaviorPreparatory || behaviorAttempt) ? "存在行为阳性" : "未见行为阳性");
        behavior.put("riskLevel",
                (behaviorPreparatory || behaviorAttempt) ? "high" : "low");
        dimensions.add(behavior);
        assessment.put("dimensions", dimensions);
        assessment.put("recommendedActions", recommendedActions);

        Map<String, Object> workflowSignals = new LinkedHashMap<>();
        workflowSignals.put("needsFollowUp", "medium".equals(riskLevel) || "high".equals(riskLevel));
        workflowSignals.put("needsCrisisIntervention", highRiskPositive);
        workflowSignals.put("sleepSignal", false);
        workflowSignals.put("profileOnly", false);
        assessment.put("workflowSignals", workflowSignals);
        assessment.put("highestPositiveItem", highestPositiveItem);

        Map<String, Object> branching = new LinkedHashMap<>();
        branching.put("ideationSeverity", ideationScore);
        branching.put("preparatoryBehavior", behaviorPreparatory);
        branching.put("attemptHistory", behaviorAttempt);
        branching.put("highRiskPositive", highRiskPositive);
        assessment.put("branchingSummary", branching);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scaleCode", definition.get("code"));
        result.put("totalScore", highestPositiveItem);
        result.put("riskLevel", riskLevel);
        result.put("assessmentResult", assessment);
        result.put("normalizedAnswers", normalizedAnswers);
        return result;
    }

    private WorkflowBundle applyScaleWorkflowRules(
            Map<String, Object> definition,
            int totalScore,
            Map<Integer, Integer> answerMap,
            String overallRisk,
            List<Map<String, Object>> alerts) {
        String code = asString(definition.get("code"));
        Map<String, Object> workflowSignals = new LinkedHashMap<>();
        workflowSignals.put("needsFollowUp", false);
        workflowSignals.put("needsCrisisIntervention", false);
        workflowSignals.put("sleepSignal", false);
        workflowSignals.put("profileOnly", false);
        List<String> recommendedActions = new ArrayList<>();
        List<String> nextScales = new ArrayList<>();

        if ("PHQ-9".equals(code)) {
            if (answerMap.getOrDefault(9, 0) > 0) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("itemId", 9);
                alert.put("action", "recommend_cssrs");
                alert.put("message", "PHQ-9 第9题大于0，建议立即补做 C-SSRS 进行专项自杀风险复核。");
                alerts.add(alert);
                nextScales.add("C-SSRS");
                recommendedActions.add("补做 C-SSRS 专项风险量表。");
                overallRisk = bestRisk(overallRisk, "medium");
            }
            if (totalScore >= 10) {
                workflowSignals.put("needsFollowUp", true);
                recommendedActions.add("进入中风险随访，并建议进一步专业评估。");
            }
        }

        if ("GAD-7".equals(code) && totalScore >= 10) {
            workflowSignals.put("needsFollowUp", true);
            recommendedActions.add("进入中风险随访，关注压力源与功能受损情况。");
        }

        if ("ISI".equals(code) && totalScore >= 10) {
            workflowSignals.put("sleepSignal", true);
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("itemId", 0);
            alert.put("action", "sleep_signal");
            alert.put("message", "ISI 得分达到 10 分及以上，建议标记为睡眠异常线索并结合情绪量表联动分析。");
            alerts.add(alert);
            recommendedActions.add("标记睡眠异常线索，并结合情绪/风险模块继续观察。");
        }

        if ("DASS-21".equals(code)) {
            workflowSignals.put("profileOnly", true);
            recommendedActions.add("DASS-21 用于情绪状态画像，不单独作为危机判定依据。");
        }

        return new WorkflowBundle(
                overallRisk,
                workflowSignals,
                uniqueKeepOrder(recommendedActions),
                uniqueKeepOrder(nextScales));
    }

    private static int normalizeAnswerScore(String scaleCode, Map<String, Object> question, int rawScore) {
        if ("SDS".equals(scaleCode)) {
            return rawScore;
        }
        if ("BHS".equals(scaleCode) && asBoolean(question.get("reverse"))) {
            return 1 - rawScore;
        }
        return rawScore;
    }

    private static Map<Integer, Map<String, Object>> questionMap(Map<String, Object> definition) {
        Map<Integer, Map<String, Object>> map = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions =
                (List<Map<String, Object>>) definition.getOrDefault("questions", List.of());
        for (Map<String, Object> item : questions) {
            map.put(asInt(item.get("id"), 0), item);
        }
        return map;
    }

    private static Map<String, Object> thresholdHit(List<Map<String, Object>> thresholds, double score) {
        if (thresholds == null) {
            return null;
        }
        for (Map<String, Object> threshold : thresholds) {
            double min = asDouble(threshold.get("min"), Double.NEGATIVE_INFINITY);
            double max = asDouble(threshold.get("max"), Double.POSITIVE_INFINITY);
            if (min <= score && score <= max) {
                return threshold;
            }
        }
        return null;
    }

    private static String bestRisk(String a, String b) {
        int oa = RISK_ORDER.getOrDefault(a, -1);
        int ob = RISK_ORDER.getOrDefault(b, -1);
        return oa >= ob ? a : b;
    }

    private static List<String> uniqueKeepOrder(List<String> items) {
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item == null || item.isBlank() || !seen.add(item)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private static Map<String, Object> baseAssessmentPayload(Map<String, Object> definition) {
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("summary", "");
        assessment.put("label", "");
        assessment.put("suggestion", null);
        assessment.put("dimensions", List.of());
        assessment.put("alerts", List.of());
        assessment.put("interpretation", definition.get("interpretation"));
        assessment.put("systemClassification",
                definition.getOrDefault("system_classification", Map.of()));

        Map<String, Object> authority = new LinkedHashMap<>();
        authority.put("sourceUrl", definition.get("source_url"));
        authority.put("originalPaper", definition.get("original_paper"));
        authority.put("licenseNote", definition.get("license_note"));
        authority.put("validatedPopulation", definition.get("validated_population"));
        authority.put("screeningOnly", asBoolean(definition.get("screening_only")));
        assessment.put("authority", authority);

        assessment.put("recommendedActions", List.of());
        assessment.put("recommendedNextScales", List.of());

        Map<String, Object> workflowSignals = new LinkedHashMap<>();
        workflowSignals.put("needsFollowUp", false);
        workflowSignals.put("needsCrisisIntervention", false);
        workflowSignals.put("sleepSignal", false);
        workflowSignals.put("profileOnly", false);
        assessment.put("workflowSignals", workflowSignals);
        return assessment;
    }

    /**
     * 安全求值 special_rules.condition（Python 用 eval，此处仅支持 {@code num op num}）。
     * 调用前已将 {@code score} 替换为数值。
     */
    static boolean evalSimpleCondition(String expr) {
        if (expr == null || expr.isBlank()) {
            return false;
        }
        Matcher matcher = SIMPLE_COMPARE.matcher(expr);
        if (!matcher.matches()) {
            return false;
        }
        double left = Double.parseDouble(matcher.group(1));
        String op = matcher.group(2);
        double right = Double.parseDouble(matcher.group(3));
        return switch (op) {
            case ">" -> left > right;
            case ">=" -> left >= right;
            case "<" -> left < right;
            case "<=" -> left <= right;
            case "==" -> left == right;
            case "!=" -> left != right;
            default -> false;
        };
    }

    /** 对齐 Python3 round（银行家舍入）。 */
    static int roundHalfEven(double value) {
        return new BigDecimal(Double.toString(value))
                .setScale(0, RoundingMode.HALF_EVEN)
                .intValue();
    }

    private static Object firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value);
            if (!s.isBlank()) {
                return value instanceof String ? s : value;
            }
        }
        return "";
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int asIntObj(Object value) {
        return asInt(value, 0);
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record WorkflowBundle(
            String overallRisk,
            Map<String, Object> workflowSignals,
            List<String> recommendedActions,
            List<String> nextScales) {
    }
}
