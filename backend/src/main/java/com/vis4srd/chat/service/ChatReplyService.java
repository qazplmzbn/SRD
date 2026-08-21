package com.vis4srd.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.chat.rag.SuiAgentRagService;
import com.vis4srd.common.config.LlmProperties;
import com.vis4srd.common.llm.LlmChatClient;
import com.vis4srd.model.service.ModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话回复：对齐原 SuiAgent RAG（catalog + 文件片段）+ LLM + SSE。
 */
@Slf4j
@Service
public class ChatReplyService {

    private static final Map<String, String> SYSTEM_PROMPTS = Map.of(
            "deep_think",
            "你是心理健康支持助手。请用清晰、共情、结构化的方式帮助用户梳理情绪与问题。"
                    + "不要替代专业诊断；若出现自伤/自杀风险信号，提醒寻求紧急帮助（如当地危机热线）。"
                    + "回答时应优先依据提供的知识库证据片段，并注明依据来自哪些材料。",
            "risk_assessment",
            "你是风险识别助手。请识别表达中的自伤/自杀风险信号，说明判断依据与分级思路，"
                    + "避免恐吓或夸大；强调这不是正式临床诊断。优先引用提供的证据片段。",
            "intervention",
            "你是危机干预与陪伴支持助手。请给出可执行的即时干预步骤（1小时/今晚/24小时），"
                    + "并说明何时需要升级处置（家属、老师、医院、紧急服务）。优先依据安全计划与求助资源证据。",
            "scale_interpret",
            "你是心理量表解读助手。请结合量表分数说明可能含义，强调需结合近期状态，"
                    + "用通俗语言解释，并给出后续核查或支持建议。可引用知识库中的专业材料。"
    );

    private final ChatService chatService;
    private final SuiAgentRagService suiAgentRagService;
    private final ModelService modelService;
    private final LlmChatClient llmChatClient;
    private final LlmProperties llmProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatReplyService(
            ChatService chatService,
            SuiAgentRagService suiAgentRagService,
            ModelService modelService,
            LlmChatClient llmChatClient,
            LlmProperties llmProperties,
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.suiAgentRagService = suiAgentRagService;
        this.modelService = modelService;
        this.llmChatClient = llmChatClient;
        this.llmProperties = llmProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> sendMessage(long sessionId, String content, String aiModeRaw) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new com.vis4srd.common.exception.BusinessException("消息内容不能为空");
        }
        Map<String, Object> session = chatService.getSession(sessionId);
        String aiMode = chatService.normalizeAiMode(
                StringUtils.hasText(aiModeRaw) ? aiModeRaw : str(session.get("aiMode")));

        long userMsgId = chatService.createMessage(Map.of(
                "session_id", sessionId,
                "role", "user",
                "content", text,
                "content_type", "text"
        ));

        ReplyBundle bundle = generateReply(text, aiMode);
        Map<String, Object> assistantData = new LinkedHashMap<>();
        assistantData.put("session_id", sessionId);
        assistantData.put("role", "ai");
        assistantData.put("content", bundle.answer());
        assistantData.put("content_type", "text");
        assistantData.put("ai_model", bundle.modelName());
        assistantData.put("ai_mode", aiMode);
        assistantData.put("is_error", bundle.error());
        if (bundle.error()) {
            assistantData.put("error_message", bundle.errorMessage());
        }
        assistantData.put("references_json", bundle.references());
        assistantData.put("retrieval_sources", bundle.ragSources());
        assistantData.put("rag_context", Map.of(
                "evidence", bundle.knowledgePanel().getOrDefault("evidence", List.of()),
                "mindMap", bundle.knowledgePanel().get("mindMap"),
                "contextSources", bundle.knowledgePanel().getOrDefault("contextSources", List.of()),
                "knowledgePanel", bundle.knowledgePanel()
        ));

        long assistantId = chatService.createMessage(assistantData);
        chatService.bumpSessionAfterReply(sessionId, 2);

