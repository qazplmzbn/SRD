package com.vis4srd.auth.controller;

import com.vis4srd.auth.dto.ChangePasswordRequest;
import com.vis4srd.auth.dto.LoginRequest;
import com.vis4srd.auth.dto.RegisterRequest;
import com.vis4srd.auth.dto.TokenPayload;
import com.vis4srd.auth.dto.UpdateProfileRequest;
import com.vis4srd.auth.dto.UserView;
import com.vis4srd.auth.entity.AppUser;
import com.vis4srd.auth.service.AuthService;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.common.security.JwtTokenProvider;
import com.vis4srd.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ApiResponse<TokenPayload> register(@Valid @RequestBody RegisterRequest body) {
        AppUser user = authService.register(body.getUsername(), body.getPassword(), body.getNickname());
        return ApiResponse.ok(buildToken(user));
    }

    @PostMapping("/login")
    public ApiResponse<TokenPayload> login(@Valid @RequestBody LoginRequest body) {
        AppUser user = authService.authenticate(body.getUsername(), body.getPassword(), false);
        return ApiResponse.ok(buildToken(user));
    }

    @PostMapping("/web/register")
    public ApiResponse<TokenPayload> registerWeb(@Valid @RequestBody RegisterRequest body) {
        return ApiResponse.ok(buildToken(authService.registerWeb(body.getUsername(), body.getPassword(), body.getNickname())));
    }

    @PostMapping("/web/login")
    public ApiResponse<TokenPayload> loginWeb(@Valid @RequestBody LoginRequest body) {
        return ApiResponse.ok(buildToken(authService.authenticate(body.getUsername(), body.getPassword(), true)));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout() {
        AuthUser current = SecurityUtils.requireCurrentUser();
        authService.revokeToken(current.getJti(), current.getExp());
        return ApiResponse.ok(Map.of("message", "已安全退出"));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        AuthUser current = SecurityUtils.requireCurrentUser();
        return ApiResponse.ok(authService.getUserById(current.getUserId()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        AuthUser current = SecurityUtils.requireCurrentUser();
        String message = authService.changePassword(
                current.getUserId(), body.getOldPassword(), body.getNewPassword());
        return ApiResponse.ok(Map.of("message", message));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, String>> updateProfile(@Valid @RequestBody UpdateProfileRequest body) {
        AuthUser current = SecurityUtils.requireCurrentUser();
        if (body.getNickname() != null) {
            authService.updateNickname(current.getUserId(), body.getNickname());
        }
        return ApiResponse.ok(Map.of("message", "资料更新成功"));
    }

    private TokenPayload buildToken(AppUser user) {
        Map<String, Object> tokenData = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole());
        return TokenPayload.builder()
                .token((String) tokenData.get("token"))
                .expiresAt((String) tokenData.get("expires_at"))
                .jti((String) tokenData.get("jti"))
                .user(authService.toView(user, false))
                .build();
    }
}
