package com.niuwang.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 牛王特征分析VO
 */
@Getter @Setter
public class FeatureAnalysisVO {

    /** 牛角特征描述 */
    private String hornFeatures;

    /** 牛头特征描述 */
    private String headFeatures;

    /** 牛眼特征描述 */
    private String eyeFeatures;

    /** 毛发特征描述 */
    private String furFeatures;

    /** 身上牛旋特征描述 */
    private String swirlFeatures;

    /** 牛脚特征描述 */
    private String legFeatures;

    /** AI综合分析结果 */
    private String aiSummary;

    /** 参考的知识图谱文件ID列表 */
    private List<Long> knowledgeFileIds;
}
