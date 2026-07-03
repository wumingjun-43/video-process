package com.niuwang.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 牛王实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("bull_king")
public class BullKing extends BaseEntity {

    /** 牛王描述 */
    private String description;

    /** 历史战绩 */
    private String battleRecord;
}
