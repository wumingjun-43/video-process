package com.niuwang.controller;

import com.niuwang.common.response.Result;
import com.niuwang.model.dto.FaceMatchDTO;
import com.niuwang.model.dto.FaceRegisterDTO;
import com.niuwang.model.vo.FaceMatchResultVO;
import com.niuwang.model.vo.UserInfoVO;
import com.niuwang.service.AiRecognitionService;
import com.niuwang.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人脸识别控制器
 */
@RestController
@RequestMapping("/face")
@RequiredArgsConstructor
public class FaceRecognitionController {

    private final UserService userService;
    private final AiRecognitionService aiRecognitionService;

    /** 注册用户人脸 */
    @PostMapping("/register")
    public Result<Void> registerFace(@ModelAttribute @Valid FaceRegisterDTO dto) {
        userService.registerFace(dto.getUserId(), dto.getFaceImage());
        return Result.success("人脸注册成功");
    }

    /** 删除用户人脸 */
    @DeleteMapping("/{userId}")
    public Result<Void> removeFace(@PathVariable Long userId) {
        userService.removeFace(userId);
        return Result.success("人脸删除成功");
    }

    /** 人脸匹配 */
    @PostMapping("/match")
    public Result<FaceMatchResultVO> matchFace(@ModelAttribute @Valid FaceMatchDTO dto) {
        FaceMatchResultVO result = userService.matchFace(dto.getImage());
        return Result.success(result);
    }

    /** 获取所有已注册用户人脸列表 */
    @GetMapping("/users")
    public Result<List<UserInfoVO>> getUserFaces() {
        return Result.success(userService.pageUser("", 1, 1000).getRecords());
    }
}
