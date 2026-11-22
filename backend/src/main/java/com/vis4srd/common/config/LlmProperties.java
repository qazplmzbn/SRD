package com.vis4srd.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** 默认模型名，如 qwen-flash */
    private String model = "qwen-flash";

    /** OpenAI 兼容 API Key（可用 LLM_API_KEY / DASHSCOPE_API_KEY） */
    private String apiKey = "";

    /** OpenAI 兼容 Base URL（含 /v1 或不含均可） */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private double temperature = 0.7;
    private int maxTokens = 1000;

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 120000;
}
