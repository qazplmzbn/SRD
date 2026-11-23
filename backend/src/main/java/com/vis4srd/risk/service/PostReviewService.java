package com.vis4srd.risk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.vis4srd.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class PostReviewService {
    private final RiskService risk;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public PostReviewService(RiskService risk,ObjectMapper mapper,@Value("${app.python-inference.base-url:http://127.0.0.1:8001}") String base){
        this.risk=risk; this.mapper=mapper; endpoint=URI.create(base+"/infer/appointment-review");
    }

    public Map<String,Object> review(List<String> posts,String family,String name,boolean allowed) throws Exception {
        return review(posts,family,name,allowed,null);
    }
    public Map<String,Object> review(List<String> posts,String family,String name,boolean allowed,RiskService.ResearchCloud cloud) throws Exception {
        if (!allowed) throw new BusinessException(HttpStatus.FORBIDDEN,"未授权向大模型发送贴文");
        if (!Set.of("emocc","fealearner").contains(family) || posts.isEmpty() || posts.size()>32) throw new BusinessException("检测模型或贴文数无效");
        var payload=new LinkedHashMap<String,Object>();
        payload.put("model",family);payload.put("posts",posts);
        if (family.equals("emocc")) payload.put("emojiSequences",risk.appointmentEmojis(posts,true));
        var req=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(90)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
        var response=client.send(req,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"检测失败，请检查表情、编码器、词典和模型权重");
        var node=mapper.readTree(response.body());
        if (!Set.of("low","medium","high").contains(node.path("riskLevel").asText()) || !node.path("riskScore").isNumber() || !(node.path("riskScore").asDouble()>=0 && node.path("riskScore").asDouble()<=1) || !(family.equals("emocc")?"Emocc-Weibo":"FeaLearner-Weibo").equalsIgnoreCase(node.path("model").asText())) throw new BusinessException("检测模型未返回有效结果");
        var fields=mapper.convertValue(node,new TypeReference<LinkedHashMap<String,Object>>(){});
        fields.put("modelName",name);
        risk.enrichPostDetection(fields,posts,family,true,cloud);
        if (!"llm_analysis".equals(fields.get("fusionMethod"))) throw new BusinessException(HttpStatus.BAD_GATEWAY,"已完成检测，但报告生成失败，请检查报告模型配置");
        return fields;
    }
}