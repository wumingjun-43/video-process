package com.niuwang.controller;

import com.niuwang.common.response.Result;
import com.niuwang.model.vo.LoginVO;
import com.niuwang.security.JwtUtil;
import com.niuwang.service.FaceRecognitionAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人脸认证控制器
 * 提供人脸登录功能
 * 流程: 提取特征 → pgvector 向量搜索 → 签发 JWT
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class FaceAuthController {

    private final FaceRecognitionAgent faceRecognitionAgent;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate postgresqlJdbcTemplate;

    /**
     * 人脸登录
     * 流程: 提取特征 → pgvector 向量搜索 → 签发 JWT
     */
    @PostMapping("/face-login")
    public Result<Map<String, Object>> faceLogin(@RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return Result.error(400, "请上传人脸照片");
        }

        try {
            // Step 1: 提取人脸特征向量
            float[] queryVector = faceRecognitionAgent.extractFaceEmbedding(image);

            // Step 2: 搜索最相似的人脸
            Map<String, Object> searchResult = faceRecognitionAgent.searchNearestFace(queryVector, 1);

            if (searchResult == null) {
                return Result.error(401, "未识别到已注册人脸，请联系管理员注册");
            }

            // Step 3: 获取匹配用户信息
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) searchResult.get("metadata");
            Long matchedUserId = Long.parseLong(metadata.get("user_id").toString());
            Double similarity = (Double) searchResult.get("similarity");

            // 检查相似度阈值
            if (similarity < 0.75) {
                return Result.error(401, "人脸相似度不足，请重试或改用密码登录");
            }

            // Step 4: 查询用户并生成 JWT
            var user = faceRecognitionAgent.getUserById(matchedUserId);
            if (user == null) {
                return Result.error(401, "匹配用户不存在");
            }

            String token = jwtUtil.generateToken(user.getId(), user.getLoginName());
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("userName", user.getName());
            data.put("loginType", "face");
            data.put("similarity", similarity);
            return Result.success("人脸登录成功", data);

        } catch (Exception e) {
            return Result.error("人脸登录失败: " + e.getMessage());
        }
    }
}
