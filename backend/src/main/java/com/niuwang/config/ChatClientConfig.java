package com.niuwang.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean(name = "aa")
    public ChatModel getChatMode(){
        // 初始化 ChatModel
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey("sk-ws-H.RPLHMHY.C2m3.MEUCICbhNvXPok3Mp-HXri7_FRXD8lmZyr5j-oEBuFPURsAcAiEAkH4Z8mmZpbov7ux_Kl0Y5tDY8MOtkDM5daS0e8rOiwY")
                .build();
        ChatModel chatModel = DashScopeChatModel.builder()
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(0.7)    // 控制随机性
                        .withMaxToken(2000)      // 最大输出长度
                        .withTopP(0.9)           // 核采样参数
                        .model("qwen-plus")
                        .build())

                .dashScopeApi(dashScopeApi).build();
        return chatModel;
    }
    
    @Bean("configChatClient")
    public ChatClient chatClient(@Qualifier("aa") ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
    

}