package com.studyspace.service;

import com.studyspace.dto.CreditVO;
import com.studyspace.dto.LoginRequest;
import com.studyspace.dto.LoginResponse;
import com.studyspace.dto.RegisterRequest;
import com.studyspace.dto.UpdateProfileRequest;
import com.studyspace.dto.UserVO;

/**
 * 用户相关业务
 */
public interface UserService {

    /** 学生注册（F-01） */
    UserVO register(RegisterRequest request);

    /** 登录并签发令牌（F-01 / F-02） */
    LoginResponse login(LoginRequest request);

    /** 查看自己的资料（F-03） */
    UserVO getProfile(Long userId);

    /** 修改自己的昵称、邮箱、手机号（F-03） */
    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    /** 查看自己的信用分（F-04） */
    CreditVO getCredit(Long userId);
}
