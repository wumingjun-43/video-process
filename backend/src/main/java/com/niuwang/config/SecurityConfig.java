package com.niuwang.config;

import com.niuwang.common.response.Result;
import com.niuwang.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 * JWT 无状态认证，RBAC 权限控制
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 密码编码器: BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 自定义 AccessDeniedHandler: 返回 JSON 格式的 403 响应
     * 防止 Security 异常在响应已提交后无法处理
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setStatus(403);
            try {
                response.getWriter().write(
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                Result.error(403, "权限不足")));
            } catch (IOException e) {
                log.error("写入错误响应失败", e);
            }
        };
    }

    /**
     * 自定义 AuthenticationEntryPoint: 返回 JSON 格式的 401 响应
     * 防止未认证请求在响应已提交后无法处理
     */
    @Bean
    public org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setStatus(401);
            try {
                response.getWriter().write(
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                Result.error(401, "未认证，请先登录")));
            } catch (IOException e) {
                log.error("写入错误响应失败", e);
            }
        };
    }

    /**
     * 安全过滤链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置自定义异常处理器
            .exceptionHandling(ex -> ex
                    .accessDeniedHandler(accessDeniedHandler())
                    .authenticationEntryPoint(authenticationEntryPoint()))
            .authorizeHttpRequests(auth -> auth
                // ASYNC 分发阶段放行（SSE 流式响应的异步回调）
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                // 不需要认证的公开接口
                .requestMatchers("/auth/login", "/auth/logout", "/auth/face-login", "/upload/**", "/doc.html", "/webjars/**", "/swagger-resources/**", "/v3/api-docs/**", "/druid/**").permitAll()
                // 人脸匹配和注册需要认证（防止未授权访问）
                .requestMatchers("/face/match").authenticated()
                .requestMatchers("/face/register").authenticated()
                .requestMatchers("/face/users").authenticated()
                // 用户管理需要认证
                .requestMatchers("/user").authenticated()
                .requestMatchers("/user/**").authenticated()
                // 知识图谱需要认证
                .requestMatchers("/knowledge").authenticated()
                .requestMatchers("/knowledge/**").authenticated()
                // 智能对话需要认证
                .requestMatchers("/chat").authenticated()
                .requestMatchers("/chat/**").authenticated()
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*")); // TODO: 生产环境应替换为具体的前端域名白名单
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
