package com.niuwang.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 自定义 Bean 配置
 * ChatModel 由 Spring AI Alibaba 自动配置（DashScopeChatAutoConfiguration）提供
 */
@Configuration
public class ChatClientConfig {

    /**
     * 基于自动配置的 ChatModel 创建 ChatClient Bean
     */
    @Bean("configChatClient")
    public ChatClient chatClient(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean("sseTaskExecutor")
    public Executor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }
}