package com.niuwang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档切割策略配置
 *
 * 支持的策略类型：
 * - fixed_size: 固定大小切割（带重叠）
 * - semantic: 语义边界切割（按段落/句子/标题层级）
 * - special_content: 特殊内容专项处理（代码以函数为单位、表格整块保留）
 * - parent_child: 父子切割（检索用小chunk，返回用大chunk）
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.chunking")
public class RagChunkProperties {

    /** 切割策略类型: fixed_size | semantic | special_content | parent_child */
    private StrategyType strategy = StrategyType.SEMANTIC;

    /** 每个 chunk 最大 token 数（约等于中文字数） */
    private int maxTokens = 600;

    /** 相邻 chunk 重叠 token 数 */
    private int overlapTokens = 100;

    /** 固定大小策略：每次切割的字符数（当 maxTokens 不适用时作为兜底） */
    private int chunkSize = 500;

    // ==================== 父子切割策略专属配置 ====================

    /** 子 chunk 最大 token 数（用于向量检索，追求精准） */
    private int childMaxTokens = 200;

    /** 父 chunk 最大 token 数（检索后返回完整上下文） */
    private int parentMaxTokens = 1000;

    /** 父子 chunk 之间的重叠 token 数 */
    private int parentOverlapTokens = 50;

    /** 枚举：文档切割策略类型 */
    public enum StrategyType {
        /** 固定大小切割 */
        FIXED_SIZE,
        /** 语义边界切割 */
        SEMANTIC,
        /** 特殊内容专项处理 */
        SPECIAL_CONTENT,
        /** 父子切割 */
        PARENT_CHILD
    }
}