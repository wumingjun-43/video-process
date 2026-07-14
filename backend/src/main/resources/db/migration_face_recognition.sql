-- =====================================================
-- 人脸识别系统 - 数据库迁移脚本
-- 执行日期: 2026-07-12
-- 说明: 人脸特征向量已迁移至 PostgreSQL pgvector 向量库
--       MySQL 中仅保留人脸图片路径
-- =====================================================

USE niuwang;

-- 1. 为用户表添加人脸图片路径字段(如果尚未添加)
ALTER TABLE user_info ADD COLUMN IF NOT EXISTS face_image_url VARCHAR(500) COMMENT '人脸照片存储路径';

-- 2. 为匹配记录表添加用户ID字段(支持人脸识别记录)
ALTER TABLE match_record ADD COLUMN IF NOT EXISTS user_id BIGINT COMMENT '匹配的用户ID(人脸识别用)';

-- 3. 创建索引以优化查询
CREATE INDEX IF NOT EXISTS idx_user_face ON user_info(face_image_url);
CREATE INDEX IF NOT EXISTS idx_match_record_user ON match_record(user_id);

-- 4. 注意: face_feature_vector 和 face_reference_urls 字段已废弃
--    人脸特征向量已迁移至 PostgreSQL pgvector 向量库存储
--    如需清理旧字段，可执行:
-- ALTER TABLE user_info DROP COLUMN IF EXISTS face_feature_vector;
-- ALTER TABLE user_info DROP COLUMN IF EXISTS face_reference_urls;
