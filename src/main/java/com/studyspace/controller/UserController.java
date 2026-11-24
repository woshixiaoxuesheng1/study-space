package com.studyspace.controller;

import com.studyspace.common.Result;
import com.studyspace.dto.CreditVO;
import com.studyspace.dto.UpdateProfileRequest;
import com.studyspace.dto.UserVO;
import com.studyspace.security.SecurityUtils;
import com.studyspace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口（A-03 / A-04 / A-05）
 * <p>注意路径里没有 userId：用户身份一律从令牌里取，前端传 userId 也影响不到别人</p>
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** A-03 查看自己的资料 */
    @GetMapping
    public Result<UserVO> me() {
        return Result.success(userService.getProfile(SecurityUtils.currentUserId()));
    }

    /** A-04 修改自己的昵称、邮箱、手机号 */
    @PutMapping
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success("修改成功", userService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    /** A-05 查看自己的信用分 */
    @GetMapping("/credit")
    public Result<CreditVO> myCredit() {
        return Result.success(userService.getCredit(SecurityUtils.currentUserId()));
    }
}
