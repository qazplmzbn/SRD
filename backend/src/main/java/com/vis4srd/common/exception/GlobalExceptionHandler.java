package com.vis4srd.common.exception;

import com.vis4srd.common.result.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, Object>> handleUpload(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "请上传完整的申请信息及符合大小要求的证明材料");
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "请求格式错误，请检查内容及日期格式");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return error(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "参数校验失败" : message);
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage() == null ? "未认证" : ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "需要管理员权限");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissingResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "请求的资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        ex.printStackTrace();
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "系统处理请求时发生错误，请稍后重试。");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String detail) {
        Map<String, Object> body = new HashMap<>();
        // 兼容 FastAPI HTTPException 的 detail 字段，同时保留 message
        body.put("detail", detail);
        body.put("message", detail);
        body.put("success", false);
        return ResponseEntity.status(status).body(body);
    }
}
