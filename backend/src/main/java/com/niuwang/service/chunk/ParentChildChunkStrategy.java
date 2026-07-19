package com.niuwang.service.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 策略四：父子切割（Parent-Child Chunking）
 *
 * 核心思路：检索时用放大镜（小块，精准定位），返回时用全景图（大块，上下文完整）。
 * - 存储两份：细粒度小 chunk（child）用于向量检索，粗粒度大 chunk（parent）存储完整上下文
 * - 检索时用小 chunk 找到精准命中点，根据关联 ID 取出对应的大 chunk 交给 LLM 阅读
 *
 * 类比：图书馆找书——用目录卡（小 chunk）快速定位到某章某节，但拿出来读的是完整的一章（大 chunk）。
 *
 * 适用场景：对生成质量要求高的 RAG 系统，兼顾检索精度和上下文完整性。
 */
@Slf4j
@Component
public class ParentChildChunkStrategy implements ChunkStrategy {

    /** 估计每个 token 对应的中文字数 */
    private static final int TOKEN_TO_CHAR_RATIO = 2;

    /** 中文句子结束标点 */
    private static final String SENTENCE_END = "[。！？；.!?;]";

    @Override
    public List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        text = normalizeNewlines(text);

        // 父子切割的核心参数：
        // childMaxTokens: 子 chunk 大小，用于向量检索（语义聚焦，检索精度高）
        // parentMaxTokens: 父 chunk 大小，检索后返回完整上下文（生成质量更好）
        int childMaxTokens = 200;   // 子 chunk 200 token ≈ 400 字
        int parentMaxTokens = 1000; // 父 chunk 1000 token ≈ 2000 字
        int parentOverlapTokens = 50;

        // 如果传入的 maxTokens 小于 childMaxTokens，说明用户希望更细粒度
        if (maxTokens > 0 && maxTokens < childMaxTokens) {
            childMaxTokens = maxTokens;
            // 父 chunk 为子 chunk 的 4-5 倍
            parentMaxTokens = childMaxTokens * 5;
        } else if (maxTokens > childMaxTokens) {
            // 用户传入了更大的 maxTokens，按比例调整
            childMaxTokens = maxTokens / 4;
            parentMaxTokens = maxTokens;
        }

