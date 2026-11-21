package com.vis4srd.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class UploadedModelControllerTest {
 @Test void validatesFeaturesAndConsentAndNeverFallsBack() throws Exception {
  var client=mock(PythonInferenceClient.class);
  var mvc=MockMvcBuilders.standaloneSetup(new UploadedModelController(client)).setControllerAdvice(new GlobalExceptionHandler()).build();
  var body=new HashMap<String,Object>();body.put("userHash","uploaded-user");body.put("dataSource","weibo");body.put("posts",List.of("text"));body.put("emojiSequences",List.of(List.of()));body.put("bertEmbeddings",List.of(Collections.nCopies(768,0.2)));body.put("consent",false);
  var json=new ObjectMapper();
  mvc.perform(post("/api/model-analysis/emocc").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  body.put("consent",true);body.put("bertEmbeddings",List.of(List.of(1.0)));
  mvc.perform(post("/api/model-analysis/emocc").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  verifyNoInteractions(client);
  body.put("bertEmbeddings",List.of(Collections.nCopies(768,0.2)));body.put("posts",List.of());
  mvc.perform(post("/api/model-analysis/emocc").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  verifyNoInteractions(client);body.put("posts",List.of("text"));
  when(client.inferUploadedEmocc(anyMap())).thenReturn(Optional.empty());
  mvc.perform(post("/api/model-analysis/emocc").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isServiceUnavailable());
  when(client.inferUploadedEmocc(anyMap())).thenReturn(Optional.of(Map.of("modelType","emocc_local","postCount",1)));
  mvc.perform(post("/api/model-analysis/emocc").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.data.modelType").value("emocc_local"));
 }
}
