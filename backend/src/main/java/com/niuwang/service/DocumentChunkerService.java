package com.niuwang.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档切分服务
 * 离线阶段：将完整文档按语义切分为多个 chunk
 *
 * 策略：
 * - 按段落分割（双换行符分隔）
 * - 每个 chunk 不超过 maxTokens token（约 500~800 字）
 * - 相邻 chunk 之间重叠 overlapTokens token（约 100 字），避免语义断裂
 * - 每个 chunk 附带 source_id metadata 用于 RAG 检索过滤
 */
public interface DocumentChunkerService {

    /**
     * 将完整文本切分为多个语义 chunk
     *
     * @param text           完整文本内容
     * @param sourceId       来源文件 ID（用于 metadata）
     * @param maxTokens      每个 chunk 的最大 token 数（约等于字数）
     * @param overlapTokens  相邻 chunk 的重叠 token 数
     * @return 切分后的文档列表
     */
    List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens);
}
