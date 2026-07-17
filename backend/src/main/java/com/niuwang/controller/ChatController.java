package com.niuwang.controller;

import com.niuwang.common.response.Result;
import com.niuwang.model.dto.AskDTO;
import com.niuwang.model.vo.ChatHistoryVO;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.ChatAgentService;
import com.niuwang.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import reactor.core.Disposable;

/**
 * SSE 事件格式工具
 * 将 chunk 编码为 JSON 字符串，避免 \n \r \ 等字符破坏 SSE 格式
 */
final class SseEncoder {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private SseEncoder() {}

    /** 将文本编码为 SSE 消息: json: "encoded"\n\n */
    static String encode(String text) {
        try {
            String json = MAPPER.writeValueAsString(text);
            return "json: " + json;
        } catch (Exception e) {
            // 极端情况下降级：去掉换行
            return "data: " + text.replace("\r", "").replace("\n", " ");
        }
    }
}


/**
 * 智能对话控制器
 * 基于 Agent + RAG + 知识图谱架构
 *
 * API:
 *   POST /chat/ask          - 智能对话（同步模式）
 *   POST /chat/ask-stream   - 智能对话（流式 SSE 模式）
 *   GET  /chat/knowledge-list - 获取可检索的知识文件列表
 *   GET  /chat/history       - 获取对话历史
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatAgentService chatAgentService;
    private final ChatHistoryService chatHistoryService;

    private final Executor sseTaskExecutor;

    /** Agent 智能对话（同步） */
    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody AskDTO askDTO) {
        String question = askDTO.getQuestion();
        List<Long> knowledgeFileIds = askDTO.getKnowledgeFileIds();

        String answer = chatAgentService.chat(question, knowledgeFileIds);

        Map<String, Object> data = Map.of(
                "answer", answer,
                "hasContext", question != null && !question.isEmpty()
        );
        return Result.success(data);
    }

    /** Agent 智能对话（流式 SSE） */
    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter askStream(@RequestBody AskDTO askDTO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(300_000L);

        // 保存 Subscription 引用，用于客户端断开时取消后台执行
        final class SubscriptionHolder {
            Disposable disposable;
        }
        SubscriptionHolder holder = new SubscriptionHolder();

        // 客户端完成/断开/超时时统一处理：取消后台 Flux 订阅 + 日志
        emitter.onCompletion(() -> {
            log.info("SSE 流结束");
            if (holder.disposable != null && !holder.disposable.isDisposed()) {
                holder.disposable.dispose();
            }
        });
        emitter.onTimeout(() -> log.info("SSE 流超时"));
        emitter.onError((ex) -> log.error("SSE 流错误", ex));

        // 在线程池中执行流式输出，避免阻塞 Tomcat 线程
        sseTaskExecutor.execute(() -> {
            try {
                holder.disposable = chatAgentService.chatStream(askDTO.getQuestion(), askDTO.getKnowledgeFileIds())
                        .subscribe(
                                chunk -> {
                                    try {
                                        emitter.send(SseEncoder.encode(chunk) + "\n\n", MediaType.TEXT_EVENT_STREAM);
                                    } catch (Exception e) {
                                        log.error("发送 SSE 数据失败，取消订阅", e);
                                        if (holder.disposable != null) holder.disposable.dispose();
                                        emitter.complete();
                                    }
                                },
                                error -> {
                                    log.error("流式对话失败", error);
                                    if (holder.disposable != null) holder.disposable.dispose();
                                    try {
                                        emitter.send("data: [错误] " + error.getMessage() + "\n\n", MediaType.TEXT_EVENT_STREAM);
                                    } catch (Exception ex) {
                                        log.error("发送错误信息失败", ex);
                                    }
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    if (holder.disposable != null) holder.disposable.dispose();
                                    try {
                                        emitter.send(": connected\n\n", MediaType.TEXT_EVENT_STREAM);
                                        emitter.complete();
                                    } catch (Exception e) {
                                        log.error("发送完成信号失败", e);
                                    }
                                }
                        );
            } catch (Exception e) {
                log.error("启动 SSE 流失败", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** 获取可检索的知识文件列表 */
    @GetMapping("/knowledge-list")
    public Result<List<KnowledgeFileVO>> getKnowledgeList() {
        return Result.success(chatAgentService.listAvailableKnowledge());
    }

    /** 获取最近对话历史 */
    @GetMapping("/history")
    public Result<List<ChatHistoryVO>> getHistory(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(chatHistoryService.listRecent(limit));
    }
}
