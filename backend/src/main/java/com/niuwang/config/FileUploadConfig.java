package com.niuwang.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 文件上传配置类
 * 将本地磁盘路径映射为 HTTP 可访问的 URL 路径
 */
@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.access-url}")
    private String accessUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /upload/** 请求映射到本地磁盘路径
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
