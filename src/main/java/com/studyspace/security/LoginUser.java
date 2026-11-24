package com.studyspace.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录用户身份，放进 SecurityContext 里
 * <p>业务代码要拿当前用户 id，只能从这里取（也就是从令牌里解出来的），
 * 绝不能相信前端传过来的 userId —— 否则改个参数就能操作别人的数据。</p>
 */
@Data
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long id;

    private String studentNo;

    private String role;
}
