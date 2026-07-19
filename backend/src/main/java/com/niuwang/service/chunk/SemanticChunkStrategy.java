package com.niuwang.service.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 策略二：语义边界切割（Semantic/Structure Based Chunking）
 *
 * 按文档的天然断点来切——段落、句子、标题层级。核心思想：不要在语义中间截断，
 * 找到文字天然的「断点」再切。
 *
 * 切割层次：
 * 1. 先按标题层级（# 或 X.Y. 格式）切分为大块
 * 2. 大块内按段落（双换行）分割
 * 3. 段落内按句子（。！？；）合并，不超过 maxTokens
 * 4. 超长段落兜底按标点断句
 *
 * 适用场景：大多数自然语言文档（说明书、论文、规章制度等），切分质量好。
 */
@Slf4j
@Component
public class SemanticChunkStrategy implements ChunkStrategy {

    /** 估计每个 token 对应的中文字数 */
    private static final int TOKEN_TO_CHAR_RATIO = 2;

    /** 中文句子结束标点正则 */
    private static final String SENTENCE_END_PATTERN = "[。！？；.!?;]";

    /** 中文段落分隔：双换行 */
    private static final String PARAGRAPH_SEPARATOR = "\\n\\s*\\n";

    /** 标题模式：Markdown 风格 (# 开头) 或 编号风格 (1. / 1.1 / 第一章) */
    private static final String HEADING_PATTERN = "(?:(?:^|#\\s+|\\d+[.．]\\s|第[一二三四五六七八九十百千万]+[章节部分])[\\s\\S]*?)(?=\\n|$)";

    @Override
    public List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        text = normalizeNewlines(text);

        // 第一步：检测是否有标题层级结构
        boolean hasHeadings = hasHeadingStructure(text);

        List<Document> documents;
        if (hasHeadings) {
            // 有标题结构：先按标题分块，再在各标题块内做语义切分
            documents = chunkByHeadings(text, sourceId, maxTokens, overlapTokens);
        } else {
            // 无标题结构：直接按段落+句子切分
            documents = chunkByParagraphs(text, sourceId, maxTokens, overlapTokens);
        }

        log.info("[语义边界切割] 原文本 {} 字符 → {} 个 chunk (maxTokens={}, overlap={})",
                text.length(), documents.size(), maxTokens, overlapTokens);
        return documents;
    }

    @Override
    public String strategyName() {
        return "semantic";
    }

    // ==================== 标题层级切分 ====================

    /** 检测文本是否包含标题层级结构 */
    private boolean hasHeadingStructure(String text) {
        // 检测 Markdown 标题 (#) 或 中文编号标题 (第一章、1.、1.1)
        return text.matches(".*(?:^|#\\s+|\\d+[.．]\\s|第[一二三四五六七八九十百千万]+[章节部分]).*");
    }

    /** 按标题层级将文本拆分为大块 */
    private List<Document> chunkByHeadings(String text, Long sourceId,
                                           int maxTokens, int overlapTokens) {
        // 按 Markdown 标题分割
        String[] headingBlocks = text.split("(?=^#\\s+.+)");
        List<Document> documents = new ArrayList<>();

        for (String block : headingBlocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            // 每个标题块内部再做语义切分
            documents.addAll(chunkByParagraphs(block, sourceId, maxTokens, overlapTokens));
        }

        return documents;
    }

    /** 按段落+句子进行语义切分 */
    private List<Document> chunkByParagraphs(String text, Long sourceId,
                                             int maxTokens, int overlapTokens) {
        // 按段落分割
        String[] paragraphs = text.split(PARAGRAPH_SEPARATOR);
        List<String> segments = mergeParagraphsToSegments(paragraphs, maxTokens);

        List<Document> documents = new ArrayList<>();
        int chunkIndex = 0;

        for (String segment : segments) {
            // 按句子分割
            List<String> sentences = splitSentences(segment);
            documents.addAll(createSemanticChunks(sentences, sourceId, chunkIndex, maxTokens, overlapTokens));
            chunkIndex += sentences.isEmpty() ? 0 : 1;
        }

        // 去重空 chunk
        documents.removeIf(d -> d.getText() == null || d.getText().trim().isEmpty());
        return documents;
    }

    /** 将段落合并为不超过 maxTokens 的语义段 */
    private List<String> mergeParagraphsToSegments(String[] paragraphs, int maxTokens) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokenLen = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int paraTokenLen = trimmed.length() / TOKEN_TO_CHAR_RATIO;

            if (currentTokenLen + paraTokenLen > maxTokens && current.length() > 0) {
                segments.add(current.toString().trim());
                current = new StringBuilder();
                currentTokenLen = 0;
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
            currentTokenLen += paraTokenLen + 2;
        }

        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }

        // 如果单个段仍然超长，按标点兜底切分
        List<String> result = new ArrayList<>();
        for (String seg : segments) {
            if (seg.length() / TOKEN_TO_CHAR_RATIO > maxTokens) {
                result.addAll(splitAtPunctuation(seg, maxTokens));
            } else {
                result.add(seg);
            }
        }
        return result;
    }

    /** 按中文标点兜底切分超长文本 */
    private List<String> splitAtPunctuation(String text, int maxTokens) {
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
                        Math.max(text.lastIndexOf('；', end),
                        text.lastIndexOf('\n', end)))));
                if (lastPunct > start + maxChars / 2) {
                    end = lastPunct + 1;
                }
            }
            parts.add(text.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    /** 按中文标点分割句子 */
    private List<String> splitSentences(String text) {
        // 按句子结束标点分割，保留分隔符
        String[] parts = text.split("(?<=" + SENTENCE_END_PATTERN + ")");
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /** 创建带有重叠的语义 chunk */
    private List<Document> createSemanticChunks(List<String> sentences, Long sourceId,
                                                int startIndex, int maxTokens, int overlapTokens) {
        List<Document> documents = new ArrayList<>();
        int maxChars = maxTokens * TOKEN_TO_CHAR_RATIO;
        int overlapChars = overlapTokens * TOKEN_TO_CHAR_RATIO;

        if (sentences.isEmpty()) return documents;

        StringBuilder currentChunk = new StringBuilder();
        int currentCharLen = 0;
        int chunkIdx = startIndex;

        for (String sentence : sentences) {
            int sentLen = sentence.length();

            if (currentChunk.length() == 0 || currentCharLen + sentLen <= maxChars) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n");
                }
                currentChunk.append(sentence);
                currentCharLen += sentLen + 1;
            } else {
                // 保存当前 chunk
                documents.add(buildDocument(currentChunk.toString().trim(), sourceId, chunkIdx++));

                // 重叠：从重叠区域开始新 chunk
                if (overlapChars > 0 && currentCharLen > overlapChars) {
                    int overlapStart = currentChunk.length() - overlapChars;
                    // 往前找句子边界
                    int lastBreak = currentChunk.lastIndexOf(SENTENCE_END_PATTERN.replace("[", "").replace("]", "")
                            .replace("\\", "").replace("(", "").replace(")", ""), overlapStart);
                    if (lastBreak > 0) {
                        overlapStart = lastBreak + 1;
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
            documents.add(buildDocument(currentChunk.toString().trim(), sourceId, chunkIdx));
        }

        return documents;
    }

    private Document buildDocument(String text, Long sourceId, int index) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_id", sourceId);
        metadata.put("chunk_index", index);
        metadata.put("strategy", "semantic");
        return new Document(text.trim(), metadata);
    }

    private String normalizeNewlines(String text) {
        return text.replaceAll("\\r\\n", "\n").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }
}
