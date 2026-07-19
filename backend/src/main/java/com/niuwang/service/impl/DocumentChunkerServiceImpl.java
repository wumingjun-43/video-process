package com.niuwang.service.impl;

import com.niuwang.config.RagChunkProperties;
import com.niuwang.service.DocumentChunkerService;
import com.niuwang.service.chunk.ChunkStrategy;
import com.niuwang.service.chunk.FixedSizeChunkStrategy;
import com.niuwang.service.chunk.ParentChildChunkStrategy;
import com.niuwang.service.chunk.SemanticChunkStrategy;
import com.niuwang.service.chunk.SpecialContentChunkStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文档切分服务实现（策略模式）
 *
 * 根据 RagChunkProperties.strategy 配置动态路由到具体切割策略：
 *
 * 配置示例（application.yml）:
 * <pre>
 * rag:
 *   chunking:
 *     strategy: semantic          # 可选: fixed_size | semantic | special_content | parent_child
 *     max-tokens: 600             # 每个 chunk 最大 token 数
 *     overlap-tokens: 100         # 相邻 chunk 重叠 token 数
 * </pre>
 */
@Slf4j
@Service
public class DocumentChunkerServiceImpl implements DocumentChunkerService {

    private final Map<String, ChunkStrategy> strategyMap;
    private final RagChunkProperties properties;

    public DocumentChunkerServiceImpl(FixedSizeChunkStrategy fixedSize,
                                      SemanticChunkStrategy semantic,
                                      SpecialContentChunkStrategy specialContent,
                                      ParentChildChunkStrategy parentChild,
                                      RagChunkProperties properties) {
        this.properties = properties;

        // 注册所有策略
        this.strategyMap = Map.of(
                "fixed_size", fixedSize,
                "semantic", semantic,
                "special_content", specialContent,
                "parent_child", parentChild
        );

        log.info("文档切割服务初始化完成，当前策略: {}", properties.getStrategy());
    }

    @Override
    public List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens) {
        ChunkStrategy strategy = strategyMap.get(properties.getStrategy().name().toLowerCase());
        if (strategy == null) {
            log.warn("未找到切割策略 '{}'，回退到语义边界切割", properties.getStrategy());
            strategy = strategyMap.get("semantic");
        }

        log.debug("执行切割策略: {}, maxTokens={}, overlapTokens={}, textLen={}",
                strategy.strategyName(), maxTokens, overlapTokens,
                text != null ? text.length() : 0);

        return strategy.chunk(text, sourceId, maxTokens, overlapTokens);
    }
}
