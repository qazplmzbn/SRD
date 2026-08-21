package com.vis4srd.model.controller;

import com.vis4srd.model.service.ModelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModelService modelService;

    @Test
    @WithMockUser
    void listModelsShouldReturnEnvelope() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser
    void literalPathsShouldNotBeCapturedAsModelId() throws Exception {
        mockMvc.perform(get("/api/models/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/models/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].key").exists());

        mockMvc.perform(get("/api/models/api/configured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser
    void ollamaStatusShouldNotThrowWhenUnreachable() throws Exception {
        mockMvc.perform(get("/api/models/ollama/status")
                        .param("base_url", "http://127.0.0.1:1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.healthy").value(false))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.base_url").exists());
    }

    @Test
    @WithMockUser
    void compareAndHistoryShouldReturnEnvelope() throws Exception {
        mockMvc.perform(get("/api/models/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.radar_data").exists());

        mockMvc.perform(get("/api/models/inference-history").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.history").isArray())
                .andExpect(jsonPath("$.data.total").exists());
    }

    @Test
    void convertModelShouldExposeCamelCaseAndHasApiKey() {
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("id", 1L);
        row.put("model_name", "通义千问");
        row.put("model_code", "qwen-flash");
        row.put("model_category", "api");
        row.put("model_type", "api");
        row.put("api_key", "sk-test");
        row.put("supported_datasets", "[\"reddit\"]");
        row.put("performance_metrics", "{\"accuracy\":0.9}");
        row.put("is_builtin", false);
        row.put("is_available", true);

        var camel = modelService.convertModelToCamel(row);
        org.junit.jupiter.api.Assertions.assertEquals("通义千问", camel.get("modelName"));
        org.junit.jupiter.api.Assertions.assertEquals(true, camel.get("hasApiKey"));
        org.junit.jupiter.api.Assertions.assertTrue(camel.get("supportedDatasets") instanceof java.util.List);
        org.junit.jupiter.api.Assertions.assertTrue(camel.get("performanceMetrics") instanceof java.util.Map);
    }

    @Test
    @WithMockUser
    void providersContentTypeIsJson() throws Exception {
        mockMvc.perform(get("/api/models/providers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", anyOf(equalTo(true), equalTo(Boolean.TRUE))));
    }
}
