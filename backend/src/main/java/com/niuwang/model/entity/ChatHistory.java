package com.niuwang.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 智能对话历史记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("chat_history")
public class ChatHistory extends BaseEntity {

    /** 用户提问 */
    private String question;

    /** AI 回答 */
    private String answer;

    /** 使用的知识文件 ID 列表（逗号分隔） */
    private String knowledgeFileIds;
}
