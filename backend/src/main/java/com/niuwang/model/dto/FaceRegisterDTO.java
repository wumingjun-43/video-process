package com.niuwang.model.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸注册请求DTO
 */
@Data
public class FaceRegisterDTO {

    /** 目标用户ID */
    private Long userId;

    /** 人脸照片 */
    private MultipartFile image;

    /** 兼容前端使用 faceImage 字段名 */
    public MultipartFile getFaceImage() {
        return image;
    }

    public void setFaceImage(MultipartFile faceImage) {
        this.image = faceImage;
    }
}
