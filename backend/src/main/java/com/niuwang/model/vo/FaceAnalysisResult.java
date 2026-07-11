package com.niuwang.model.vo;

import lombok.Data;

/**
 * 人脸分析结果VO
 */
@Data
public class FaceAnalysisResult {

    /** 检测到的人脸数量 */
    private Integer faceCount;

    /** 是否有清晰正面人脸 */
    private Boolean isClearFrontal;

    /** 人脸角度评估 */
    private String poseEvaluation;

    /** 光照条件评估 */
    private String lightingEvaluation;

    /** 综合质量评分(0-1) */
    private Double qualityScore;

    /** AI分析说明 */
    private String analysis;
}
