package com.niuwang.controller;

import com.niuwang.common.response.Result;
import com.niuwang.model.dto.AskDTO;
import com.niuwang.model.vo.ChatHistoryVO;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.ChatAgentService;
import com.niuwang.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    @PostMapping(value = "/ask-stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> askStream(@RequestBody AskDTO askDTO) {
        String question = askDTO.getQuestion();
        List<Long> knowledgeFileIds = askDTO.getKnowledgeFileIds();
        return chatAgentService.chatStream(question, knowledgeFileIds);
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

