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
import com.niuwang.model.vo.UserInfoVO;
import com.niuwang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

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

    private UserInfoVO toUserInfoVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setName(user.getName());
        vo.setLoginName(user.getLoginName());
        vo.setAge(user.getAge());
        vo.setGender(user.getGender());
        vo.setGenderText(user.getGender() != null && user.getGender() == 1 ? "男" : "女");
        return vo;
    }
}
