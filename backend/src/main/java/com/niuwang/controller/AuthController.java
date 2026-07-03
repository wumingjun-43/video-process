package com.niuwang.controller;

import com.niuwang.common.response.Result;
import com.niuwang.model.dto.LoginDTO;
import com.niuwang.model.entity.User;
import com.niuwang.security.JwtUtil;
import com.niuwang.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        User user = userService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getLoginName, dto.getLoginName()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getLoginName());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("userName", user.getName());
        return Result.success("登录成功", data);
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success("退出成功");
    }
}
