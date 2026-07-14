package com.niuwang.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.niuwang.common.serializer.LongToStringSerializer;
import lombok.Data;

/**
 * 登录响应VO
 */
@Data
public class LoginVO {

    /** JWT Token */
    private String token;

    /** 用户ID */
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long userId;

    /** 用户姓名 */
    private String userName;
}
