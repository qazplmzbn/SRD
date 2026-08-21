package com.vis4srd.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserView {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    @JsonProperty("avatar_color")
    private String avatarColor;
    private String role;
    @JsonProperty("is_active")
    private Boolean isActive;
    @JsonProperty("last_login_at")
    private String lastLoginAt;
    @JsonProperty("created_at")
    private String createdAt;
}
