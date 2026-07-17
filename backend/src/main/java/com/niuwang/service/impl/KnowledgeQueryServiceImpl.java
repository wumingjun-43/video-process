package com.niuwang.service.impl;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.KnowledgeGraphService;
import com.niuwang.service.KnowledgeQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识图谱查询服务实现
 * 结合结构化查询 + 向量检索提供精确知识匹配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeQueryServiceImpl implements KnowledgeQueryService {

    private final KnowledgeGraphService knowledgeGraphService;
    private final VectorStore vectorStore;

    @Override
    public List<String> queryKnowledgeGraph(String question, List<Long> knowledgeFileIds) {
        List<String> results = new ArrayList<>();

        // 1. 从知识文件元数据中查找匹配的文件
        List<KnowledgeFileVO> files = listKnowledgeFiles();
        for (KnowledgeFileVO file : files) {
            if (file.getFilename() != null && file.getFilename().contains(question)) {
                results.add("知识文件: " + file.getFilename() + " (状态: " + file.getStatus() + ")");
            }
        }

        // 2. 从向量库中检索与该问题直接相关的文件级摘要（受 knowledgeFileIds 过滤）
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(question)
                    .topK(3);

            // 如果指定了知识文件范围，通过 metadata 过滤
            if (knowledgeFileIds != null && !knowledgeFileIds.isEmpty()) {
                Filter.Expression orExpr = buildOrExpression(knowledgeFileIds);
                builder.filterExpression(orExpr);
            }

            List<Document> fileLevelDocs = vectorStore.similaritySearch(builder.build());

            for (Document doc : fileLevelDocs) {
                if (doc.getMetadata() != null && doc.getMetadata().containsKey("source_id")) {
                    Long sourceId = Long.valueOf(doc.getMetadata().get("source_id").toString());
                    String snippet = truncate(doc.getText(), 200);
                    results.add("相关片段 [文件ID:" + sourceId + "]: " + snippet);
                }
            }
        } catch (Exception e) {
            log.warn("向量级知识查询失败", e);
        }

        return results;
    }

    /**
     * 构建 OR 表达式: source_id='1' OR source_id='2' OR ...
     */
    private Filter.Expression buildOrExpression(List<Long> ids) {
        if (ids.size() == 1) {
            return new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key("source_id"),
                    new Filter.Value(ids.get(0).toString())
            );
        }
        Filter.Expression result = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("source_id"),
                new Filter.Value(ids.get(0).toString())
        );
        for (int i = 1; i < ids.size(); i++) {
            Filter.Expression eq = new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key("source_id"),
                    new Filter.Value(ids.get(i).toString())
            );
            result = new Filter.Expression(Filter.ExpressionType.OR, result, eq);
        }
        return result;
    }

    @Override
    public List<KnowledgeFileVO> listKnowledgeFiles() {
        return knowledgeGraphService.pageKnowledge(1, 1000).getRecords();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
