package com.niuwang.service.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 策略一：固定大小切割（Fixed Size Chunking）
 *
 * 按固定字符数切割文本，相邻 chunk 之间通过重叠（overlap）来缓解边界截断问题。
 * 这种策略最简单高效，但不考虑语义边界，可能在句子中间截断。
 *
 * 适用场景：对切分质量要求不高、追求处理速度的场景，通常作为基线方案。
 */
@Slf4j
@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {

    /** 估计每个 token 对应的中文字数 */
    private static final int TOKEN_TO_CHAR_RATIO = 2;

    @Override
    public List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        text = normalizeNewlines(text);

        int maxChars = maxTokens * TOKEN_TO_CHAR_RATIO;
        int overlapChars = overlapTokens * TOKEN_TO_CHAR_RATIO;
        List<Document> documents = new ArrayList<>();

        int start = 0;
        int chunkIndex = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            String chunkText = text.substring(start, end).trim();

            if (!chunkText.isEmpty()) {
                documents.add(buildDocument(chunkText, sourceId, chunkIndex++));
            }

            // 如果没有重叠，直接前进；否则回退 overlapChars 实现重叠
            if (overlapChars > 0 && end < text.length()) {
                start = end - overlapChars;
            } else {
                start = end;
            }
        }

        log.info("[固定大小切割] 原文本 {} 字符 → {} 个 chunk (maxTokens={}, overlap={})",
                text.length(), documents.size(), maxTokens, overlapTokens);
        return documents;
    }

    @Override
    public String strategyName() {
        return "fixed_size";
    }

    private Document buildDocument(String text, Long sourceId, int index) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_id", sourceId);
        metadata.put("chunk_index", index);
        metadata.put("strategy", "fixed_size");
        return new Document(text.trim(), metadata);
    }

    private String normalizeNewlines(String text) {
        return text.replaceAll("\\r\\n", "\n").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }
}