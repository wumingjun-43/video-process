package com.niuwang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuwang.common.exception.BusinessException;
import com.niuwang.common.response.PageResult;
import com.niuwang.mapper.UserMapper;
import com.niuwang.model.dto.UserDTO;
import com.niuwang.model.entity.User;
import com.niuwang.model.vo.FaceMatchResultVO;
import com.niuwang.model.vo.UserInfoVO;
import com.niuwang.service.AiRecognitionService;
import com.niuwang.service.MatchRecordService;
import com.niuwang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final AiRecognitionService aiRecognitionService;
    private final MatchRecordService matchRecordService;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.access-url}")
    private String accessUrl;

    @Value("${face.matching.candidate-count:5}")
    private int candidateCount;

    @Value("${face.matching.confidence-threshold:0.75}")
    private double confidenceThreshold;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addUser(UserDTO dto) {
        // 检查登录名是否已存在
        long count = count(new LambdaQueryWrapper<User>().eq(User::getLoginName, dto.getLoginName()));
        if (count > 0) {
            throw new BusinessException("登录名称已存在");
        }

        User user = new User();
        user.setName(dto.getName());
        user.setLoginName(dto.getLoginName());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setAge(dto.getAge());
        user.setGender(dto.getGender());
        save(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserDTO dto) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setName(dto.getName());
        user.setLoginName(dto.getLoginName());
        user.setAge(dto.getAge());
        user.setGender(dto.getGender());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        updateById(user);
    }

    @Override
    public UserInfoVO getUserDetail(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toUserInfoVO(user);
    }

    @Override
    public PageResult<UserInfoVO> pageUser(String keyword, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(User::getName, keyword)
                    .or().like(User::getLoginName, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> pageParam = new Page<>(page, size);
        Page<User> result = page(pageParam, wrapper);

        List<UserInfoVO> voList = result.getRecords().stream()
                .map(this::toUserInfoVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerFace(Long userId, MultipartFile faceImage) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 1. 保存人脸图片
        String faceUrl = "/face/" + System.currentTimeMillis() + ".jpg";
        try {
            File dir = new File(uploadPath + faceUrl).getParentFile();
            if (!dir.exists()) dir.mkdirs();
            faceImage.transferTo(new File(uploadPath + faceUrl));
        } catch (Exception e) {
            throw new BusinessException("人脸图片保存失败: " + e.getMessage());
        }

        // 2. 删除旧的人脸图片(如果有)
        if (user.getFaceImageUrl() != null && !user.getFaceImageUrl().isEmpty()) {
            File oldFile = new File(uploadPath + user.getFaceImageUrl());
            if (oldFile.exists()) oldFile.delete();
        }

        // 3. 提取人脸特征向量
        float[] embedding = aiRecognitionService.extractFaceEmbedding(faceImage);
        String featureVectorStr = Arrays.toString(embedding)
                .replace("[", "").replace("]", "");

        // 4. 更新用户人脸信息
        user.setFaceImageUrl(faceUrl);
        user.setFaceFeatureVector(featureVectorStr);
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFace(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 删除旧的人脸图片
        if (user.getFaceImageUrl() != null && !user.getFaceImageUrl().isEmpty()) {
            File oldFile = new File(uploadPath + user.getFaceImageUrl());
            if (oldFile.exists()) oldFile.delete();
        }

        user.setFaceImageUrl(null);
        user.setFaceFeatureVector(null);
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaceMatchResultVO matchFace(MultipartFile image) {
        try {
            // Step 1: 提取查询图像的特征向量
            float[] queryVector = aiRecognitionService.extractFaceEmbedding(image);

            // Step 2: 加载所有已注册用户人脸
            List<User> registeredUsers = list(new LambdaQueryWrapper<User>()
                    .isNotNull(User::getFaceFeatureVector)
                    .ne(User::getFaceFeatureVector, "")
                    .orderByDesc(User::getCreateTime));

            if (registeredUsers.isEmpty()) {
                throw new BusinessException("数据库中没有人脸注册数据");
            }

            // Step 3: 计算余弦相似度，筛选候选
            List<AiRecognitionService.FaceCandidate> candidates = new ArrayList<>();
            for (User user : registeredUsers) {
                float[] storedVector = parseVectorString(user.getFaceFeatureVector());
                double similarity = cosineSimilarity(queryVector, storedVector);
                if (similarity > 0.3) { // 低阈值过滤，避免漏掉可能的匹配
                    candidates.add(new AiRecognitionService.FaceCandidate(
                            user.getId(),
                            user.getName(),
                            user.getFaceImageUrl(),
                            storedVector,
                            similarity));
                }
            }

            // 按相似度降序排序
            candidates.sort((a, b) -> Double.compare(b.getCosineSimilarity(), a.getCosineSimilarity()));

            // 取 Top-K
            int topK = Math.min(candidateCount, candidates.size());
            List<AiRecognitionService.FaceCandidate> topCandidates = candidates.subList(0, topK);

            // Step 4: 保存匹配图片
            String matchImageUrl = "/face-match/" + System.currentTimeMillis() + ".jpg";
            try {
                File dir = new File(uploadPath + matchImageUrl).getParentFile();
                if (!dir.exists()) dir.mkdirs();
                image.transferTo(new File(uploadPath + matchImageUrl));
            } catch (Exception e) { /* ignore */ }

            // Step 5: 如果有候选，进行AI多模态精排
            FaceMatchResultVO result;
            if (!topCandidates.isEmpty()) {
                result = aiRecognitionService.matchFaceByAI(image, topCandidates);
            } else {
                result = new FaceMatchResultVO();
                result.setAiAnalysis("未找到任何候选用户");
                result.setIsMatch(false);
                result.setConfidenceScore(BigDecimal.ZERO);
            }

            // Step 6: 填充候选信息和匹配图片路径
            result.setImageUrl(accessUrl + matchImageUrl);
            if (result.getCandidates() == null || result.getCandidates().isEmpty()) {
                result.setCandidates(topCandidates.stream().map(c -> {
                    FaceMatchResultVO.CandidateInfo info = new FaceMatchResultVO.CandidateInfo();
                    info.setUserId(c.getUserId());
                    info.setUserName(c.getUserName());
                    info.setFaceImageUrl(accessUrl + c.getFaceImageUrl());
                    info.setCosineSimilarity(new BigDecimal(c.getCosineSimilarity()));
                    return info;
                }).collect(Collectors.toList()));
            }

            // Step 7: 如果AI匹配到了用户，补充用户详细信息
            if (result.getUserId() != null && result.getUserId() > 0) {
                User matchedUser = getById(result.getUserId());
                if (matchedUser != null) {
                    result.setUserName(matchedUser.getName());
                    result.setLoginName(matchedUser.getLoginName());
                }
            }

            // Step 8: 保存匹配记录
            matchRecordService.saveFaceMatchRecord(
                    result.getUserId() != null ? result.getUserId() : 0L,
                    matchImageUrl,
                    result.getConfidenceScore() != null ? result.getConfidenceScore().doubleValue() : 0.0);

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("人脸匹配失败: " + e.getMessage());
        }
    }

    /** 解析向量字符串为 float[] */
    private float[] parseVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.trim().isEmpty()) {
            return new float[512];
        }
        String cleaned = vectorStr.replace(" ", "").replace("[", "").replace("]", "");
        String[] parts = cleaned.split(",");
        float[] vector = new float[Math.min(parts.length, 512)];
        for (int i = 0; i < vector.length; i++) {
            try {
                vector[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                vector[i] = 0f;
            }
        }
        return vector;
    }

    /** 计算余弦相似度 */
    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private UserInfoVO toUserInfoVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setName(user.getName());
        vo.setLoginName(user.getLoginName());
        vo.setAge(user.getAge());
        vo.setGender(user.getGender());
        vo.setGenderText(user.getGender() != null && user.getGender() == 1 ? "男" : "女");
        vo.setHasFace(user.getFaceImageUrl() != null && !user.getFaceImageUrl().isEmpty());
        vo.setFaceImageUrl(user.getFaceImageUrl());
        return vo;
    }
}
