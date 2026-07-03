package com.niuwang.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户新增/更新请求DTO
 */
@Data
public class UserDTO {

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50个字符")
    private String name;

    /** 登录名称 */
    @NotBlank(message = "登录名称不能为空")
    @Size(max = 50, message = "登录名称不能超过50个字符")
    private String loginName;

    /** 密码 */
    @Size(min = 6, max = 50, message = "密码长度不能少于6个字符")
    private String password;

    /** 年龄 */
    @NotNull(message = "年龄不能为空")
    private Integer age;

    /** 性别: 0-女 1-男 */
    private Integer gender;
}
