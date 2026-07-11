package com.niuwang.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.niuwang.common.serializer.LongToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人脸匹配结果VO
 */
@Data
public class FaceMatchResultVO {

    /** 匹配到的用户ID */
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long userId;

    /** 用户姓名 */
    private String userName;

    /** 登录名称 */
    private String loginName;

    /** 匹配置信度(0-1) */
    private BigDecimal confidenceScore;

    /** AI分析说明 */
    private String aiAnalysis;

    /** 是否匹配成功 */
    private Boolean isMatch;

    /** 上传的匹配图片路径 */
    private String imageUrl;

    /** 候选用户列表(供参考) */
    private java.util.List<CandidateInfo> candidates;

    /** 候选用户信息 */
    @Data
    public static class CandidateInfo {
        @JsonSerialize(using = LongToStringSerializer.class)
        private Long userId;
        private String userName;
        private String faceImageUrl;
        private BigDecimal cosineSimilarity;
    }
}
