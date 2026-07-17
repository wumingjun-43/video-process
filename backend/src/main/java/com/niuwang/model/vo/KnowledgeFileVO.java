package com.niuwang.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.niuwang.common.serializer.LongToStringSerializer;
import com.niuwang.model.enums.KnowledgeFileStatus;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识图谱文件VO
 */
@Data
public class KnowledgeFileVO {
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    private String filename;
    private String fileType;
    private String status;
    private String errorMsg;
    private LocalDateTime createTime;
}
