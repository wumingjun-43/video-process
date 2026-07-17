package com.niuwang.controller;

import com.niuwang.common.response.Result;
import com.niuwang.model.dto.AskDTO;
import com.niuwang.model.vo.ChatHistoryVO;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.ChatAgentService;
import com.niuwang.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;
import java.util.Map;

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

        // 在子线程中执行流式输出，避免阻塞 Tomcat 线程
        new Thread(() -> {
            try {
                chatAgentService.chatStream(askDTO.getQuestion(), askDTO.getKnowledgeFileIds())
                        .subscribe(
                                chunk -> {
                                    try {
                                        // 对 chunk 中的特殊字符做 SSE 转义
                                        String escaped = chunk
                                                .replace("\\", "\\\\")
                                                .replace("\n", "\\n")
                                                .replace("\r", "\\r")
                                                .replace("data: ", "[DATA] "); // 避免 chunk 本身以 data: 开头
                                        emitter.send("data: " + escaped + "\n\n", MediaType.TEXT_EVENT_STREAM);
                                    } catch (Exception e) {
                                        log.error("发送 SSE 数据失败", e);
                                    }
                                },
                                error -> {
                                    log.error("流式对话失败", error);
                                    try {
                                        emitter.send("data: [错误] " + error.getMessage() + "\n\n", MediaType.TEXT_EVENT_STREAM);
                                    } catch (Exception ex) {
                                        log.error("发送错误信息失败", ex);
                                    }
                                    emitter.completeWithError(error);
                                },
                                () -> {
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
        }).start();

        // 客户端断开连接时的回调
        emitter.onCompletion(() -> log.info("SSE 流完成"));
        emitter.onTimeout(() -> log.info("SSE 流超时"));
        emitter.onError((ex) -> log.error("SSE 流错误", ex));

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
