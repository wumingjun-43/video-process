-- =====================================================
-- 人脸识别系统 - 数据库迁移脚本
-- 执行日期: 2026-07-11
-- =====================================================

USE niuwang;

-- 1. 为用户表添加人脸相关字段
ALTER TABLE user_info ADD COLUMN face_image_url VARCHAR(500) COMMENT '人脸照片存储路径';
ALTER TABLE user_info ADD COLUMN face_feature_vector TEXT COMMENT '人脸特征向量(DashScope embedding,逗号分隔浮点数)';
ALTER TABLE user_info ADD COLUMN face_reference_urls TEXT COMMENT '人脸参考图片URL列表(JSON数组)';

-- 2. 为匹配记录表添加用户ID字段(支持人脸识别记录)
ALTER TABLE match_record ADD COLUMN user_id BIGINT COMMENT '匹配的用户ID(人脸识别用)';

-- 3. 创建索引以优化查询
CREATE INDEX idx_user_face ON user_info(face_image_url);
CREATE INDEX idx_match_record_user ON match_record(user_id);
