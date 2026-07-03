package com.niuwang.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.niuwang.common.serializer.LongToStringSerializer;
import lombok.Data;

/**
 * 用户信息VO
 */
@Data
public class UserInfoVO {

    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;

    private String name;
    private String loginName;
    private Integer age;
    private Integer gender;
    private String genderText;
}
