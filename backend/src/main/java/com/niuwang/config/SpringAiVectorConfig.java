package com.niuwang.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Spring AI 向量库配置
 * 使用 Dynamic Datasource 的 PostgreSQL 数据源 (slave)
 * 基于 Spring AI Alibaba 1.1.2
 *
 * 数据源路由:
 * - master: MySQL (业务数据)
 * - slave:  PostgreSQL (pgvector 向量存储)
 */
@Configuration
public class SpringAiVectorConfig {

    /** 向量维度: 512 (insightface buffalo_l 模型输出) */
    private static final int VECTOR_DIMENSION = 512;

    /**
     * PostgreSQL JdbcTemplate
     * 从 DynamicRoutingDataSource 获取 slave 数据源
     */
    @Bean(name = "postgresqlJdbcTemplate")
    public JdbcTemplate postgresqlJdbcTemplate(DynamicRoutingDataSource dynamicRoutingDataSource) {
        DataSource postgresDs = dynamicRoutingDataSource.getDataSource("slave");
        if (postgresDs == null) {
            throw new IllegalStateException("PostgreSQL datasource 'slave' not found in dynamic-datasource");
        }
        return new JdbcTemplate(postgresDs);
    }

    /**
     * 使用 pgvector 创建向量存储 Bean
     * 自动建表: vector_store, 列: embedding vector(512)
     */
    @Bean
    public VectorStore vectorStore(
            JdbcTemplate postgresqlJdbcTemplate,
            EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(postgresqlJdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName("vector_store")
                .dimensions(VECTOR_DIMENSION)
                .build();
    }
}