        // 第一步：按句子切分为最小的原子单元
        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) {
            return Collections.emptyList();
        }

        // 第二步：用句子构建子 chunk（child chunks）
        List<ChunkInfo> childChunks = buildChildChunks(sentences, childMaxTokens, overlapTokens);

        // 第三步：为每个子 chunk 找到对应的父 chunk（parent chunks）
        // 父 chunk = 子 chunk 前后各扩展 N 个句子，形成完整上下文
        List<Document> documents = buildParentChildDocuments(childChunks, sentences, sourceId,
                parentMaxTokens, parentOverlapTokens);

        log.info("[父子切割] 原文本 {} 字符 → {} 个子chunk + {} 个父chunk (childTokens={}, parentTokens={})",
                text.length(), childChunks.size(), documents.size(),
                childMaxTokens, parentMaxTokens);
        return documents;
    }

    @Override
    public String strategyName() {
        return "parent_child";
    }

    // ==================== 子 chunk 构建 ====================

    /** 将句子列表合并为子 chunk */
    private List<ChunkInfo> buildChildChunks(List<String> sentences, int maxTokens, int overlapTokens) {
        List<ChunkInfo> childChunks = new ArrayList<>();
        int maxChars = maxTokens * TOKEN_TO_CHAR_RATIO;
        int overlapChars = overlapTokens * TOKEN_TO_CHAR_RATIO;

        StringBuilder current = new StringBuilder();
        int currentLen = 0;
        List<Integer> sentenceIndices = new ArrayList<>();
        int childIndex = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentLen = sentence.length();

            if (currentLen + sentLen <= maxChars || current.length() == 0) {
                if (current.length() > 0) {
                    current.append(" ");
                }
                current.append(sentence);
                currentLen += sentLen + 1;
                sentenceIndices.add(i);
            } else {
                // 保存当前子 chunk
                childChunks.add(new ChunkInfo(childIndex++, current.toString().trim(),
                        new ArrayList<>(sentenceIndices), ChunkType.CHILD));

                // 重叠：保留最后几个句子
                if (overlapChars > 0 && currentLen > overlapChars) {
                    int overlapStartIndex = findOverlapSentenceIndex(sentences, sentenceIndices, overlapChars);
                    current = new StringBuilder();
                    currentLen = 0;
                    sentenceIndices.clear();

                    // 从重叠起始位置重新开始
                    for (int j = overlapStartIndex; j < i; j++) {
                        String s = sentences.get(j);
                        if (currentLen + s.length() + (currentLen > 0 ? 1 : 0) <= maxChars) {
                            if (current.length() > 0) current.append(" ");
                            current.append(s);
                            currentLen += s.length() + 1;
                            sentenceIndices.add(j);
                        }
                    }
                } else {
                    current = new StringBuilder(sentence);
                    currentLen = sentLen;
                    sentenceIndices.clear();
                    sentenceIndices.add(i);
                }
            }
        }

        // 保存最后一个子 chunk
        if (current.length() > 0) {
            childChunks.add(new ChunkInfo(childIndex, current.toString().trim(),
                    new ArrayList<>(sentenceIndices), ChunkType.CHILD));
        }

        return childChunks;
    }

    /** 找到重叠区域的起始句子索引 */
    private int findOverlapSentenceIndex(List<String> sentences, List<Integer> indices, int overlapChars) {
        // 从后往前找，直到累计字符数超过 overlapChars
        int totalChars = 0;
        for (int i = indices.size() - 1; i >= 0; i--) {
            int idx = indices.get(i);
            totalChars += sentences.get(idx).length();
            if (totalChars >= overlapChars) {
                return idx;
            }
        }
        return indices.get(0);
    }

    // ==================== 父 chunk 构建 ====================

    /** 为每个子 chunk 构建对应的父 chunk（含上下文） */
    private List<Document> buildParentChildDocuments(List<ChunkInfo> childChunks,
                                                     List<String> sentences, Long sourceId,
                                                     int parentMaxTokens, int parentOverlapTokens) {
        List<Document> documents = new ArrayList<>();
        int maxChars = parentMaxTokens * TOKEN_TO_CHAR_RATIO;
        int overlapChars = parentOverlapTokens * TOKEN_TO_CHAR_RATIO;

        // 按子 chunk 的句子索引范围，聚合出父 chunk
        // 相邻的子 chunk 如果句子范围接近，合并到同一个父 chunk
        List<ParentGroup> groups = new ArrayList<>();
        ParentGroup currentGroup = null;

        for (ChunkInfo child : childChunks) {
            if (child.sentenceIndices.isEmpty()) continue;

            int firstSentIdx = child.sentenceIndices.get(0);
            int lastSentIdx = child.sentenceIndices.get(child.sentenceIndices.size() - 1);

            if (currentGroup == null) {
                currentGroup = new ParentGroup();
                currentGroup.firstSentence = firstSentIdx;
                currentGroup.lastSentence = lastSentIdx;
                groups.add(currentGroup);
            } else {
                // 如果子 chunk 的句子与当前组接近（间隔不超过 3 个句子），归入同一组
                if (firstSentIdx - currentGroup.lastSentence <= 3) {
                    currentGroup.lastSentence = Math.max(currentGroup.lastSentence, lastSentIdx);
                } else {
                    currentGroup = new ParentGroup();
                    currentGroup.firstSentence = firstSentIdx;
                    currentGroup.lastSentence = lastSentIdx;
                    groups.add(currentGroup);
                }
            }
        }

        // 从每组中提取父 chunk 文本，如果超长则进一步分割
        int parentIndex = 0;
        for (ParentGroup group : groups) {
            // 向前后扩展，形成完整的父上下文
            int expandForward = 3; // 向前扩展 3 个句子
            int expandBackward = 3; // 向后扩展 3 个句子

            int startIdx = Math.max(0, group.firstSentence - expandForward);
            int endIdx = Math.min(sentences.size(), group.lastSentence + expandBackward + 1);

            // 收集句子构建父 chunk
            List<String> parentSentences = sentences.subList(startIdx, endIdx);
            String parentText = String.join(" ", parentSentences);

            // 如果父 chunk 仍然超长，按句子分割
            if (parentText.length() > maxChars) {
                List<String> parts = splitBySentenceWindow(parentSentences, maxChars, overlapChars);
                for (int i = 0; i < parts.size(); i++) {
                    documents.add(buildParentDocument(parts.get(i), sourceId, parentIndex + i,
                            childChunks, group));
                }
                parentIndex += parts.size();
            } else {
                documents.add(buildParentDocument(parentText.trim(), sourceId, parentIndex++,
                        childChunks, group));
            }
        }

        return documents;
    }

    /** 按句子窗口切分超长父 chunk */
    private List<String> splitBySentenceWindow(List<String> sentences, int maxChars, int overlapChars) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentLen = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sent = sentences.get(i);
            if (currentLen + sent.length() > maxChars && current.length() > 0) {
                parts.add(current.toString().trim());
                // 重叠
                if (overlapChars > 0 && currentLen > overlapChars) {
                    int count = 0;
                    for (int j = current.length() - 1; j >= 0; j--) {
                        if (current.charAt(j) == ' ') count++;
                        if (count >= 3) {
                            current = new StringBuilder(current.substring(j + 1));
                            break;
                        }
                    }
                } else {
                    current = new StringBuilder();
                }
                currentLen = current.length();
            }
            if (current.length() > 0) current.append(" ");
            current.append(sent);
            currentLen += sent.length() + 1;
        }

        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }

        return parts;
    }

    private Document buildParentDocument(String text, Long sourceId, int index,
                                         List<ChunkInfo> childChunks, ParentGroup group) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_id", sourceId);
        metadata.put("chunk_index", index);
        metadata.put("strategy", "parent_child");
        metadata.put("chunk_type", "parent");
        // 记录关联的子 chunk 索引范围
        if (!group.childIndices.isEmpty()) {
            metadata.put("child_indices", group.childIndices);
        }
        return new Document(text.trim(), metadata);
    }

    // ==================== 句子分割 ====================

    /** 按中文标点分割句子 */
    private List<String> splitSentences(String text) {
        String[] parts = text.split("(?<=" + SENTENCE_END.replace("[", "\\[").replace("]", "\\]")
                .replace("\\", "\\\\") + ")");
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private String normalizeNewlines(String text) {
        return text.replaceAll("\\r\\n", "\n").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    // ==================== 辅助类 ====================

    /** 子 chunk 信息 */
    record ChunkInfo(int index, String text, List<Integer> sentenceIndices, ChunkType type) {}

    enum ChunkType { CHILD, PARENT }

    /** 父 chunk 分组 */
    static class ParentGroup {
        int firstSentence;
        int lastSentence;
        List<Integer> childIndices = new ArrayList<>();
    }
}
