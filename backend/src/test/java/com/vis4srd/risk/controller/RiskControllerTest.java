package com.vis4srd.risk.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void taskTypesShouldReturnStaticList() throws Exception {
        mockMvc.perform(get("/api/risk/task-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type_code").value("suicide"));
    }

    @Test
    @WithMockUser
    void modelsShouldReturnEmoccPlaceholder() throws Exception {
        mockMvc.perform(get("/api/risk/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Emocc"));
    }

    @Test
    @WithMockUser
    void tasksListShouldSucceedWithoutPythonSidecar() throws Exception {
        mockMvc.perform(get("/api/risk/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tasks").exists())
                .andExpect(jsonPath("$.data.stats").exists());
    }
}
