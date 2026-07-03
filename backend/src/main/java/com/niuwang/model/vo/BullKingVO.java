package com.niuwang.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.niuwang.common.serializer.LongToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 牛王详情VO
 */
@Data
public class BullKingVO {

    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;

    private String description;
    private String battleRecord;
    private List<BullKingImageVO> images;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Data
    public static class BullKingImageVO {
        @JsonSerialize(using = LongToStringSerializer.class)
        private Long id;
        private String imageUrl;
        private Integer sortOrder;
    }
}
