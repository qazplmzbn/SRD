package com.vis4srd.research;

import com.vis4srd.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/research/experiments")
@RequiredArgsConstructor
public class ResearchExperimentController {
    private final ResearchExperimentService service;
    public record Create(@NotBlank @Size(max=36) String versionId, @Positive long modelId,
                         @AssertTrue(message="请确认有权将测试集发送给所选模型服务") boolean consent, boolean checkSpeaker, boolean checkNegation) {
        public Create(String versionId,long modelId,boolean consent){this(versionId,modelId,consent,false,false);}
    }
    @ModelAttribute void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping public ApiResponse<?> list(@RequestParam(defaultValue="1") int page) { return ApiResponse.ok(service.list(page)); }
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody Create input) { return ApiResponse.ok(service.create(input)); }
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable String id) { return ApiResponse.ok(service.detail(id)); }
    @PostMapping("/{id}/cancel") public ApiResponse<?> cancel(@PathVariable String id) { service.cancel(id); return ApiResponse.ok(null); }
}
