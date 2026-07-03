package com.niuwang.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring AI 向量库配置
 * 使用简易内存向量存储
 */
@Configuration
public class SpringAiVectorConfig {

    @Bean
    public VectorStore vectorStore() {
        Map<String, Document> store = new ConcurrentHashMap<>();

        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {
                for (Document doc : documents) {
                    String id = doc.getId() != null ? doc.getId() : String.valueOf(System.nanoTime());
                    store.put(id, doc);
                }
            }

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                return store.values().stream().toList();
            }

            @Override
            public void delete(List<String> ids) {
                ids.forEach(store::remove);
            }

            @Override
            public void delete(Filter.Expression filter) {
                // 暂不支持过滤删除
            }
        };
    }
}
