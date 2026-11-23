package com.vis4srd.common.config;

import com.vis4srd.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"detail\":\"未提供认证 Token\",\"message\":\"未提供认证 Token\",\"success\":false}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/web/register", "/api/auth/web/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/scale/definitions", "/api/scale/definitions/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/upload/archive", "/api/upload/archive/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/datasets/upload", "/api/datasets/builtin/sync").hasRole("ADMIN")
                        .requestMatchers("/api/models/ollama/**").denyAll()
                        .requestMatchers(HttpMethod.POST, "/api/risk/tasks", "/api/risk/tasks/*/execute",
                                "/api/risk/emocc-tasks", "/api/risk/emocc-tasks/*/execute",
                                "/api/risk/fealearner-tasks", "/api/risk/fealearner-tasks/*/execute").hasAnyRole("ADMIN", "RESEARCHER")
                        .requestMatchers(HttpMethod.POST,"/api/risk/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,"/api/risk/**").hasRole("ADMIN")
                        .requestMatchers("/api/risk/predict/**").hasRole("ADMIN")
                        .requestMatchers("/api/research/**").hasAnyRole("ADMIN", "RESEARCHER", "DOCTOR")
                        .requestMatchers("/api/me/doctor-applications").hasAnyRole("RESEARCHER", "DOCTOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/models/api/configured").hasAnyRole("DOCTOR", "RESEARCHER", "ADMIN")
                        .requestMatchers("/api/models", "/api/models/{id:[0-9]+}", "/api/models/{id:[0-9]+}/api-key",
                                "/api/models/templates", "/api/models/templates/{id:[0-9]+}").hasAnyRole("DOCTOR", "RESEARCHER", "ADMIN")
                        .requestMatchers("/api/models", "/api/models/**").hasRole("ADMIN")
                        .requestMatchers("/api/home", "/api/home/**", "/api/users", "/api/users/**",
                                "/api/datasets", "/api/datasets/**", "/api/risk", "/api/risk/**",
                                "/api/upload/archive", "/api/upload/archive/**").hasAnyRole("ADMIN", "RESEARCHER", "DOCTOR")
                        .requestMatchers("/uploads/archives/**").denyAll()
                        .requestMatchers("/uploads/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = corsProperties.getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            origins = Arrays.asList(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:3000",
                    "http://127.0.0.1:3000");
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
