package com.vis4srd.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.python-inference")
public class PythonInferenceProperties {

    /** 推理侧车地址，默认 http://127.0.0.1:8001 */
    private String baseUrl = "http://127.0.0.1:8001";

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 180000;

    /** 侧车不可用时是否回退启发式 */
    private boolean fallbackHeuristic = true;
}
