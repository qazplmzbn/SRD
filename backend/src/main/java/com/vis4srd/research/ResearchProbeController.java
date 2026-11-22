package com.vis4srd.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/research/versions")
public class ResearchProbeController {
    private final ResearchVersionService versions;
    private final ObjectMapper mapper;
    @Value("${app.python-inference.base-url:http://127.0.0.1:8001}") private String base;
    public record Run(boolean consent) {}
    public record Prediction(int index,int expected,int predicted) {}

    @PostMapping("/{id}/bert-probe")
    public ApiResponse<?> run(@PathVariable String id,@RequestBody Run input,HttpServletResponse response) throws Exception {
        response.setHeader("Cache-Control","no-store");
        var version=versions.detail(id);
        if(!input.consent()) throw invalid("请确认允许本机编码");
        List<String> train=new ArrayList<>(),test=new ArrayList<>();
        List<Integer> labels=new ArrayList<>(),expected=new ArrayList<>(),indices=new ArrayList<>();
        int index=0;
        if(version.containsKey("table"))throw invalid("通用表格未配置二分类实验字段");
        for(JsonNode sample:(JsonNode)version.get("samples")) {
            String text=sample.path("text").asText(); int label=sample.path("label").asInt();
            if("TRAIN".equals(sample.path("split").asText())) {train.add(text);labels.add(label);}
            else {test.add(text);expected.add(label);indices.add(index);}
            index++;
        }
        if(!labels.containsAll(List.of(0,1))||test.isEmpty()) throw invalid("训练集需要同时包含标签 0 和 1，测试集至少需要一条贴文");
        var seen=new HashSet<String>();train.forEach(t->seen.add(t.strip()));
        if(test.stream().anyMatch(t->seen.contains(t.strip())))throw invalid("训练集和测试集含相同文本，请去重后创建新版本");
        var request=HttpRequest.newBuilder(URI.create(base+"/infer/research-probe")).timeout(Duration.ofSeconds(120)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("train",train,"labels",labels,"test",test)))).build();
        HttpResponse<String> upstream;
        try {upstream=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());}
        catch(java.io.IOException e){throw unavailable();}
        if(upstream.statusCode()!=200)throw unavailable();
        versions.detail(id);
        JsonNode result=mapper.readTree(upstream.body()), predictions=result.path("predictions");
        if(!predictions.isArray()||predictions.size()!=test.size())throw unavailable();
        int majority=Collections.frequency(labels,1)>labels.size()/2?1:0, correct=0, baseline=0;
        var rows=new ArrayList<Prediction>();
        for(int i=0;i<test.size();i++){
            JsonNode value=predictions.get(i);if(!value.isInt()||(value.intValue()!=0&&value.intValue()!=1))throw unavailable();
            int y=expected.get(i),p=value.intValue();rows.add(new Prediction(indices.get(i),y,p));
            if(y==p) correct++;if(y==majority)baseline++;
        }
        return ApiResponse.ok(Map.of("protocol","bert-mean-cosine-v1","checksum",version.get("checksum"),"encoder",result.path("encoder").asText(),"trainCount",train.size(),"rows",rows,"correct",correct,"baselineCorrect",baseline,"baselineLabel",majority));
    }
    private static BusinessException invalid(String message){return new BusinessException(HttpStatus.BAD_REQUEST,message);}
    private static BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"未获得有效预测，请检查本机 BERT 编码器和推理服务");}
}