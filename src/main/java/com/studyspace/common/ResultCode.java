package com.studyspace.common;

/**
 * 业务状态码
 * <p>约定：0 表示成功，其余非 0 数字表示具体失败原因，前端按 code 判断即可</p>
 */
public enum ResultCode {

    SUCCESS(0, "success"),

    PARAM_ERROR(400, "参数不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "数据不存在"),

    STUDENT_NO_EXISTS(1001, "该学号已注册"),
    EMAIL_EXISTS(1002, "该邮箱已被使用"),
    ACCOUNT_NOT_FOUND(1003, "学号或密码错误"),
    ACCOUNT_DISABLED(1005, "账号已被禁用，请联系管理员"),

    SERVER_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
