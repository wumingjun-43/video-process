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
import com.niuwang.service.FaceRecognitionAgent;
import com.niuwang.service.MatchRecordService;
import com.niuwang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 人脸注册/删除/匹配委托给 FaceRecognitionAgent 处理
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final FaceRecognitionAgent faceRecognitionAgent;
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

        // 3. 更新用户人脸图片路径
        user.setFaceImageUrl(faceUrl);
        updateById(user);

        // 4. 委托给 Agent 处理特征提取和向量入库
        faceRecognitionAgent.registerFace(userId, faceImage);
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
        updateById(user);

        // 委托给 Agent 清理向量库
        faceRecognitionAgent.removeFace(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaceMatchResultVO matchFace(MultipartFile image) {
        // 委托给 Agent 处理完整的人脸匹配流程（向量搜索 + AI精排）
        return faceRecognitionAgent.matchFace(image, candidateCount, confidenceThreshold);
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
