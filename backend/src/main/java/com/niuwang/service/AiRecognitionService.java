package com.niuwang.service;

import com.niuwang.model.dto.BullKingMatchDTO;
import com.niuwang.model.vo.FaceAnalysisResult;
import com.niuwang.model.vo.FaceMatchResultVO;
import com.niuwang.model.vo.FeatureAnalysisVO;
import com.niuwang.model.vo.MatchResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI 识别服务接口
 * 基于 spring-ai-alibaba 进行图片识别和知识图谱分析
 */
public interface AiRecognitionService {

    /** 图片匹配牛王 */
    MatchResultVO matchBullKing(BullKingMatchDTO dto);

    /** 牛王特征分析 */
    FeatureAnalysisVO analyzeFeatures(Long knowledgeFileId);

    /** 从人脸图片中提取特征向量(DashScope embedding) */
    float[] extractFaceEmbedding(MultipartFile image);

    /** AI多模态精排: 基于Top-K候选做最终人脸比对 */
    FaceMatchResultVO matchFaceByAI(MultipartFile queryImage, List<FaceCandidate> candidates);

    /** 人脸质量分析 */
    FaceAnalysisResult analyzeFace(MultipartFile image);

    /** 候选用户信息 */
    class FaceCandidate {
        private Long userId;
        private String userName;
        private String faceImageUrl;
        private float[] featureVector;
        private double cosineSimilarity;

        public FaceCandidate() {}

        public FaceCandidate(Long userId, String userName, String faceImageUrl, float[] featureVector, double cosineSimilarity) {
            this.userId = userId;
            this.userName = userName;
            this.faceImageUrl = faceImageUrl;
            this.featureVector = featureVector;
            this.cosineSimilarity = cosineSimilarity;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getFaceImageUrl() { return faceImageUrl; }
        public void setFaceImageUrl(String faceImageUrl) { this.faceImageUrl = faceImageUrl; }
        public float[] getFeatureVector() { return featureVector; }
        public void setFeatureVector(float[] featureVector) { this.featureVector = featureVector; }
        public double getCosineSimilarity() { return cosineSimilarity; }
        public void setCosineSimilarity(double cosineSimilarity) { this.cosineSimilarity = cosineSimilarity; }
    }
}
