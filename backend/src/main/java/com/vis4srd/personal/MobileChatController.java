package com.vis4srd.personal;

import com.vis4srd.common.llm.LlmChatClient;
import com.vis4srd.common.llm.SceneModels;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MobileChatController {
    private final LlmChatClient model;
    private final SceneModels scenes;
    public record Question(@NotBlank @Size(max=9000) String conversation) {}

    @PostMapping("/api/me/chat/general")
    public ApiResponse<String> answer(@Valid @RequestBody Question question) {
        SecurityUtils.requireCurrentUser();
        return ApiResponse.ok(model.sceneChat("你是手机端通用问答助手。根据本次对话简短回答，一次最多问一个问题。不诊断。明确有即时危险时建议联系当地紧急服务或身边可信任的人。",
                question.conversation(), scenes.getMobileModel(), scenes.getMobileTemperature(), scenes.getMobileMaxTokens()));
    }
}