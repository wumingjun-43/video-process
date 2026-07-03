package com.niuwang.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginDTO {

    @NotBlank(message = "登录名称不能为空")
    private String loginName;

    @NotBlank(message = "密码不能为空")
    private String password;
}
