package com.studyspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求（F-03：只允许改昵称、邮箱、手机号）
 * <p>学号、角色、信用分不允许自己改，所以这里压根没有这些字段</p>
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 20, message = "昵称长度需在 1-20 个字之间")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最多 100 个字符")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
