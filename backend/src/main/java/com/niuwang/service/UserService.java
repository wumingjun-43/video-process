package com.niuwang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuwang.common.response.PageResult;
import com.niuwang.model.dto.UserDTO;
import com.niuwang.model.entity.User;
import com.niuwang.model.vo.FaceMatchResultVO;
import com.niuwang.model.vo.UserInfoVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /** 新增用户 */
    Long addUser(UserDTO dto);

    /** 更新用户 */
    void updateUser(Long id, UserDTO dto);

    /** 查询用户详情 */
    UserInfoVO getUserDetail(Long id);

    /** 分页查询用户列表 */
    PageResult<UserInfoVO> pageUser(String keyword, long page, long size);

    /** 删除用户 */
    void deleteUser(Long id);

    /** 注册用户人脸 */
    void registerFace(Long userId, MultipartFile faceImage);

    /** 删除用户人脸 */
    void removeFace(Long userId);

    /** 人脸匹配 */
    FaceMatchResultVO matchFace(MultipartFile image);
}
