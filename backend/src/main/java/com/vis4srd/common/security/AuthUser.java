package com.vis4srd.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {
    private Long userId;
    private String username;
    private String role;
    private String jti;
    private Long exp;
}
