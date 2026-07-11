package com.niuwang.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 匹配记录实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("match_record")
public class MatchRecord extends BaseEntity {

    /** 匹配的牛王ID */
    private Long bullKingId;

    /** 匹配的用户ID(人脸识别用) */
    private Long userId;

    /** 上传的匹配图片路径 */
    private String imageUrl;

    /** 匹配置信度(0-1) */
    private BigDecimal confidenceScore;
}
