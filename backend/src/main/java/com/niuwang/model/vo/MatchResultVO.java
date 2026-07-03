package com.niuwang.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.niuwang.common.serializer.LongToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 牛王匹配结果VO
 */
@Data
public class MatchResultVO {

    @JsonSerialize(using = LongToStringSerializer.class)
    private Long bullKingId;

    private String description;
    private String battleRecord;
    private java.util.List<String> images;
    private BigDecimal confidenceScore;
    private String aiAnalysis;
}
