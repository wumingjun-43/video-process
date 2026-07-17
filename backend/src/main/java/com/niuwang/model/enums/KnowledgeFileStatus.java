package com.niuwang.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 知识文件处理状态枚举
 */
@Getter
public enum KnowledgeFileStatus {

    pending("pending", "待处理"),
    processing("processing", "处理中"),
    done("done", "已完成"),
    error("error", "失败");

    @EnumValue
    private final String code;
    private final String description;

    KnowledgeFileStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static KnowledgeFileStatus fromCode(String code) {
        for (KnowledgeFileStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
