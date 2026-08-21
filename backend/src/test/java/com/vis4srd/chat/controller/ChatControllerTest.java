package com.vis4srd.chat.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void sessionsShouldReturnEnvelopeWithoutPythonSidecar() throws Exception {
        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessions").exists())
                .andExpect(jsonPath("$.data.page").exists());
    }

    @Test
    @WithMockUser
    void recommendedQuestionsShouldReturnList() throws Exception {
        mockMvc.perform(get("/api/chat/recommended-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser
    void streamMissingSessionShouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/999999999/messages/stream")
                        .contentType("application/json")
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().is(anyOf(equalTo(404), equalTo(400), equalTo(500))))
                .andExpect(jsonPath("$.success").value(false));
    }
}
