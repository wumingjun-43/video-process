package com.niuwang.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API 文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("牛王认证系统 API")
                        .description("牛王认证系统 - 基于Spring Boot 3.x + spring-ai-alibaba")
                        .version("1.0.0"));
    }
}
