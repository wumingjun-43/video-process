package com.niuwang.service;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能对话服务接口
 * 基于 Agent + RAG + 知识图谱架构
 */
public interface ChatAgentService {

    /**
     * 同步对话
     */
    String chat(String question, List<Long> knowledgeFileIds);

    /**
     * 流式对话
     */
    Flux<String> chatStream(String question, List<Long> knowledgeFileIds);

    /**
     * 获取可检索的知识文件列表
     */
    List<com.niuwang.model.vo.KnowledgeFileVO> listAvailableKnowledge();
}
