package com.vis4srd.personal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceAccessTest {
    @Autowired MockMvc mvc;

    private void assertResearchDenied() throws Exception {
        for (String url : new String[]{"/api/research/models", "/api/users", "/api/datasets", "/api/risk/tasks", "/api/home/stats", "/api/models", "/api/upload/archive/datasets"}) {
            mvc.perform(get(url)).andExpect(status().isForbidden());
        }
    }

    @Test @WithMockUser
    void ordinaryUserCannotBrowseResearch() throws Exception { assertResearchDenied(); }

    @Test @WithMockUser(roles = "DOCTOR")
    void doctorInheritsResearchAccessButCannotReadModelSecrets() throws Exception {
        for (String url : new String[]{"/api/users", "/api/datasets", "/api/risk/task-types"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        assertPublicModelMetadata();
    }

    @Test @WithMockUser(roles = "RESEARCHER")
    void researcherCanReadArchivesButNotModelSecrets() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isOk());
        assertPublicModelMetadata();
    }

    private void assertPublicModelMetadata() throws Exception {
        mvc.perform(get("/api/models/api/configured")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[*].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data[*].api_key").doesNotExist());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void archiveFilesCannotBypassControllerPermissions() throws Exception {
        mvc.perform(get("/uploads/archives/fixture.json")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotDownloadUploads() throws Exception {
        mvc.perform(get("/uploads/knowledge/fixture.pdf")).andExpect(status().isUnauthorized());
    }
}
