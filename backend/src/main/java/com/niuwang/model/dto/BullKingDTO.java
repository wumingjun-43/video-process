package com.niuwang.model.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 新增/更新牛王请求DTO
 */
@Data
public class BullKingDTO {

    /** 牛王描述 */
    private String description;

    /** 历史战绩 */
    private String battleRecord;

    /** 新增上传的图片文件 */
    private MultipartFile[] images;

    /** 保留的旧图片 URL 列表（编辑时使用） */
    private List<String> retainedUrls;
}
