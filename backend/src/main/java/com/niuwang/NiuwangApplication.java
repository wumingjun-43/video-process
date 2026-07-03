package com.niuwang;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 牛王认证系统 - 应用启动类
 */
@SpringBootApplication
@MapperScan("com.niuwang.mapper")
@EnableAsync
public class NiuwangApplication {
    public static void main(String[] args) {
        SpringApplication.run(NiuwangApplication.class, args);
    }
}
