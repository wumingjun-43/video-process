-- =====================================================
-- 智能对话 - 数据库迁移脚本
-- 执行日期: 2026-07-15
-- =====================================================

-- 1. 创建对话历史表
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT PRIMARY KEY COMMENT '主键ID(雪花算法)',
    question VARCHAR(2000) NOT NULL COMMENT '用户提问',
    answer TEXT COMMENT 'AI回答',
    knowledge_file_ids VARCHAR(500) COMMENT '使用的知识文件ID列表(逗号分隔)',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能对话历史表';

-- 2. 创建索引
CREATE INDEX idx_chat_history_time ON chat_history(create_time);
CREATE INDEX idx_chat_history_question ON chat_history(question(200));
