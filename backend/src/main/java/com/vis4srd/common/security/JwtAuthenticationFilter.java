package com.vis4srd.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vis4srd.auth.entity.TokenBlacklist;
import com.vis4srd.auth.mapper.TokenBlacklistMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistMapper tokenBlacklistMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            AuthUser user = jwtTokenProvider.parseToken(token);
            if (user.getJti() != null && isRevoked(user.getJti())) {
                writeUnauthorized(response, "Token 已失效，请重新登录");
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + (user.getRole() == null ? "user" : user.getRole()).toUpperCase())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            String message = jwtTokenProvider.isExpired(ex) ? "Token 已过期，请重新登录" : "无效的 Token";
            writeUnauthorized(response, message);
        }
    }

    private boolean isRevoked(String jti) {
        Long count = tokenBlacklistMapper.selectCount(
                Wrappers.<TokenBlacklist>lambdaQuery().eq(TokenBlacklist::getTokenJti, jti));
        return count != null && count > 0;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"detail\":\"" + message + "\",\"message\":\"" + message + "\",\"success\":false}");
    }
}
