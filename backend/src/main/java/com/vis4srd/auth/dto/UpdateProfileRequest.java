package com.vis4srd.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 100, message = "昵称最多 100 字符")
    private String nickname;
}
