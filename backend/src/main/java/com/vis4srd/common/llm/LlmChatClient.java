package com.vis4srd.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.config.LlmProperties;
import com.vis4srd.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 Chat Completions（同步 + SSE 流式）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmChatClient {

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null, null, null);
    }

    public String chat(
            String systemPrompt,
            String userPrompt,
            String model,
            String apiKey,
            String baseUrl) {
        Map<String, Object> payload = buildPayload(systemPrompt, userPrompt, model, false);
        Map<String, Object> body = postJson(payload, apiKey, baseUrl, false);
        return extractContent(body);
    }

    /**
     * 流式调用：对每个文本 delta 回调 onChunk；返回完整文本。
     */
    public String chatStream(
            String systemPrompt,
            String userPrompt,
            String model,
            String apiKey,
            String baseUrl,
            Consumer<String> onChunk) {
        Map<String, Object> payload = buildPayload(systemPrompt, userPrompt, model, true);
        String endpoint = chatCompletionsUrl(baseUrl);
        String key = resolveApiKey(apiKey);
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "未配置 LLM API Key（app.llm.api-key）");
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(llmProperties.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(llmProperties.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "LLM 流式调用失败 HTTP " + response.statusCode() + ": " + truncate(err, 300));
            }

            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    try {
                        JsonNode root = objectMapper.readTree(data);
                        String delta = extractDelta(root);
                        if (StringUtils.hasText(delta)) {
                            full.append(delta);
                            if (onChunk != null) {
                                onChunk.accept(delta);
                            }
                        }
                    } catch (Exception parseEx) {
                        log.debug("跳过无效 SSE 行: {}", parseEx.getMessage());
                    }
                }
            }
            return full.toString();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("LLM 流式调用异常: {}", ex.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "LLM 流式调用失败: " + ex.getMessage());
        }
    }

    public String resolveModelName(String override) {
        if (StringUtils.hasText(override)) {
            return override.trim();
        }
        return StringUtils.hasText(llmProperties.getModel()) ? llmProperties.getModel() : "qwen-flash";
    }

    private Map<String, Object> buildPayload(
            String systemPrompt, String userPrompt, String model, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", StringUtils.hasText(systemPrompt) ? systemPrompt : "你是一个有用的AI助手。"));
        messages.add(Map.of(
                "role", "user",
                "content", userPrompt == null ? "" : userPrompt));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveModelName(model));
        payload.put("temperature", llmProperties.getTemperature() > 0 ? llmProperties.getTemperature() : 0.7);
        payload.put("messages", messages);
        payload.put("stream", stream);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(
            Map<String, Object> payload, String apiKey, String baseUrl, boolean stream) {
        String endpoint = chatCompletionsUrl(baseUrl);
        String key = resolveApiKey(apiKey);
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "未配置 LLM API Key（app.llm.api-key）");
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(llmProperties.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(llmProperties.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "LLM 调用失败 HTTP " + response.statusCode() + ": " + truncate(response.body(), 300));
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "LLM 调用失败: " + ex.getMessage());
        }
    }

    private String chatCompletionsUrl(String baseUrlOverride) {
        String base = StringUtils.hasText(baseUrlOverride) ? baseUrlOverride.trim() : llmProperties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    private String resolveApiKey(String override) {
        if (StringUtils.hasText(override)) {
            return override.trim();
        }
        return llmProperties.getApiKey() == null ? "" : llmProperties.getApiKey().trim();
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        if (body == null) {
            return "";
        }
        Object choices = body.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object message = first.get("message");
            if (message instanceof Map<?, ?> msg) {
                Object content = msg.get("content");
                return content == null ? "" : String.valueOf(content).trim();
            }
            Object text = first.get("text");
            if (text != null) {
                return String.valueOf(text).trim();
            }
        }
        Object content = body.get("content");
        return content == null ? "" : String.valueOf(content).trim();
    }

    private String extractDelta(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode delta = choices.get(0).path("delta");
        if (delta.hasNonNull("content")) {
            return delta.get("content").asText("");
        }
        JsonNode message = choices.get(0).path("message");
        if (message.hasNonNull("content")) {
            return message.get("content").asText("");
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
