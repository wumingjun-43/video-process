-- =====================================================
-- 向量数据库初始化脚本
-- 数据库: PostgreSQL (niuwang)
-- 日期: 2026-07-15
-- 说明: 安装 pgvector 扩展 + 创建 vector_store 表
-- =====================================================

-- 1. 创建 pgvector 扩展（必须先执行）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建 vector_store 表
-- 向量维度: 1024 (DashScope embedding 模型输出)
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL COMMENT '文档文本内容',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb COMMENT '元数据，包含 source_id, chunk_index 等',
    embedding vector(1024) NOT NULL COMMENT '1024 维嵌入向量'
);

-- 3. 创建索引加速向量相似度搜索
-- hnsw 索引：高速近似最近邻搜索
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);

-- 4. 为 metadata 创建 GIN 索引加速过滤查询
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata ON vector_store USING gin (metadata);

-- 5. 验证表和数据
SELECT table_name FROM information_schema.tables WHERE table_name = 'vector_store';
