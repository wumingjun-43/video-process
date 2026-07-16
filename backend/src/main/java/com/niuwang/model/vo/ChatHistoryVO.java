package com.niuwang.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.niuwang.common.serializer.LongToStringSerializer;
import lombok.Data;

/**
 * 对话历史 VO
 */
@Data
public class ChatHistoryVO {
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    private String question;
    private String answer;
    private String knowledgeFileIds;
    private java.time.LocalDateTime createTime;
}
