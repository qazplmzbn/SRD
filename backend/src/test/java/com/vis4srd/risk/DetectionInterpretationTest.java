package com.vis4srd.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.llm.LlmChatClient;
import com.vis4srd.model.service.ModelService;
import com.vis4srd.risk.service.RiskService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DetectionInterpretationTest {
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final ModelService models = mock(ModelService.class);
    final LlmChatClient cloud = mock(LlmChatClient.class);
    final com.vis4srd.chat.rag.SuiAgentRagService rag = mock(com.vis4srd.chat.rag.SuiAgentRagService.class);
    final com.vis4srd.common.llm.SceneModels scenes = new com.vis4srd.common.llm.SceneModels();
    final RiskService service = new RiskService(db, new ObjectMapper(), null, models, cloud, null, null, null, rag, scenes);
    { scenes.setReportModel("test-local"); scenes.setOllamaPort(11434); }
    final Map<String,Object> result = new LinkedHashMap<>(Map.of("riskLevel", "high", "riskScore", 0.91));

    @Test void feedsDetectorScoreAndPreservesItWhenLlmDisagrees() {
        Map<String,Object> local = Map.of("ollama_base_url", "http://localhost:11434", "ollama_model_name", "test-local");
        when(db.queryForList(anyString())).thenReturn(List.of(local));
        when(models.normalizeOllamaBaseUrl(anyString())).thenAnswer(i -> i.getArgument(0));
        when(rag.retrieveEvidence(anyString(),eq(2))).thenReturn(Map.of("hits",List.of(Map.of("file",Map.of("name","guide.md"),"fragment","example evidence"))));
        when(cloud.sceneChat(anyString(),argThat(q->q.contains("example evidence") && q.contains("0.91")),eq("test-local"),eq(11434),anyDouble(),anyInt())).thenReturn("{\"summary\":\"test summary\",\"risk_level\":\"low\",\"risk_score\":0.05}");
        service.enrichAppointmentDetection(result, List.of("sample"), "fealearner", false);
        assertEquals("test summary", result.get("summary"));
        assertEquals("retrieved", result.get("knowledgeStatus"));
        assertEquals("high", result.get("riskLevel"));
        assertEquals(0.91, result.get("riskScore"));
        assertFalse(result.containsKey("confidence"));
        assertEquals("test-local", result.get("llmModel"));
    }

    @Test void externalEndpointIsNotUsedForRestrictedAppointment() {
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of("ollama_base_url", "https://external.example")));
        when(models.normalizeOllamaBaseUrl(anyString())).thenAnswer(i -> i.getArgument(0));
        service.enrichAppointmentDetection(result, List.of("sample"), "emocc", false);
        assertEquals("detector_only", result.get("fusionMethod"));
        verify(models, never()).callOllamaModel(anyMap(), anyString(), anyString());
        verify(models, never()).callApiModel(anyMap(), anyString(), anyString());
        verify(cloud).sceneChat(anyString(),anyString(),eq("test-local"),eq(11434),anyDouble(),anyInt());
        verifyNoMoreInteractions(cloud);
    }
}