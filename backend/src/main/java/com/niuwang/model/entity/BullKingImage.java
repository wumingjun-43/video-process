package com.niuwang.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 牛王图片实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("bull_king_image")
public class BullKingImage extends BaseEntity {

    /** 关联牛王ID */
    private Long bullKingId;

    /** 图片存储路径 */
    private String imageUrl;

    /** 排序序号 */
    private Integer sortOrder;
}
