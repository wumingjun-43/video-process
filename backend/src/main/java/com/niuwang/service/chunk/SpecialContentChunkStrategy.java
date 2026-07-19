package com.niuwang.service.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 策略三：特殊内容专项处理（Special Content Chunking）
 *
 * 针对代码和表格这两种通用切割策略效果差的特殊内容进行单独处理：
 * - 代码：以函数/类为单位切割，保证每个 chunk 是完整可理解的代码逻辑单元
 * - 表格：整块保留，转成 Markdown 格式存储，不按行截断
 * - 普通文本：回退到语义边界切割
 *
 * 适用场景：技术文档、API 文档、包含代码示例或数据表的文档。
 */
@Slf4j
@Component
public class SpecialContentChunkStrategy implements ChunkStrategy {

    /** 区间表示: [start, end] */
    private static final class Range {
        final int start;
        final int end;
        Range(int s, int e) { this.start = s; this.end = e; }
    }

    /** 估计每个 token 对应的中文字数 */
    private static final int TOKEN_TO_CHAR_RATIO = 2;

    /** 代码块标记：```language ... ``` */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    /** 表格行：至少包含 | 分隔符 */
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|.+\\|.*$", Pattern.MULTILINE);

    /** 段落分隔正则 */
    private static final String PARAGRAPH_SEPARATOR = "\\n\\s*\\n";

    @Override
    public List<Document> chunk(String text, Long sourceId, int maxTokens, int overlapTokens) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        text = normalizeNewlines(text);

        // 第一步：识别并分离出代码块、表格和普通文本
        List<ContentBlock> blocks = identifyContentBlocks(text);

        List<Document> documents = new ArrayList<>();
        int chunkIndex = 0;

        for (ContentBlock block : blocks) {
            switch (block.type) {
                case CODE -> documents.addAll(extractCodeChunks(block.content, sourceId, chunkIndex));
                case TABLE -> documents.add(buildDocument(block.content, sourceId, chunkIndex++));
                default -> documents.addAll(chunkNormalText(block.content, sourceId, chunkIndex, maxTokens, overlapTokens));
            }
            chunkIndex = Math.max(chunkIndex, block.endIndex + 1);
        }

