package com.niuwang.service.impl;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.service.RagOnlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 在线检索服务实现
 *
 * 在线阶段完整流程：
 * 1. Query 处理：意图分析 + 语义重写（将口语化问题改写为更适合检索的形式）
 * 2. 向量检索(粗排)：从 pgvector 检索 Top-20
 * 3. Rerank(精排)：用 LLM 对粗排结果做深度相关性评分，取 Top-5
 * 4. 组装上下文供答案生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagOnlineServiceImpl implements RagOnlineService {

    private final VectorStore vectorStore;
    private final ChatClient configChatClient;

    /** 粗排返回数量 */
    private static final int COARSE_TOP_K = 20;

    /** 精排保留数量 */
    private static final int RERANK_TOP_K = 5;

    @Value("${rag.retrieval.top-k:5}")
    private int defaultTopK;

    @Override
    public List<Document> retrieve(String question, List<Long> knowledgeFileIds) {
        try {
            // ===== Step 1: Query 处理 =====
            String rewrittenQuery = rewriteQuery(question);
            log.info("Query 处理完成: 原问题='{}', 重写='{}'", question, rewrittenQuery);

            // ===== Step 2: 向量检索 (粗排) =====
            List<Document> coarseDocs = coarseRetrieve(rewrittenQuery, knowledgeFileIds);
            log.info("粗排检索到 {} 个文档片段", coarseDocs.size());

            if (coarseDocs.isEmpty()) {
                return Collections.emptyList();
            }

            // ===== Step 3: Rerank (精排) =====
            List<Document> rerankedDocs = rerank(coarseDocs, question, rewrittenQuery);
            log.info("精排后保留 {} 个文档片段", rerankedDocs.size());

            return rerankedDocs;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG 在线检索失败: {}", question, e);
            throw new BusinessException("检索失败: " + e.getMessage());
        }
    }

    /**
     * Query 语义重写
     * 将口语化、模糊的问题改写为更适合向量检索的形式
     * 例如："上次说的那个方案怎么样" → "XX方案的具体内容和评价"
     */
    private String rewriteQuery(String question) {
        try {
            String prompt = String.format(
                    """
                    你是一个查询改写助手。用户提出了一个问题，你需要将其改写为适合知识库检索的形式。
                    改写原则：
                    1. 保留问题的核心语义
                    2. 补充可能缺失的关键信息
                    3. 去除口语化表达
                    4. 保持简洁，不超过 50 个字
                    5. 如果问题已经很清晰，原样返回

                    用户问题：%s

                    请直接返回改写后的问题，不要加任何解释。""",
                    question);

            String rewritten = configChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();

            // 清理可能的引号或多余内容
            rewritten = rewritten.replaceAll("^[。？！\"\"''\"\"]+", "")
                    .replaceAll("[。？！\"\"''\"\"]+$", "");

            return rewritten;
        } catch (Exception e) {
            log.warn("Query 重写失败，使用原始问题: {}", e.getMessage());
            return question;
        }
    }

    /**
     * 向量检索（粗排）
     * 从 pgvector 中快速检索 Top-K 最相似的文档
     */
    private List<Document> coarseRetrieve(String query, List<Long> knowledgeFileIds) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(COARSE_TOP_K);

            // 如果指定了知识文件范围，通过 metadata 过滤
            if (knowledgeFileIds != null && !knowledgeFileIds.isEmpty()) {
                // 用编程式 Filter API 构建 OR 表达式（IN 在 PgVector 中有 jsonpath bug）
                Filter.Expression orExpr = buildOrExpression(knowledgeFileIds);
                builder.filterExpression(orExpr);
            }

            return vectorStore.similaritySearch(builder.build());

        } catch (Exception e) {
            log.error("粗排检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建 OR 表达式: source_id='1' OR source_id='2' OR ...
     */
    private Filter.Expression buildOrExpression(List<Long> ids) {
        if (ids.size() == 1) {
            return new Filter.Expression(
                    ExpressionType.EQ,
                    new Filter.Key("source_id"),
                    new Filter.Value(ids.get(0).toString())
            );
        }
        // 递归构建 OR 树: OR(left, OR(right1, right2, ...))
        Filter.Expression result = new Filter.Expression(
                ExpressionType.EQ,
                new Filter.Key("source_id"),
                new Filter.Value(ids.get(0).toString())
        );
        for (int i = 1; i < ids.size(); i++) {
            Filter.Expression eq = new Filter.Expression(
                    ExpressionType.EQ,
                    new Filter.Key("source_id"),
                    new Filter.Value(ids.get(i).toString())
            );
            result = new Filter.Expression(ExpressionType.OR, result, eq);
        }
        return result;
    }

    /**
     * Rerank 精排
     * 用 LLM 对粗排结果做深度语义相关性评分，过滤不相关内容
     */
    private List<Document> rerank(List<Document> coarseDocs, String question, String rewrittenQuery) {
        if (coarseDocs.isEmpty()) {
            return coarseDocs;
        }

        // 构建评分 prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个文档相关性评分助手。请评估以下每个文档片段与用户问题的相关性。\n\n");
        prompt.append("用户问题：").append(question).append("\n");
        prompt.append("改写后查询：").append(rewrittenQuery).append("\n\n");
        prompt.append("请对每个文档片段评分（0-5分），0=完全不相关，5=高度相关。\n");
        prompt.append("只返回 JSON 数组，格式：[{\"index\": 0, \"score\": 5}, ...]\n\n");

        for (int i = 0; i < coarseDocs.size(); i++) {
            Document doc = coarseDocs.get(i);
            String sourceInfo = "";
            if (doc.getMetadata() != null) {
                Object sourceId = doc.getMetadata().get("source_id");
                if (sourceId != null) {
                    sourceInfo = " [来源文件ID:" + sourceId + "]";
                }
            }
            prompt.append(String.format("片段 %d%s:\n%s\n\n", i, sourceInfo,
                    truncate(doc.getText(), 400)));
        }

        try {
            String response = configChatClient.prompt()
                    .user(prompt.toString())
                    .call()
                    .content();

            // 解析 LLM 返回的评分结果
            List<RerankScore> scores = parseRerankScores(response, coarseDocs.size());

            // 过滤低分结果并按分数排序
            List<Document> reranked = new ArrayList<>();
            for (RerankScore score : scores.stream()
                    .filter(s -> s.score >= 3)
                    .sorted(Comparator.comparingInt(s -> -s.score))
                    .limit(RERANK_TOP_K)
                    .collect(Collectors.toList())) {
                if (score.index >= 0 && score.index < coarseDocs.size()) {
                    reranked.add(coarseDocs.get(score.index));
                }
            }

            log.info("Rerank 完成: 粗排 {} → 精排 {}", coarseDocs.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("LLM Rerank 失败，使用关键词重排序: {}", e.getMessage());
            return fallbackRerank(coarseDocs, question);
        }
    }

    /**
     * 降级方案：关键词重排序
     */
    private List<Document> fallbackRerank(List<Document> coarseDocs, String question) {
        Set<String> keywords = extractKeywords(question);
        List<Document> ranked = new ArrayList<>(coarseDocs);
        ranked.sort((a, b) -> {
            int scoreA = computeKeywordScore(a.getText(), keywords);
            int scoreB = computeKeywordScore(b.getText(), keywords);
            return Integer.compare(scoreB, scoreA);
        });
        return ranked.subList(0, Math.min(RERANK_TOP_K, ranked.size()));
    }

    /**
     * 解析 Rerank 评分 JSON
     */
    private List<RerankScore> parseRerankScores(String response, int docCount) {
        List<RerankScore> scores = new ArrayList<>();
        try {
            // 尝试从响应中提取 JSON 数组
            String json = extractJsonArray(response);
            if (json != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                RerankScore[] arr = mapper.readValue(json, RerankScore[].class);
                scores = Arrays.asList(arr);
            }
        } catch (Exception e) {
            log.warn("解析 Rerank 评分失败: {}", e.getMessage());
        }

        // 如果解析失败，默认给所有文档相同分数
        if (scores.isEmpty()) {
            for (int i = 0; i < docCount; i++) {
                scores.add(new RerankScore(i, 3));
            }
        }
        return scores;
    }

    /**
     * 从文本中提取 JSON 数组
     */
    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    /**
     * 从问题中提取关键词
     */
    private Set<String> extractKeywords(String question) {
        Set<String> stopwords = Set.of("的", "了", "是", "在", "我", "有", "和", "就",
                "不", "人", "都", "一", "一个", "上", "也", "很", "到",
                "说", "要", "去", "会", "吗", "什么", "怎么", "为什么", "如何");
        Set<String> keywords = new HashSet<>();
        for (char c : question.toCharArray()) {
            String s = String.valueOf(c);
            if (!stopwords.contains(s) && !Character.isWhitespace(c)) {
                keywords.add(s);
            }
        }
        return keywords;
    }

    private int computeKeywordScore(String text, Set<String> keywords) {
        int score = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) score++;
        }
        return score;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /** Rerank 评分结果 */
    static class RerankScore {
        int index;
        int score;

        RerankScore() {}

        RerankScore(int index, int score) {
            this.index = index;
            this.score = score;
        }
    }
}
