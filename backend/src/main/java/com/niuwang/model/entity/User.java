package com.niuwang.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 用户实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("user_info")
public class User extends BaseEntity {

    /** 姓名 */
    private String name;

    /** 登录名称 */
    private String loginName;

    /** 加密密码(BCrypt) */
    private String password;

    /** 年龄 */
    private Integer age;

    /** 性别: 0-女 1-男 */
    private Integer gender;
}
