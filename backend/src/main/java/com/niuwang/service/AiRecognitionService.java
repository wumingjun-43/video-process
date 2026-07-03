package com.niuwang.service;

import com.niuwang.model.dto.BullKingMatchDTO;
import com.niuwang.model.vo.FeatureAnalysisVO;
import com.niuwang.model.vo.MatchResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 识别服务接口
 * 基于 spring-ai-alibaba 进行图片识别和知识图谱分析
 */
public interface AiRecognitionService {

    /** 图片匹配牛王 */
    MatchResultVO matchBullKing(BullKingMatchDTO dto);

    /** 牛王特征分析 */
    FeatureAnalysisVO analyzeFeatures(Long knowledgeFileId);
}
