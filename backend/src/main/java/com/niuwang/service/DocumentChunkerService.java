package com.niuwang.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档切分服务
 * 离线阶段：将完整文档按语义切分为多个 chunk
 *
 * 策略模式：通过 rag.chunking.strategy 配置项选择切割策略
 * - fixed_size: 固定大小切割（简单高效，带重叠）
 * - semantic: 语义边界切割（按段落/句子/标题层级）
 * - special_content: 特殊内容专项处理（代码以函数为单位、表格整块保留）
 * - parent_child: 父子切割（检索用小chunk精准定位，返回用大chunk完整上下文）
 */
public interface DocumentChunkerService {

    /**
     * 将完整文本切分为多个 chunk
     *
     * @param text           完整文本内容
     * @param sourceId       来源文件 ID（用于 metadata）
     * @param maxTokens      每个 chunk 的最大 token 数（约等于字数）
     * @param overlapTokens  相邻 chunk 的重叠 token 数
     * @return 切分后的文档列表
     */
    List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens);
}
