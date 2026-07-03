package com.niuwang.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 知识图谱文件实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("knowledge_file")
public class KnowledgeFile extends BaseEntity {

    /** 文件名 */
    private String filename;

    /** 文件类型: pdf/word/excel */
    private String fileType;

    /** 文件存储路径 */
    private String filePath;

    /** 处理状态: pending/processing/done/error */
    private String status;

    /** 错误信息 */
    private String errorMsg;
}
