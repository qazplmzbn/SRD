package com.vis4srd.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为 3-50 字符")
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fff]+$", message = "用户名只能包含字母、数字、下划线和中文")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码至少 6 字符")
    private String password;

    @Size(max = 100, message = "昵称最多 100 字符")
    private String nickname;
}
