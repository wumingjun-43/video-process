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

    /** 人脸照片存储路径 */
    private String faceImageUrl;

    /** 人脸特征向量(DashScope embedding,逗号分隔浮点数) */
    private String faceFeatureVector;

    /** 人脸参考图片URL列表(JSON数组字符串) */
    private String faceReferenceUrls;
}
