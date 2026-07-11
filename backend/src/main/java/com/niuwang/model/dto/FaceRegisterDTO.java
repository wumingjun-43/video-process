package com.niuwang.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸注册请求DTO
 */
@Data
public class FaceRegisterDTO {

    /** 目标用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 人脸照片 */
    @NotEmpty(message = "人脸照片不能为空")
    private MultipartFile faceImage;
}
