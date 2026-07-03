package com.niuwang.controller;

import com.niuwang.common.response.PageResult;
import com.niuwang.common.response.Result;
import com.niuwang.model.dto.UserDTO;
import com.niuwang.model.vo.UserInfoVO;
import com.niuwang.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public Result<Long> addUser(@Valid @RequestBody UserDTO dto) {
        return Result.success(userService.addUser(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        userService.updateUser(id, dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<UserInfoVO> getUserDetail(@PathVariable Long id) {
        return Result.success(userService.getUserDetail(id));
    }

    @GetMapping
    public Result<PageResult<UserInfoVO>> pageUser(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(userService.pageUser(keyword, page, size));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }
}
