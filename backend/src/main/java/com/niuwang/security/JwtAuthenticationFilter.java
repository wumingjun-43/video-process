package com.niuwang.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * 从请求头中提取 Token 并验证，设置认证上下文
 *
 * SSE 流式响应（Flux<String>）在 Tomcat 异步分发时会触发 ASYNC dispatcher type，
 * 此时 SecurityContext 中的 Authentication 可能不可见（ThreadLocal 隔离），
 * 因此 ASYNC 阶段跳过 JWT 校验，直接放行。
 * 同时在 SecurityConfig 中配置 dispatcherTypeMatchers(ASYNC).permitAll() 兜底。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // SSE 流式响应的 ASYNC 分发阶段：跳过 JWT 校验，直接放行
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(jwtUtil.getHeader());

        if (header != null && header.startsWith(jwtUtil.getPrefix())) {
            String token = header.substring(jwtUtil.getPrefix().length());

            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String loginName = jwtUtil.getLoginNameFromToken(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        );
                authentication.setDetails(loginName);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}