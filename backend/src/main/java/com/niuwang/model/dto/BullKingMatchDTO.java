package com.niuwang.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 牛王匹配请求DTO
 */
@Data
public class BullKingMatchDTO {

    /** 匹配图片 */
    @NotEmpty(message = "匹配图片不能为空")
    private MultipartFile image;

    /** 知识图谱文件ID(可选, 为null则使用全部知识图谱) */
    private Long knowledgeFileId;
}
