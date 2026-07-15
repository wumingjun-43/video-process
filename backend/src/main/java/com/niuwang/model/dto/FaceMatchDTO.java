package com.niuwang.model.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸匹配请求DTO
 */
@Data
public class FaceMatchDTO {

    /** 待匹配的人脸照片 */
    private MultipartFile image;
}
