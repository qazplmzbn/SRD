package com.vis4srd.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TokenPayload {
    private String token;
    @JsonProperty("expires_at")
    private String expiresAt;
    private String jti;
    private UserView user;
    private Map<String, Object> extra;
}