        log.info("[特殊内容专项切割] 原文本 {} 字符 → {} 个 chunk (代码块={}, 表格={}, 普通段={})",
                text.length(), documents.size(),
                blocks.stream().filter(b -> b.type == ContentType.CODE).count(),
                blocks.stream().filter(b -> b.type == ContentType.TABLE).count(),
                blocks.stream().filter(b -> b.type == ContentType.NORMAL).count());
        return documents;
    }

    @Override
    public String strategyName() {
        return "special_content";
    }

    // ==================== 内容块识别 ====================

    enum ContentType { CODE, TABLE, NORMAL }

    record ContentBlock(ContentType type, String content, int endIndex) {}

    /** 识别文本中的代码块、表格和普通文本 */
    private List<ContentBlock> identifyContentBlocks(String text) {
        List<ContentBlock> blocks = new ArrayList<>();
        int pos = 0;

        // 1. 提取所有代码块区间
        List<Range> codeRanges = new ArrayList<>();
        Matcher codeMatcher = CODE_BLOCK_PATTERN.matcher(text);
        while (codeMatcher.find()) {
            codeRanges.add(new Range(codeMatcher.start(), codeMatcher.end()));
        }

        // 2. 提取表格区间（连续 2+ 行的 |...| 模式视为一个表格）
        List<Range> tableRanges = extractTableRanges(text);

        // 3. 合并所有区间，按起始位置排序
        List<Range> allRanges = new ArrayList<>();
        allRanges.addAll(codeRanges);
        allRanges.addAll(tableRanges);
        allRanges.sort(Comparator.comparingInt(r -> r.start));

        // 4. 合并重叠区间
        List<Range> mergedRanges = mergeOverlappingRanges(allRanges);

        // 5. 构建 ContentBlock 列表
        for (Range range : mergedRanges) {
            // range 之前的文本是 NORMAL
            if (range.start > pos) {
                String normalText = text.substring(pos, range.start).trim();
                if (!normalText.isEmpty()) {
                    blocks.add(new ContentBlock(ContentType.NORMAL, normalText, range.start));
                }
            }

            // 判断当前 range 是代码还是表格
            boolean isCode = codeRanges.stream().anyMatch(r -> r.start == range.start);
            blocks.add(new ContentBlock(isCode ? ContentType.CODE : ContentType.TABLE,
                    text.substring(range.start, range.end).trim(), range.end));
            pos = range.end;
        }

        // 剩余文本
        if (pos < text.length()) {
            String remainder = text.substring(pos).trim();
            if (!remainder.isEmpty()) {
                blocks.add(new ContentBlock(ContentType.NORMAL, remainder, text.length()));
            }
        }

        // 过滤空块
        blocks.removeIf(b -> b.content.isEmpty());
        return blocks;
    }

    /** 从表格行模式中提取连续表格区间 */
    private List<Range> extractTableRanges(String text) {
        List<Range> ranges = new ArrayList<>();
        Matcher matcher = TABLE_ROW_PATTERN.matcher(text);
        List<Range> rows = new ArrayList<>();

        while (matcher.find()) {
            Range row = new Range(matcher.start(), matcher.end());
            if (rows.isEmpty()) {
                rows.add(row);
            } else {
                Range last = rows.get(rows.size() - 1);
                // 连续（间隔不超过 100 字符）视为同一表格
                if (row.start - last.end < 100) {
                    rows.add(row);
                } else {
                    flushTableRow(rows, ranges);
                    rows.clear();
                    rows.add(row);
                }
            }
        }
        flushTableRow(rows, ranges);
        return ranges;
    }

    /** 将累积的表格行合并为一个 Range */
    private void flushTableRow(List<Range> rows, List<Range> result) {
        if (rows.size() >= 2) {
            int minStart = rows.stream().mapToInt(r -> r.start).min().orElse(0);
            int maxEnd = rows.stream().mapToInt(r -> r.end).max().orElse(0);
            result.add(new Range(minStart, maxEnd));
        }
    }

    /** 合并重叠/相邻区间 */
    private List<Range> mergeOverlappingRanges(List<Range> ranges) {
        if (ranges.isEmpty()) return Collections.emptyList();

        List<Range> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(r -> r.start));

        List<Range> merged = new ArrayList<>();
        Range current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            Range next = sorted.get(i);
            if (next.start <= current.end) {
                current = new Range(current.start, Math.max(current.end, next.end));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    // ==================== 代码切分：以函数/类为单位 ====================

    /** 将代码块按函数/类边界切分 */
    private List<Document> extractCodeChunks(String code, Long sourceId, int startIndex) {
        List<Document> documents = new ArrayList<>();
        List<String> functions = extractFunctions(code);

        for (int i = 0; i < functions.size(); i++) {
            String func = functions.get(i).trim();
            if (func.isEmpty()) continue;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source_id", sourceId);
            metadata.put("chunk_index", startIndex + i);
            metadata.put("strategy", "special_content");
            metadata.put("content_type", functions.size() > 1 ? "code_function" : "code_block");
            documents.add(new Document(func, metadata));
        }

        return documents;
    }

    /** 从代码文本中提取独立的函数/方法块 */
    private List<String> extractFunctions(String code) {
        List<String> functions = new ArrayList<>();

        // 尝试按函数/类定义关键字分割
        String[] parts = code.split("(?=(?:^|\\n)\\s*(?:(?:def |async def )|(?:(?:public|private|protected|static|final|abstract)\\s+[\\w<>\\[\\],\\s]+\\s+\\w+\\s*\\())|(?:^|\\n)\\s*(?:class\\s+\\w+)|(?:^|\\n)\\s*(?:function\\s+\\w+)|(?:^|\\n)\\s*(?:const\\s+\\w+\\s*=\\s*(?:async\\s*)?(?:function|\\())))");

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                functions.add(trimmed);
            }
        }

        // 如果分割太激进，回退到按空行+函数关键字分割
        if (functions.size() <= 1 && code.contains("\n")) {
            String[] fallback = code.split("(?<=\\n\\n)(?=(?:def |class |function |const )\\w)");
            for (String part : fallback) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    functions.add(trimmed);
                }
            }
        }

        return functions.isEmpty() ? List.of(code) : functions;
    }

    // ==================== 普通文本：回退到语义切割 ====================

    /** 对普通文本使用简单语义切分 */
    private List<Document> chunkNormalText(String text, Long sourceId,
                                          int startIndex, int maxTokens, int overlapTokens) {
        List<Document> documents = new ArrayList<>();
        int maxChars = maxTokens * TOKEN_TO_CHAR_RATIO;
        int overlapChars = overlapTokens * TOKEN_TO_CHAR_RATIO;

        String[] paragraphs = text.split(PARAGRAPH_SEPARATOR);
        StringBuilder current = new StringBuilder();
        int currentLen = 0;
        int idx = startIndex;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int paraLen = trimmed.length();
            if (currentLen + paraLen > maxChars && current.length() > 0) {
                documents.add(buildDocument(current.toString().trim(), sourceId, idx++));

                // 重叠
                if (overlapChars > 0 && currentLen > overlapChars) {
                    int overlapStart = current.length() - overlapChars;
                    int lastNewline = current.lastIndexOf("\n", overlapStart);
                    if (lastNewline > 0) {
                        current = new StringBuilder(current.substring(lastNewline + 1));
                    } else {
                        current = new StringBuilder(current.substring(overlapStart));
                    }
                } else {
                    current = new StringBuilder();
                }
                currentLen = current.length();
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
            currentLen += paraLen + 2;
        }

        if (current.length() > 0) {
            documents.add(buildDocument(current.toString().trim(), sourceId, idx));
        }

        return documents;
    }

    private Document buildDocument(String text, Long sourceId, int index) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_id", sourceId);
        metadata.put("chunk_index", index);
        metadata.put("strategy", "special_content");
        return new Document(text.trim(), metadata);
    }

    private String normalizeNewlines(String text) {
        return text.replaceAll("\\r\\n", "\n").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }
}
