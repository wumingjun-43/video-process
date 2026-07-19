package com.niuwang.service.chunk;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档切割策略接口
 * 每种策略实现类负责将完整文本切分为多个 Document chunk
 */
public interface ChunkStrategy {

    /**
     * 将文本切分为多个 chunk
     *
     * @param text           完整文本内容
     * @param sourceId       来源文件 ID
     * @param maxTokens      每个 chunk 的最大 token 数
     * @param overlapTokens  相邻 chunk 重叠 token 数
     * @return 切分后的文档列表
     */
    List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens);

    /**
     * 策略名称标识
     */
    String strategyName();
}