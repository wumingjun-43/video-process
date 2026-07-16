package com.niuwang.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AskDTO {
    /**
     * 请求内容
     */
    private String question;

    /**
     * 选择的知识图谱id
     */
    private List<Long> knowledgeFileIds;
}
