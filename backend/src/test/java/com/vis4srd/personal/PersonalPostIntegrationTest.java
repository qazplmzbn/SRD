package com.vis4srd.personal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real authentication, filters and MySQL SQL; all fixture rows roll back after the test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional(transactionManager = "mysqlTransactionManager")
class PersonalPostIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String register() throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("username", "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                        "password", "IterationTest_42!", "nickname", "迭代测试临时用户"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(response).path("data").path("token").asText();
    }

    @Test
    void authenticatedImportRoundTripAndIsolation() throws Exception {
        String owner = register();
        String other = register();
        mvc.perform(get("/api/me/posts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/posts/import").header("Authorization", owner).contentType("application/json")
                .content("""
                        {"consent":true,"posts":[{"content":"测试贴文，测试结束回滚","source":"联调测试","publishedAt":"2024-01-02T03:04:05Z"}]}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imported").value(1));
        String response = mvc.perform(get("/api/me/posts").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].publishedAt").value("2024-01-02T03:04:05Z"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).path("data").path("items").get(0).path("id").asText();
        mvc.perform(get("/api/me/posts").header("Authorization", other)).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(delete("/api/me/posts/" + id).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/posts/" + id).header("Authorization", owner)).andExpect(status().isOk());
    }
}
