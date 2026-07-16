package com.niuwang.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档切分服务实现
 *
 * 切分策略：
 * 1. 先按段落（双换行）分割成大块
 * 2. 大块再按句子分割
 * 3. 按 maxTokens 限制 chunk 大小，相邻 chunk 重叠 overlapTokens
 */
@Slf4j
@Service
public class DocumentChunkerServiceImpl implements com.niuwang.service.DocumentChunkerService {

    /** 估计每个 token 对应的中文字数 */
    private static final int TOKEN_TO_CHAR_RATIO = 2;

    @Override
    public List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 清理文本：移除多余空白
        text = text.replaceAll("\\r\\n", "\n").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // 第一步：按段落分割
        String[] paragraphs = text.split("\n\\s*\n");
        if (paragraphs.length == 0) {
            paragraphs = new String[]{text};
        }

        // 第二步：合并段落，形成不超过 maxTokens 的大段
        List<String> segments = mergeParagraphs(paragraphs, maxTokens);

        // 第三步：对每个大段按句子切分并重叠
        List<Document> documents = new ArrayList<>();
        int chunkIndex = 0;

        for (String segment : segments) {
            List<String> sentences = splitSentences(segment);
            documents.addAll(createOverlappingChunks(sentences, sourceId, chunkIndex++, maxTokens, overlapTokens));
        }

        log.info("文档切分完成: 原文本 {} 字符 → {} 个 chunk", text.length(), documents.size());
        return documents;
    }

    /**
     * 将段落合并为不超过 maxTokens 的大段
     */
    private List<String> mergeParagraphs(String[] paragraphs, int maxTokens) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentLen = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int paraLen = trimmed.length() / TOKEN_TO_CHAR_RATIO;

            if (currentLen + paraLen > maxTokens && current.length() > 0) {
                segments.add(current.toString().trim());
                current = new StringBuilder();
                currentLen = 0;
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
            currentLen += paraLen + 2;
        }

        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }

        // 如果单个段落仍然超长，进一步切分
        List<String> result = new ArrayList<>();
        for (String seg : segments) {
            if (seg.length() / TOKEN_TO_CHAR_RATIO > maxTokens) {
                result.addAll(splitByChars(seg, maxTokens));
            } else {
                result.add(seg);
            }
        }
        return result;
    }

    /**
     * 将超长文本按字符切分
     */
    private List<String> splitByChars(String text, int maxTokens) {
        List<String> parts = new ArrayList<>();
        int maxChars = maxTokens * TOKEN_TO_CHAR_RATIO;
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            // 尽量在标点处断开
            if (end < text.length()) {
                int lastPunct = Math.max(text.lastIndexOf('。', end),
                        Math.max(text.lastIndexOf('！', end),
                        Math.max(text.lastIndexOf('？', end),
                        text.lastIndexOf('\n', end))));
                if (lastPunct > start + maxChars / 2) {
                    end = lastPunct + 1;
                }
            }
            parts.add(text.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    /**
     * 按中文标点分割句子
     */
    private List<String> splitSentences(String text) {
        // 按 。！？；\n 分割
        String[] parts = text.split("(?<=[。！？；\\n])");
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /**
     * 创建带有重叠的 chunk 列表
     */
    private List<Document> createOverlappingChunks(List<String> sentences, Long sourceId,
                                                    int startIndex, int maxTokens, int overlapTokens) {
        List<Document> documents = new ArrayList<>();
        int maxChars = maxTokens * TOKEN_TO_CHAR_RATIO;
        int overlapChars = overlapTokens * TOKEN_TO_CHAR_RATIO;

        if (sentences.isEmpty()) return documents;

        StringBuilder currentChunk = new StringBuilder();
        int currentCharLen = 0;
        int chunkIdx = startIndex;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentLen = sentence.length();

            // 如果当前 chunk 为空或加入这句话不会超限
            if (currentChunk.length() == 0 || currentCharLen + sentLen <= maxChars) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n");
                }
                currentChunk.append(sentence);
                currentCharLen += sentLen + 1;
            } else {
                // 保存当前 chunk
                saveChunk(documents, currentChunk.toString(), sourceId, chunkIdx++);

                // 从重叠区域开始新的 chunk
                if (overlapChars > 0 && currentCharLen > overlapChars) {
                    // 找重叠起始位置
                    int overlapStart = currentChunk.length() - overlapChars;
                    // 往前找句子边界
                    int lastSentenceBreak = currentChunk.lastIndexOf("\n", overlapStart);
                    if (lastSentenceBreak > 0) {
                        overlapStart = lastSentenceBreak + 1;
                    }
                    currentChunk = new StringBuilder(currentChunk.substring(overlapStart));
                    currentCharLen = currentChunk.length();
                } else {
                    currentChunk = new StringBuilder(sentence);
                    currentCharLen = sentLen;
                }
            }
        }

        // 保存最后一个 chunk
        if (currentChunk.length() > 0) {
            saveChunk(documents, currentChunk.toString(), sourceId, chunkIdx);
        }

        return documents;
    }

    private void saveChunk(List<Document> documents, String text, Long sourceId, int index) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_id", sourceId);
        metadata.put("chunk_index", index);
        documents.add(new Document(text.trim(), metadata));
    }
}
