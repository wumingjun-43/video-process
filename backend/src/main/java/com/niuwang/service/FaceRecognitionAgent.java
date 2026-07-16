package com.niuwang.service;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.mapper.UserMapper;
import com.niuwang.model.entity.User;
import com.niuwang.model.vo.FaceMatchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人脸识别 Agent
 * 基于 insightface 提取 1024 维人脸特征向量，存入 PostgreSQL pgvector
 */
@Slf4j
@Service
public class FaceRecognitionAgent {

    private final FaceEmbeddingService faceEmbeddingService;
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
            UserMapper userMapper,
            MatchRecordService matchRecordService,
            AiRecognitionService aiRecognitionService,
            @Qualifier("postgresqlJdbcTemplate") JdbcTemplate postgresqlJdbcTemplate) {
        this.faceEmbeddingService = faceEmbeddingService;
        this.userMapper = userMapper;
        this.matchRecordService = matchRecordService;
        this.aiRecognitionService = aiRecognitionService;
        this.postgresqlJdbcTemplate = postgresqlJdbcTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public void registerFace(Long userId, File faceFile, String faceUrl) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            // 1. 提取 1024 维人脸特征向量
            float[] embedding = faceEmbeddingService.extractFaceEmbedding(faceFile);

            // 2. 删除旧记录（如果有）
            postgresqlJdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'user_id' = ?",
                    String.valueOf(userId));

            // 3. 更新用户人脸图片路径（先更新 DB，再读新值）
            user.setFaceImageUrl(faceUrl);
            userMapper.updateById(user);

            // 4. 构建 metadata JSON 字符串（防止 JSON 注入）
            String safeImageUrl = faceUrl.replace("\\", "\\\\").replace("\"", "\\\"");
            String metadataJson = String.format(
                    "{\"user_id\":%d,\"face_image_url\":\"%s\",\"type\":\"face\"}",
                    userId, safeImageUrl);

            // 5. 使用 PreparedStatementCreator 显式设置 jsonb 和 vector 类型
            String sql = "INSERT INTO vector_store (id, content, metadata, embedding) "
                    + "VALUES (gen_random_uuid(), ?, ?, ?)";
            postgresqlJdbcTemplate.update((java.sql.Connection conn) -> {
                java.sql.PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, "face:" + userId);

                // metadata 需要设为 jsonb 类型
                org.postgresql.util.PGobject pgMetadata = new org.postgresql.util.PGobject();
                pgMetadata.setType("jsonb");
                pgMetadata.setValue(metadataJson);
                ps.setObject(2, pgMetadata);

                // embedding 需要设为 vector 类型
                org.postgresql.util.PGobject pgVector = new org.postgresql.util.PGobject();
                pgVector.setType("vector");
                pgVector.setValue(faceEmbeddingService.toPgVectorString(embedding));
                ps.setObject(3, pgVector);

                return ps;
            });

            log.info("人脸注册成功: userId={}, embedding dim={}", userId, embedding.length);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("人脸注册失败: userId={}", userId, e);
            throw new BusinessException("人脸注册失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeFace(Long userId) {
        postgresqlJdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'user_id' = ?",
                String.valueOf(userId));
        log.info("人脸已删除: userId={}", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public FaceMatchResultVO matchFace(MultipartFile image, int candidateCount, double confidenceThreshold) {
        try {
            // 1. 提取特征向量
            float[] queryVector = faceEmbeddingService.extractFaceEmbedding(image);

            // 2. pgvector 向量搜索（使用 PGobject 显式绑定 vector 类型）
            org.postgresql.util.PGobject queryVecObj = new org.postgresql.util.PGobject();
            queryVecObj.setType("vector");
            queryVecObj.setValue(faceEmbeddingService.toPgVectorString(queryVector));

            String sql = "SELECT id, metadata, 1 - (embedding <=> ?::vector) AS similarity "
                    + "FROM vector_store WHERE embedding IS NOT NULL "
                    + "AND metadata->>'type' = 'face' "
                    + "ORDER BY embedding <=> ?::vector LIMIT ?";

            List<Map<String, Object>> rows = postgresqlJdbcTemplate.queryForList(sql,
                    new Object[]{queryVecObj, queryVecObj, candidateCount});

            if (rows.isEmpty()) {
                FaceMatchResultVO result = new FaceMatchResultVO();
                result.setAiAnalysis("系统中暂无注册人脸");
                result.setIsMatch(false);
                result.setConfidenceScore(BigDecimal.ZERO);
                return result;
            }

            // 3. 构建候选列表
            List<AiRecognitionService.FaceCandidate> faceCandidates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = parseMetadata(row.get("metadata"));
                Long uid = Long.parseLong(metadata.get("user_id").toString());
                String faceUrl = (String) metadata.get("face_image_url");
                Double similarity = (Double) row.get("similarity");

                User user = userMapper.selectById(uid);
                if (user == null) continue;

                faceCandidates.add(new AiRecognitionService.FaceCandidate(
                        uid, user.getName(), faceUrl, null, similarity));
            }

            // 4. 保存匹配图片
            String matchImageUrl = "/face-match/" + System.currentTimeMillis() + ".jpg";
            try {
                File dir = new File(uploadPath + matchImageUrl).getParentFile();
                if (!dir.exists()) dir.mkdirs();
                image.transferTo(new File(uploadPath + matchImageUrl));
            } catch (Exception e) {
                // ignore
            }

            // 5. AI 多模态精排
            FaceMatchResultVO result;
            if (!faceCandidates.isEmpty()) {
                result = aiRecognitionService.matchFaceByAI(image, faceCandidates);
            } else {
                result = new FaceMatchResultVO();
                result.setAiAnalysis("未找到任何候选用户");
                result.setIsMatch(false);
                result.setConfidenceScore(BigDecimal.ZERO);
            }

            // 6. 填充候选信息
            result.setImageUrl(accessUrl + matchImageUrl);
            if (result.getCandidates() == null || result.getCandidates().isEmpty()) {
                result.setCandidates(faceCandidates.stream().map(c -> {
                    FaceMatchResultVO.CandidateInfo info = new FaceMatchResultVO.CandidateInfo();
                    info.setUserId(c.getUserId());
                    info.setUserName(c.getUserName());
                    info.setFaceImageUrl(accessUrl + c.getFaceImageUrl());
                    info.setCosineSimilarity(BigDecimal.valueOf(c.getCosineSimilarity()));
                    return info;
                }).collect(Collectors.toList()));
            }

            // 7. 补充用户详细信息
            if (result.getUserId() != null && result.getUserId() > 0) {
                User matchedUser = userMapper.selectById(result.getUserId());
                if (matchedUser != null) {
                    result.setUserName(matchedUser.getName());
                    result.setLoginName(matchedUser.getLoginName());
                }
            }

            // 8. 保存匹配记录
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

    public float[] extractFaceEmbedding(File imageFile) {
        try {
            byte[] bytes = Files.readAllBytes(imageFile.toPath());
            return faceEmbeddingService.extractFaceEmbedding(bytes);
        } catch (Exception e) {
            throw new BusinessException("人脸特征提取失败: " + e.getMessage());
        }
    }

    public float[] extractFaceEmbedding(MultipartFile image) {
        try {
            return faceEmbeddingService.extractFaceEmbedding(image);
        } catch (Exception e) {
            throw new BusinessException("人脸特征提取失败: " + e.getMessage());
        }
    }

    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    public Map<String, Object> searchNearestFace(float[] queryVector) {
        try {
            org.postgresql.util.PGobject queryVecObj = new org.postgresql.util.PGobject();
            queryVecObj.setType("vector");
            queryVecObj.setValue(faceEmbeddingService.toPgVectorString(queryVector));

            String sql = "SELECT id, metadata, 1 - (embedding <=> ?::vector) AS similarity "
                    + "FROM vector_store WHERE embedding IS NOT NULL "
                    + "AND metadata->>'type' = 'face' "
                    + "ORDER BY embedding <=> ?::vector LIMIT 1";

            List<Map<String, Object>> rows = postgresqlJdbcTemplate.queryForList(sql,
                    new Object[]{queryVecObj, queryVecObj});
            if (rows.isEmpty()) return null;

            // 解析 metadata 列（PostgreSQL 返回为 PGobject，需转为 Map）
            Map<String, Object> row = rows.get(0);
            Object metadataObj = row.get("metadata");
            if (metadataObj != null && !(metadataObj instanceof Map)) {
                row = new LinkedHashMap<>(row);
                row.put("metadata", parseMetadata(metadataObj));
            }
            return row;
        } catch (SQLException e) {
            throw new BusinessException("人脸向量搜索失败: " + e.getMessage());
        }
    }

    /**
     * 解析 PostgreSQL 返回的 jsonb 类型为 Map
     * pgvector JDBC 驱动返回 jsonb 列为 org.postgresql.util.PGobject
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object metadataObj) {
        if (metadataObj instanceof Map) {
            return (Map<String, Object>) metadataObj;
        }
        // PostgreSQL jsonb 返回为 PGobject，需要手动解析
        if (metadataObj != null) {
            String json = metadataObj.toString();
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, Map.class);
            } catch (Exception e) {
                log.warn("解析 metadata 失败: {}", json, e);
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }
}
