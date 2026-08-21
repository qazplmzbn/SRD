package com.vis4srd.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 调用 inference-python 侧车（Emocc / FeaLearner 真实推理）。
 */
@Slf4j
@Component
public class PythonInferenceClient {

    private final PythonInferenceProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public PythonInferenceClient(
            PythonInferenceProperties properties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeoutMs())))
                .setReadTimeout(Duration.ofMillis(Math.max(5000, properties.getReadTimeoutMs())))
                .build();
    }

    public boolean isHealthy() {
        try {
            String url = trimSlash(properties.getBaseUrl()) + "/health";
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    public Optional<Map<String, Object>> inferFealearner(String userHash, String dataSource) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userHash", userHash);
        body.put("dataSource", dataSource);
        return postInfer("/infer/fealearner", body);
    }

    public Optional<Map<String, Object>> inferEmocc(String userHash, String dataSource, List<String> posts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userHash", userHash);
        body.put("dataSource", dataSource);
        if (posts != null && !posts.isEmpty()) {
            body.put("posts", posts);
        }
        return postInfer("/infer/emocc", body);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> postInfer(String path, Map<String, Object> body) {
        String base = properties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            return Optional.empty();
        }
        String url = trimSlash(base) + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> json = objectMapper.readValue(resp.getBody(), new TypeReference<>() {
            });
            if (!Boolean.TRUE.equals(json.get("success"))) {
                log.warn("推理侧车失败 {}: {}", path, json.get("error"));
                return Optional.empty();
            }
            Object data = json.get("data");
            if (data instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), v));
                return Optional.of(out);
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("推理侧车不可达 {}: {}", url, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("推理侧车解析失败 {}: {}", path, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
