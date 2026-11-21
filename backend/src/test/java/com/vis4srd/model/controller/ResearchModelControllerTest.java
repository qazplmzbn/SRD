package com.vis4srd.model.controller;
import com.vis4srd.model.service.ModelService;
import com.vis4srd.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ResearchModelControllerTest {
    @Test void catalogNeverExposesConfiguration() throws Exception {
        var service=mock(ModelService.class);
        when(service.getModels(null,null,null)).thenReturn(List.of(Map.of("id",1,"modelName","test","modelType","api","status","active","apiKey","secret","apiBaseUrl","private")));
        var mvc=MockMvcBuilders.standaloneSetup(new ResearchModelController(service)).build();
        mvc.perform(get("/api/research/models")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].apiKey").doesNotExist()).andExpect(jsonPath("$.data[0].apiBaseUrl").doesNotExist()).andExpect(jsonPath("$.data[0].supportsTrial").value(true));
    }
    @Test void rejectsMissingConsentAndSanitizesUpstreamErrors() throws Exception {
        var service=mock(ModelService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ResearchModelController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/research/models/1/trial").contentType("application/json").content("{\"text\":\"test\",\"consent\":false}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        when(service.getModelForInference(1)).thenReturn(Map.of("model_type","api"));
        when(service.callApiModel(anyMap(),anyString(),anyString())).thenReturn(Map.of("success",false,"error","secret endpoint"));
        mvc.perform(post("/api/research/models/1/trial").contentType("application/json").content("{\"text\":\"test\",\"consent\":true}")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.message").value("模型调用失败，请联系管理员检查服务配置或稍后重试"));
    }
}