        Map<String, Object> assistantMsg = chatService.getMessageById(assistantId);
        // 附带 knowledgePanel 到消息顶层，方便前端
        if (assistantMsg != null) {
            assistantMsg.put("knowledgePanel", bundle.knowledgePanel());
        }
        log.debug("sendMessage userMsgId={} assistantId={}", userMsgId, assistantId);
        return assistantMsg;
    }

    public Map<String, Object> sendLegacy(Map<String, Object> body) {
        String content = str(first(body, "content")).trim();
        if (!StringUtils.hasText(content)) {
            throw new com.vis4srd.common.exception.BusinessException("消息内容不能为空");
        }
        Long sessionId = null;
        Object sid = first(body, "sessionId", "session_id");
        if (sid != null && StringUtils.hasText(String.valueOf(sid))) {
            sessionId = Long.parseLong(String.valueOf(sid));
        } else {
            Map<String, Object> created = chatService.createSession(Map.of(
                    "aiMode", first(body, "aiMode", "ai_mode"),
                    "contextType", "general"
            ));
            sessionId = Long.parseLong(String.valueOf(created.get("id")));
        }
        Map<String, Object> assistant = sendMessage(
                sessionId, content, str(first(body, "aiMode", "ai_mode")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", String.valueOf(sessionId));
        data.put("assistant_message", assistant);
        data.put("assistantMessage", assistant);
        return data;
    }

    public StreamingResponseBody streamMessage(long sessionId, String content, String aiModeRaw) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new com.vis4srd.common.exception.BusinessException("消息内容不能为空");
        }
        Map<String, Object> session = chatService.getSession(sessionId);
        String aiMode = chatService.normalizeAiMode(
                StringUtils.hasText(aiModeRaw) ? aiModeRaw : str(session.get("aiMode")));

        chatService.createMessage(Map.of(
                "session_id", sessionId,
                "role", "user",
                "content", text,
                "content_type", "text"
        ));

        return outputStream -> {
            try {
                ReplyPrep prep = prepareContext(text, aiMode);
                if (!prep.ragSources().isEmpty()) {
                    writeSse(outputStream, Map.of("type", "rag_sources", "sources", prep.ragSources()));
                }
                if (prep.mindMap() != null && !prep.mindMap().isEmpty()) {
                    writeSse(outputStream, Map.of("type", "mind_map", "mindMap", prep.mindMap()));
                }
                if (!prep.evidence().isEmpty()) {
                    writeSse(outputStream, Map.of("type", "rag_evidence", "evidence", prep.evidence()));
                }
                if (!prep.contextSources().isEmpty()) {
                    writeSse(outputStream, Map.of("type", "context_sources", "sources", prep.contextSources()));
                }

                StringBuilder full = new StringBuilder();
                boolean error = false;
                String errorMessage = null;
                String modelName = prep.modelName();

                try {
                    if (prep.canStream()) {
                        String answer = llmChatClient.chatStream(
                                prep.systemPrompt(),
                                prep.userPrompt(),
                                prep.modelName(),
                                prep.apiKey(),
                                prep.baseUrl(),
                                chunk -> {
                                    try {
                                        full.append(chunk);
                                        writeSse(outputStream, Map.of("type", "chunk", "content", chunk));
                                    } catch (Exception ex) {
                                        throw new RuntimeException(ex);
                                    }
                                });
                        if (!StringUtils.hasText(full.toString()) && StringUtils.hasText(answer)) {
                            full.append(answer);
                        }
                    } else if (prep.useModelService()) {
                        Map<String, Object> result = "ollama".equals(prep.modelType())
                                ? modelService.callOllamaModel(prep.modelConfig(), prep.userPrompt(), prep.systemPrompt())
                                : modelService.callApiModel(prep.modelConfig(), prep.userPrompt(), prep.systemPrompt());
                        String reply = Boolean.TRUE.equals(result.get("success"))
                                ? str(result.get("response"))
                                : "";
                        if (!StringUtils.hasText(reply)) {
                            throw new IllegalStateException(str(result.get("error")));
                        }
                        emitChunked(outputStream, reply, full);
                    } else if (prep.canSyncLlm()) {
                        String reply = llmChatClient.chat(
                                prep.systemPrompt(), prep.userPrompt(), prep.modelName(), prep.apiKey(), prep.baseUrl());
                        emitChunked(outputStream, reply, full);
                    } else {
                        String fallback = buildFallbackAnswer(text, prep);
                        emitChunked(outputStream, fallback, full);
                    }
                } catch (Exception ex) {
                    error = true;
                    errorMessage = ex.getMessage();
                    log.warn("流式对话失败: {}", ex.getMessage());
                    writeSse(outputStream, Map.of("type", "error", "message", errorMessage == null ? "对话失败" : errorMessage));
                    if (full.isEmpty()) {
                        String fallback = "抱歉，发生了错误：" + (errorMessage == null ? "对话失败" : errorMessage);
                        emitChunked(outputStream, fallback, full);
                    }
                }

                String answer = full.toString();
                Map<String, Object> knowledgePanel = buildKnowledgePanel(
                        text, answer, prep.references(), prep.evidence(), prep.contextSources(), prep.mindMap());
                writeSse(outputStream, Map.of("type", "knowledge_panel", "knowledgePanel", knowledgePanel));

                Map<String, Object> assistantData = new LinkedHashMap<>();
                assistantData.put("session_id", sessionId);
                assistantData.put("role", "ai");
                assistantData.put("content", answer);
                assistantData.put("content_type", "text");
                assistantData.put("ai_model", modelName);
                assistantData.put("ai_mode", aiMode);
                assistantData.put("is_error", error);
                if (error) {
                    assistantData.put("error_message", errorMessage);
                }
                assistantData.put("references_json", prep.references());
                assistantData.put("retrieval_sources", prep.ragSources());
                assistantData.put("rag_context", Map.of(
                        "evidence", knowledgePanel.getOrDefault("evidence", List.of()),
                        "mindMap", knowledgePanel.get("mindMap"),
                        "contextSources", knowledgePanel.getOrDefault("contextSources", List.of()),
                        "knowledgePanel", knowledgePanel
                ));
                chatService.createMessage(assistantData);
                chatService.bumpSessionAfterReply(sessionId, 2);

                writeSse(outputStream, Map.of("type", "done"));
                outputStream.flush();
            } catch (Exception ex) {
                log.warn("SSE 输出失败: {}", ex.getMessage());
                try {
                    writeSse(outputStream, Map.of("type", "error", "message", ex.getMessage()));
                    writeSse(outputStream, Map.of("type", "done"));
                } catch (Exception ignored) {
                    // ignore
                }
            }
        };
    }

    private ReplyBundle generateReply(String question, String aiMode) {
        ReplyPrep prep = prepareContext(question, aiMode);
        try {
            String answer;
            if (prep.useModelService()) {
                Map<String, Object> result = "ollama".equals(prep.modelType())
                        ? modelService.callOllamaModel(prep.modelConfig(), prep.userPrompt(), prep.systemPrompt())
                        : modelService.callApiModel(prep.modelConfig(), prep.userPrompt(), prep.systemPrompt());
                if (Boolean.TRUE.equals(result.get("success"))) {
                    answer = str(result.get("response"));
                } else if (prep.canSyncLlm()) {
                    answer = llmChatClient.chat(
                            prep.systemPrompt(), prep.userPrompt(), prep.modelName(), prep.apiKey(), prep.baseUrl());
                } else {
                    answer = buildFallbackAnswer(question, prep);
                    Map<String, Object> panel = buildKnowledgePanel(
                            question, answer, prep.references(), prep.evidence(), prep.contextSources(), prep.mindMap());
                    return new ReplyBundle(answer, prep.modelName(), prep.references(), prep.ragSources(), panel,
                            true, str(result.get("error")));
                }
            } else if (prep.canSyncLlm()) {
                answer = llmChatClient.chat(
                        prep.systemPrompt(), prep.userPrompt(), prep.modelName(), prep.apiKey(), prep.baseUrl());
            } else {
                answer = buildFallbackAnswer(question, prep);
            }
            Map<String, Object> panel = buildKnowledgePanel(
                    question, answer, prep.references(), prep.evidence(), prep.contextSources(), prep.mindMap());
            return new ReplyBundle(answer, prep.modelName(), prep.references(), prep.ragSources(), panel, false, null);
        } catch (Exception ex) {
            log.warn("生成回复失败: {}", ex.getMessage());
            String answer = "抱歉，发生了错误：" + ex.getMessage();
            Map<String, Object> panel = buildKnowledgePanel(
                    question, answer, prep.references(), prep.evidence(), prep.contextSources(), prep.mindMap());
            return new ReplyBundle(answer, prep.modelName(), prep.references(), prep.ragSources(), panel, true, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ReplyPrep prepareContext(String question, String aiMode) {
        String systemPrompt = SYSTEM_PROMPTS.getOrDefault(aiMode, SYSTEM_PROMPTS.get("deep_think"));

        SuiAgentRagService.RagBundle rag = suiAgentRagService.retrieveForChat(question, aiMode);
        List<Map<String, Object>> ragSources = rag.ragSources();
        List<Map<String, Object>> references = rag.references();
        List<Map<String, Object>> evidence = rag.evidence();
        List<String> contextSources = rag.contextSources();
        Map<String, Object> mindMap = rag.mindMap();

        String userPrompt = "用户问题：\n" + question + "\n\n";
        if (StringUtils.hasText(rag.contextBlock())) {
            userPrompt += "相关知识库证据片段（请优先依据这些原文作答，勿编造未出现的事实）：\n"
                    + rag.contextBlock() + "\n";
        } else {
            userPrompt += "（未检索到直接证据片段，请谨慎作答并说明依据有限。）\n\n";
        }
        userPrompt += "请用中文回答，结构清晰，必要时分点说明。";

        Map<String, Object> modelConfig = findDefaultModel(false);
        String modelType = "api";
        if (modelConfig == null) {
            modelConfig = findDefaultModel(true);
            modelType = "ollama";
        }

        String modelName = llmChatClient.resolveModelName(null);
        String apiKey = llmProperties.getApiKey();
        String baseUrl = llmProperties.getBaseUrl();
        boolean useModelService = false;

        if (modelConfig != null) {
            useModelService = true;
            modelName = firstNonBlank(
                    str(modelConfig.get("model_code")),
                    str(modelConfig.get("ollama_model_name")),
                    str(modelConfig.get("model_name")),
                    modelName);
            if ("api".equals(modelType)) {
                apiKey = firstNonBlank(str(modelConfig.get("api_key")), apiKey);
                baseUrl = firstNonBlank(str(modelConfig.get("api_base_url")), baseUrl);
            } else {
                baseUrl = firstNonBlank(str(modelConfig.get("ollama_base_url")), "http://127.0.0.1:11434");
            }
        }

        boolean hasApiKey = StringUtils.hasText(apiKey);
        boolean canStream = useModelService && "api".equals(modelType) && hasApiKey;
        boolean canSyncLlm = hasApiKey || StringUtils.hasText(llmProperties.getApiKey());

        return new ReplyPrep(
                systemPrompt,
                userPrompt,
                modelName,
                apiKey,
                baseUrl,
                modelType,
                modelConfig,
                useModelService,
                canStream,
                canSyncLlm || (useModelService && "ollama".equals(modelType)),
                ragSources,
                references,
                evidence,
                contextSources,
                mindMap
        );
    }

    private Map<String, Object> findDefaultModel(boolean ollama) {
        try {
            List<Map<String, Object>> rows = ollama
                    ? jdbcTemplate.queryForList(
                    "SELECT * FROM models WHERE model_type = 'ollama' AND status = 'active' LIMIT 1")
                    : jdbcTemplate.queryForList(
                    """
                            SELECT * FROM models
                            WHERE model_type = 'api' AND status = 'active'
                              AND (provider = 'dashscope' OR model_code LIKE '%qwen%' OR provider = 'openai')
                            ORDER BY id ASC LIMIT 1
                            """);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            log.warn("查找聊天默认模型失败: {}", ex.getMessage());
            return null;
        }
    }

    private String buildFallbackAnswer(String question, ReplyPrep prep) {
        StringBuilder sb = new StringBuilder();
        sb.append("（当前未配置可用 LLM，返回基于知识库检索的简要说明）\n\n");
        sb.append("关于「").append(truncate(question, 40)).append("」：\n");
        if (prep.evidence().isEmpty()) {
            sb.append("暂未检索到直接相关的知识库文档。建议补充关键词后重试，或配置 DashScope/Ollama 后再对话。");
        } else {
            sb.append("检索到以下相关材料，可供参考：\n");
            for (Map<String, Object> ev : prep.evidence()) {
                sb.append("- ").append(str(ev.get("title"))).append("：")
                        .append(str(ev.get("snippet"))).append("\n");
            }
            sb.append("\n如出现紧急自伤风险，请立即联系当地危机干预热线或急救服务。");
        }
        return sb.toString();
    }

    private Map<String, Object> buildKnowledgePanel(
            String question,
            String answer,
            List<Map<String, Object>> references,
            List<Map<String, Object>> evidence,
            List<String> contextSources,
            Map<String, Object> mindMapOverride) {
        List<Map<String, Object>> normalizedEvidence = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            Map<String, Object> item = evidence.get(i);
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("id", firstNonBlank(str(item.get("id")), "evidence_" + String.format("%03d", i + 1)));
            ev.put("title", firstNonBlank(str(item.get("title")), "证据 " + (i + 1)));
            ev.put("sourceType", firstNonBlank(str(item.get("sourceType")), "doc"));
            ev.put("snippet", firstNonBlank(str(item.get("snippet")), "暂无证据片段"));
            ev.put("claim", firstNonBlank(str(item.get("claim")), "用于支撑当前回答中的对应判断。"));
            ev.put("docId", item.get("docId"));
            normalizedEvidence.add(ev);
        }

        List<Map<String, Object>> normalizedRefs = new ArrayList<>();
        for (int i = 0; i < references.size(); i++) {
            Map<String, Object> item = references.get(i);
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("id", firstNonBlank(str(item.get("id")), "ref_" + (i + 1)));
            ref.put("title", firstNonBlank(str(item.get("title")), "来源 " + (i + 1)));
            ref.put("type", firstNonBlank(str(item.get("type")), "md"));
            ref.put("topic", str(item.get("topic")));
            ref.put("subTopic", str(item.get("subTopic")));
            Object score = item.get("relevanceScore");
            ref.put("relevanceScore", score != null ? score : (i == 0 ? 1.0 : Math.max(0.5, 1.0 - i * 0.1)));
            normalizedRefs.add(ref);
        }

        List<String> contexts = contextSources == null ? List.of() : contextSources.stream()
                .filter(StringUtils::hasText).map(String::trim).toList();

        String questionLabel = question.length() > 16 ? question.substring(0, 16) + "..." : question;
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> qNode = new LinkedHashMap<>();
        qNode.put("id", "question");
        qNode.put("label", StringUtils.hasText(questionLabel) ? questionLabel : "当前问题");
        qNode.put("group", "question");
        qNode.put("description", "当前问答中心问题，图谱围绕它展开风险判断、事实依据与行动建议。");
        qNode.put("relatedEvidenceIds", normalizedEvidence.stream().limit(3).map(e -> str(e.get("id"))).toList());
        nodes.add(qNode);
        for (int i = 0; i < Math.min(contexts.size(), 3); i++) {
            String topic = contexts.get(i);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "context_" + i);
            node.put("label", topic.length() > 20 ? topic.substring(0, 20) + "..." : topic);
            node.put("group", i < 2 ? "core" : "support");
            node.put("description", "围绕“" + topic + "”补充当前问题的判断依据或处置线索。");
            node.put("relatedEvidenceIds",
                    i < normalizedEvidence.size() ? List.of(str(normalizedEvidence.get(i).get("id"))) : List.of());
            nodes.add(node);
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) {
            edges.add(Map.of("source", "question", "target", nodes.get(i).get("id"), "label", "关联线索"));
        }
        Map<String, Object> mindMap;
        if (mindMapOverride != null && mindMapOverride.get("nodes") instanceof List<?> list && !list.isEmpty()) {
            mindMap = mindMapOverride;
        } else {
            mindMap = new LinkedHashMap<>();
            mindMap.put("nodes", nodes);
            mindMap.put("edges", edges);
            mindMap.put("summary", "图谱展示当前问题与检索证据之间的核心关系，可用于解释回答依据与后续处置方向。");
            mindMap.put("focusNodeId", "question");
        }

        String answerExcerpt = "";
        if (StringUtils.hasText(answer)) {
            String[] parts = answer.split("\\n+");
            for (String part : parts) {
                if (StringUtils.hasText(part.trim())) {
                    answerExcerpt = part.trim();
                    break;
                }
            }
        }
        String firstRef = normalizedRefs.isEmpty() ? "当前问答" : str(normalizedRefs.get(0).get("title"));

        List<Map<String, Object>> tableRows = List.of(
                Map.of(
                        "topic", "问题焦点",
                        "knowledge", truncate(question, 24),
                        "description", StringUtils.hasText(answerExcerpt) ? answerExcerpt : "本轮回答未形成可展示摘要。"
                ),
                Map.of(
                        "topic", "证据来源",
                        "knowledge", normalizedRefs.stream().limit(2).map(r -> str(r.get("title")))
                                .filter(StringUtils::hasText).reduce((a, b) -> a + "、" + b).orElse("暂无独立来源"),
                        "description", "当前回答引用的主要来源为 " + firstRef + "，用于支撑本轮判断与建议。"
                ),
                Map.of(
                        "topic", "处置线索",
                        "knowledge", contexts.stream().limit(3).reduce((a, b) -> a + "、" + b).orElse("暂无额外线索"),
                        "description", "这些线索用于补充本轮风险判断、陪伴支持或升级处置方向。"
                )
        );

        List<String> followUps = List.of(
                "围绕“" + (contexts.isEmpty() ? "即时危险信号核验" : contexts.get(0)) + "”，当前最需要补问的一个细节是什么？",
                "如果今晚只能做一件现实干预，最优先应该安排什么？",
                "哪些迹象一旦出现，就不适合继续停留在普通安抚层面？"
        );

        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("mindMap", mindMap);
        panel.put("tableRows", tableRows);
        panel.put("followUpQuestions", followUps);
        panel.put("evidence", normalizedEvidence);
        panel.put("contextSources", contexts);
        return panel;
    }

    private void emitChunked(OutputStream out, String text, StringBuilder full) throws Exception {
        if (text == null) {
            return;
        }
        full.append(text);
        int size = 24;
        for (int i = 0; i < text.length(); i += size) {
            String chunk = text.substring(i, Math.min(i + size, text.length()));
            writeSse(out, Map.of("type", "chunk", "content", chunk));
        }
    }

    private void writeSse(OutputStream out, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private record ReplyBundle(
            String answer,
            String modelName,
            List<Map<String, Object>> references,
            List<Map<String, Object>> ragSources,
            Map<String, Object> knowledgePanel,
            boolean error,
            String errorMessage
    ) {
    }

    private record ReplyPrep(
            String systemPrompt,
            String userPrompt,
            String modelName,
            String apiKey,
            String baseUrl,
            String modelType,
            Map<String, Object> modelConfig,
            boolean useModelService,
            boolean canStream,
            boolean canSyncLlm,
            List<Map<String, Object>> ragSources,
            List<Map<String, Object>> references,
            List<Map<String, Object>> evidence,
            List<String> contextSources,
            Map<String, Object> mindMap
    ) {
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
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
