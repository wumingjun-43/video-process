-- 牛王认证系统 - 数据库初始化脚本
-- 数据库: MySQL 8.0+

CREATE DATABASE IF NOT EXISTS niuwang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE niuwang;

-- 牛王表
CREATE TABLE bull_king (
    id BIGINT PRIMARY KEY COMMENT '主键ID(雪花算法)',
    description VARCHAR(2000) COMMENT '牛王描述',
    battle_record VARCHAR(2000) COMMENT '历史战绩',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除:0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='牛王表';

-- 牛王图片表
CREATE TABLE bull_king_image (
    id BIGINT PRIMARY KEY COMMENT '主键ID(雪花算法)',
    bull_king_id BIGINT NOT NULL COMMENT '关联牛王ID',
    image_url VARCHAR(500) NOT NULL COMMENT '图片存储路径',
    sort_order INT DEFAULT 0 COMMENT '排序序号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (bull_king_id) REFERENCES bull_king(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='牛王图片表';

-- 用户表
CREATE TABLE user_info (
    id BIGINT PRIMARY KEY COMMENT '主键ID(雪花算法)',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    login_name VARCHAR(50) NOT NULL UNIQUE COMMENT '登录名称',
    password VARCHAR(100) NOT NULL COMMENT '加密密码(BCrypt)',
    age INT COMMENT '年龄',
    gender TINYINT DEFAULT 0 COMMENT '性别:0-女 1-男',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 知识图谱文件表
CREATE TABLE knowledge_file (
    id BIGINT PRIMARY KEY COMMENT '主键ID(雪花算法)',
    filename VARCHAR(200) NOT NULL COMMENT '文件名',
    file_type VARCHAR(10) NOT NULL COMMENT '文件类型:pdf/word/excel',
    file_path VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '处理状态:pending/processing/done/error',
    error_msg VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱文件表';

-- 匹配记录表
CREATE TABLE match_record (
    id BIGINT PRIMARY KEY COMMENT '主键ID(雪花算法)',
    bull_king_id BIGINT COMMENT '匹配的牛王ID',
    image_url VARCHAR(500) NOT NULL COMMENT '上传的匹配图片路径',
    confidence_score DECIMAL(5,4) COMMENT '匹配置信度(0-1)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (bull_king_id) REFERENCES bull_king(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匹配记录表';

-- 创建索引
CREATE INDEX idx_bullking_desc ON bull_king(description(500));
CREATE INDEX idx_user_login ON user_info(login_name);
CREATE INDEX idx_match_record_time ON match_record(create_time);
CREATE INDEX idx_knowledge_file_status ON knowledge_file(status);
