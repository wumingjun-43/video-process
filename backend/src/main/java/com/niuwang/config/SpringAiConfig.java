package com.niuwang.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Alibaba 配置类
 * 通过 ChatClient 调用 DashScope 多模态模型进行图片识别和知识图谱分析
 */
@Configuration
public class SpringAiConfig {

    /**
     * 创建 ChatClient Bean，用于图片识别和知识图谱分析
     */
    @Bean
    public ChatClient chatClient(@org.springframework.beans.factory.annotation.Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
