package com.vis4srd.chat.controller;

import com.vis4srd.chat.service.ChatReplyService;
import com.vis4srd.chat.service.ChatService;
import com.vis4srd.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 纯 Java 智能问答 API，对齐 Python {@code backend/src/routes/chat.py} 与前端约定。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatReplyService chatReplyService;

    @GetMapping("/sessions")
    public ApiResponse<Map<String, Object>> listSessions(
            @RequestParam(required = false) String userHash,
            @RequestParam(name = "user_hash", required = false) String userHashSnake,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "include_archived", defaultValue = "false") boolean includeArchived) {
        String hash = StringUtils.hasText(userHash) ? userHash : userHashSnake;
        return ApiResponse.ok(chatService.listSessions(hash, page, pageSize, includeArchived));
    }

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(chatService.createSession(body == null ? Map.of() : body));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> getSession(
            @PathVariable long sessionId,
            @RequestParam(name = "with_messages", defaultValue = "false") boolean withMessages) {
        if (withMessages) {
            return ApiResponse.ok(chatService.getSessionWithMessages(sessionId));
        }
        return ApiResponse.ok(chatService.getSession(sessionId));
    }

    @PutMapping("/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> updateSession(
            @PathVariable long sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(chatService.updateSession(sessionId, body == null ? Map.of() : body));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable long sessionId) {
        chatService.deleteSession(sessionId);
        return ApiResponse.ok(null, "会话已删除");
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<Map<String, Object>>> listSessionMessages(
            @PathVariable long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
        Map<String, Object> data = chatService.listMessages(sessionId, page, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = data.get("messages") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        return ApiResponse.ok(messages);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(
            @PathVariable long sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        String content = str(payload.get("content"));
        String aiMode = str(first(payload, "aiMode", "ai_mode"));
        return ApiResponse.ok(chatReplyService.sendMessage(sessionId, content, aiMode));
    }

    @PostMapping(
            value = "/sessions/{sessionId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamMessage(
            @PathVariable long sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        String content = str(payload.get("content"));
        String aiMode = str(first(payload, "aiMode", "ai_mode"));
        StreamingResponseBody stream = chatReplyService.streamMessage(sessionId, content, aiMode);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(stream);
    }

    /** 兼容旧路径：按 session_id 取消息列表 */
    @GetMapping("/messages/{sessionId}")
    public ApiResponse<List<Map<String, Object>>> listMessagesLegacy(
            @PathVariable long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
        return listSessionMessages(sessionId, page, pageSize);
    }

    /** 兼容旧路径：一次性发消息 */
    @PostMapping("/send")
    public ApiResponse<Map<String, Object>> sendLegacy(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(chatReplyService.sendLegacy(body == null ? Map.of() : body));
    }

    @GetMapping({"/recommended-questions", "/quick-questions"})
    public ApiResponse<List<Map<String, Object>>> recommendedGet(
            @RequestParam(name = "ai_mode", required = false) String aiMode,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(chatService.getRecommendedQuestions(aiMode, limit));
    }

    @PostMapping({"/recommended-questions", "/quick-questions"})
    public ApiResponse<List<Map<String, Object>>> recommendedPost(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(name = "ai_mode", required = false) String aiModeQuery) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        String mode = str(first(payload, "aiMode", "ai_mode"));
        if (!StringUtils.hasText(mode)) {
            mode = aiModeQuery;
        }
        int limit = 8;
        Object lim = first(payload, "limit");
        if (lim != null) {
            try {
                limit = Integer.parseInt(String.valueOf(lim));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return ApiResponse.ok(chatService.getRecommendedQuestions(mode, limit));
    }

    private static Object first(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key) && body.get(key) != null) {
                return body.get(key);
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
