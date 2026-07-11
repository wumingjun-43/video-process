package com.niuwang.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸匹配请求DTO
 */
@Data
public class FaceMatchDTO {

    /** 待匹配的人脸照片 */
    @NotEmpty(message = "匹配图片不能为空")
    private MultipartFile image;
}
