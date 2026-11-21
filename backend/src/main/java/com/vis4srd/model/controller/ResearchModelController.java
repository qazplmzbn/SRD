package com.vis4srd.model.controller;

import com.vis4srd.model.service.ModelService;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Research callers receive an explicit allowlist, never model configuration. */
@RestController
@RequestMapping("/api/research/models")
@RequiredArgsConstructor
public class ResearchModelController {
    private final ModelService models;
    public record Trial(@NotBlank @Size(max=8000) String text,
                        @AssertTrue(message="请确认有权使用该内容并同意发送给所选模型服务") boolean consent) {}

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(models.getModels(null,null,null).stream().map(row -> {
            Map<String,Object> safe = new LinkedHashMap<>();
            for (String key : List.of("id","modelName","modelType","status","description")) safe.put(key,row.get(key));
            safe.put("supportsTrial", List.of("api","ollama").contains(String.valueOf(row.get("modelType"))) && "active".equals(row.get("status")));
            return safe;
        }).toList());
    }

    @PostMapping("/{id}/trial")
    public ApiResponse<?> trial(@PathVariable long id, @Valid @RequestBody Trial input) {
        var config=models.getModelForInference(id);
        if(config==null) throw new BusinessException(HttpStatus.NOT_FOUND,"模型不存在或未启用");
        String type=String.valueOf(config.get("model_type"));
        if(!List.of("api","ollama").contains(type)) throw new BusinessException(HttpStatus.BAD_REQUEST,"此模型需要专用输入，请使用检测任务入口");
        long start=System.nanoTime();
        var result=type.equals("api") ? models.callApiModel(config,input.text(),"请根据输入提供分析，说明依据与不确定性，不将输出作为临床诊断。")
                : models.callOllamaModel(config,input.text(),"请根据输入提供分析，说明依据与不确定性，不将输出作为临床诊断。");
        if(!Boolean.TRUE.equals(result.get("success"))) throw new BusinessException(HttpStatus.BAD_GATEWAY,"模型调用失败，请联系管理员检查服务配置或稍后重试");
        long elapsed=(System.nanoTime()-start)/1_000_000;
        models.updateModelUsage(id,(int)Math.min(elapsed,Integer.MAX_VALUE));
        return ApiResponse.ok(Map.of("response",Objects.toString(result.get("response"),""),"elapsedMs",elapsed,"modelId",id));
    }
}
