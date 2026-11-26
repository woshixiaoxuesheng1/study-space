package com.studyspace.constants;

/**
 * 用户、角色与信用分相关常量
 */
public final class UserConstants {

    private UserConstants() {
    }

    /** 用户状态：正常（非该值视为禁用） */
    public static final int STATUS_NORMAL = 1;

    /** 学生注册时的默认角色 */
    public static final String ROLE_STUDENT = "STUDENT";

    /** 管理员角色 */
    public static final String ROLE_ADMIN = "ADMIN";

    /**
     * 信用分初始值，同时也是信用分字段为空时的兜底值
     * <p>信用分限制线（60）和禁约天数（7）属于业务参数，统一放在 application.yaml 的 booking 段，
     * 预约准入和违约扣分读同一份配置，不在这里再留一个常量副本。</p>
     */
    public static final int DEFAULT_CREDIT_SCORE = 100;
}
