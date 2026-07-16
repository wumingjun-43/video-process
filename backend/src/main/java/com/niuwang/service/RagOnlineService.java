package com.niuwang.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * RAG 在线检索服务
 * 在线阶段：Query 处理 → 向量检索(粗排) → Rerank(精排) → 生成答案
 */
public interface RagOnlineService {

    /**
     * 完整的 RAG 在线检索流程
     *
     * @param question         用户问题
     * @param knowledgeFileIds 限定知识文件范围
     * @return 检索到的相关文档片段（精排后）
     */
    List<Document> retrieve(String question, List<Long> knowledgeFileIds);
}
