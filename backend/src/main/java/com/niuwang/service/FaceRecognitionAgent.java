package com.niuwang.service;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.mapper.UserMapper;
import com.niuwang.model.entity.User;
import com.niuwang.model.vo.FaceMatchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 人脸识别 Agent
 * 基于 insightface 提取 512 维人脸特征向量，存入 PostgreSQL pgvector
 *
 * 工作流程:
 * 1. 注册人脸: 提取特征向量 → 存入 pgvector vector_store 表
 * 2. 人脸匹配: 提取特征 → pgvector 向量搜索 → AI 多模态精排
 * 3. 人脸登录: 提取特征 → pgvector 向量搜索 → JWT 签发
 */
@Slf4j
@Service
public class FaceRecognitionAgent {

    private final FaceEmbeddingService faceEmbeddingService;
    private final VectorStore vectorStore;
    private final UserMapper userMapper;
    private final MatchRecordService matchRecordService;
    private final AiRecognitionService aiRecognitionService;
    private final JdbcTemplate postgresqlJdbcTemplate;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.access-url}")
    private String accessUrl;

    public FaceRecognitionAgent(
            FaceEmbeddingService faceEmbeddingService,
            VectorStore vectorStore,
            UserMapper userMapper,
            MatchRecordService matchRecordService,
            AiRecognitionService aiRecognitionService,
            @Qualifier("postgresqlJdbcTemplate") JdbcTemplate postgresqlJdbcTemplate) {
        this.faceEmbeddingService = faceEmbeddingService;
        this.vectorStore = vectorStore;
        this.userMapper = userMapper;
        this.matchRecordService = matchRecordService;
        this.aiRecognitionService = aiRecognitionService;
        this.postgresqlJdbcTemplate = postgresqlJdbcTemplate;
    }

    // ==================== 注册人脸 ====================

    /**
     * 注册人脸: 提取特征向量并存入 pgvector
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerFace(Long userId, MultipartFile faceImage) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            // 1. 保存人脸图片
            String faceUrl = "/face/" + System.currentTimeMillis() + ".jpg";
            saveImage(faceImage, faceUrl);
            user.setFaceImageUrl(faceUrl);
            userMapper.updateById(user);

            // 2. 提取人脸特征向量
            float[] embedding = faceEmbeddingService.extractFaceEmbedding(faceImage);

            // 3. 存入 pgvector — 先插入文档记录
            Document doc = new Document("face:" + userId);
            doc.getMetadata().put("user_id", userId);
            doc.getMetadata().put("face_image_url", faceUrl);
            doc.getMetadata().put("type", "face");
            vectorStore.add(List.of(doc));

            // 4. 将 embedding 写入 vector_store 表
            String vecStr = faceEmbeddingService.toPgVectorString(embedding);
            postgresqlJdbcTemplate.update(
                    "UPDATE vector_store SET embedding = ?::vector WHERE id = ?",
                    vecStr, doc.getId()
            );

            log.info("人脸注册成功: userId={}, embedding dim={}", userId, embedding.length);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("人脸注册失败: userId={}", userId, e);
            throw new BusinessException("人脸注册失败: " + e.getMessage());
        }
    }

    // ==================== 删除人脸 ====================

    /**
     * 删除人脸: 清理用户人脸图片和向量库记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeFace(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 删除人脸图片
        if (user.getFaceImageUrl() != null && !user.getFaceImageUrl().isEmpty()) {
            String fullPath = uploadPath + user.getFaceImageUrl();
            java.io.File file = new java.io.File(fullPath);
            if (file.exists()) file.delete();
        }
        user.setFaceImageUrl(null);
        userMapper.updateById(user);

        // 删除 pgvector 中的向量记录
        postgresqlJdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'user_id' = ?",
                String.valueOf(userId)
        );

        log.info("人脸已删除: userId={}", userId);
    }

    // ==================== 人脸匹配 ====================

    /**
     * 人脸匹配: 完整匹配流程
     * 1. 提取查询图像特征向量
     * 2. pgvector 向量搜索 Top-K 候选
     * 3. AI 多模态精排
     */
    @Transactional(rollbackFor = Exception.class)
    public FaceMatchResultVO matchFace(MultipartFile image, int candidateCount, double confidenceThreshold) {
        try {
            // Step 1: 提取查询图像的特征向量
            float[] queryVector = faceEmbeddingService.extractFaceEmbedding(image);

            // Step 2: pgvector 向量搜索
            String vecStr = faceEmbeddingService.toPgVectorString(queryVector);
            String sql = """
                    SELECT id, metadata, 1 - (embedding <=> ?::vector) AS similarity
                    FROM vector_store
                    WHERE embedding IS NOT NULL
                      AND metadata->>'type' = 'face'
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?""";

            List<Map<String, Object>> rows = postgresqlJdbcTemplate.queryForList(
                    sql, vecStr, vecStr, candidateCount);

            if (rows.isEmpty()) {
                FaceMatchResultVO result = new FaceMatchResultVO();
                result.setAiAnalysis("系统中暂无注册人脸");
                result.setIsMatch(false);
                result.setConfidenceScore(java.math.BigDecimal.ZERO);
                return result;
            }

            // Step 3: 构建候选列表
            List<AiRecognitionService.FaceCandidate> faceCandidates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String docId = (String) row.get("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) row.get("metadata");
                Long uid = Long.parseLong(metadata.get("user_id").toString());
                String faceUrl = (String) metadata.get("face_image_url");
                Double similarity = (Double) row.get("similarity");

                User user = userMapper.selectById(uid);
                if (user == null) continue;

                faceCandidates.add(new AiRecognitionService.FaceCandidate(
                        uid,
                        user.getName(),
                        faceUrl,
                        null,
                        similarity
                ));
            }

            // Step 4: AI 多模态精排
            // 保存匹配图片
            String matchImageUrl = "/face-match/" + System.currentTimeMillis() + ".jpg";
            try {
                saveImage(image, matchImageUrl);
            } catch (Exception e) {
                // ignore
            }

            FaceMatchResultVO result;
            if (!faceCandidates.isEmpty()) {
                result = aiRecognitionService.matchFaceByAI(image, faceCandidates);
            } else {
                result = new FaceMatchResultVO();
                result.setAiAnalysis("未找到任何候选用户");
                result.setIsMatch(false);
                result.setConfidenceScore(java.math.BigDecimal.ZERO);
            }

            // Step 5: 填充候选信息
            result.setImageUrl(accessUrl + matchImageUrl);
            if (result.getCandidates() == null || result.getCandidates().isEmpty()) {
                result.setCandidates(faceCandidates.stream().map(c -> {
                    FaceMatchResultVO.CandidateInfo info = new FaceMatchResultVO.CandidateInfo();
                    info.setUserId(c.getUserId());
                    info.setUserName(c.getUserName());
                    info.setFaceImageUrl(accessUrl + c.getFaceImageUrl());
                    info.setCosineSimilarity(java.math.BigDecimal.valueOf(c.getCosineSimilarity()));
                    return info;
                }).toList());
            }

            // Step 6: 补充用户详细信息
            if (result.getUserId() != null && result.getUserId() > 0) {
                User matchedUser = userMapper.selectById(result.getUserId());
                if (matchedUser != null) {
                    result.setUserName(matchedUser.getName());
                    result.setLoginName(matchedUser.getLoginName());
                }
            }

            // Step 7: 保存匹配记录
            matchRecordService.saveFaceMatchRecord(
                    result.getUserId() != null ? result.getUserId() : 0L,
                    matchImageUrl,
                    result.getConfidenceScore() != null ? result.getConfidenceScore().doubleValue() : 0.0);

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("人脸匹配失败: {}", e.getMessage(), e);
            throw new BusinessException("人脸匹配失败: " + e.getMessage());
        }
    }

    /**
     * 提取人脸特征向量（供 FaceAuthController 使用）
     */
    public float[] extractFaceEmbedding(MultipartFile image) {
        return faceEmbeddingService.extractFaceEmbedding(image);
    }

    /**
     * 根据用户ID查询用户（供 FaceAuthController 使用）
     */
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 搜索最相似的人脸（供 FaceAuthController 使用）
     */
    public Map<String, Object> searchNearestFace(float[] queryVector, int topK) {
        String vecStr = faceEmbeddingService.toPgVectorString(queryVector);
        String sql = """
                SELECT id, metadata, 1 - (embedding <=> ?::vector) AS similarity
                FROM vector_store
                WHERE embedding IS NOT NULL
                  AND metadata->>'type' = 'face'
                ORDER BY embedding <=> ?::vector
                LIMIT 1""";

        List<Map<String, Object>> rows = postgresqlJdbcTemplate.queryForList(sql, vecStr, vecStr);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    /**
     * 保存人脸图片到本地磁盘
     */
    private void saveImage(MultipartFile image, String faceUrl) throws Exception {
        java.io.File dir = new java.io.File(uploadPath + faceUrl).getParentFile();
        if (!dir.exists()) dir.mkdirs();
        image.transferTo(new java.io.File(uploadPath + faceUrl));
    }
}
