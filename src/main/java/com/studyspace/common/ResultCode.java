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

    ROOM_NOT_FOUND(2001, "自习室不存在"),
    ROOM_NAME_EXISTS(2002, "该楼栋下已存在同名自习室"),
    ROOM_TIME_INVALID(2003, "开放时间必须早于关闭时间"),
    ROOM_HAS_ACTIVE_BOOKING(2004, "该自习室还有进行中的预约，暂时不能删除"),
    ROOM_SEAT_LOCKED(2005, "该自习室的座位已有预约记录，不能重新生成"),
    ROOM_CLOSED(2006, "该自习室当前不在开放状态"),
    BOOKING_TIME_INVALID(2007, "结束时间必须晚于开始时间"),
    OUT_OF_OPEN_TIME(2008, "查询时间不在自习室开放时间内"),

    BOOKING_NOT_FOUND(3001, "预约不存在"),
    SEAT_NOT_FOUND(3002, "座位不存在"),
    SEAT_DISABLED(3003, "该座位已停用，暂时无法预约"),
    SEAT_ALREADY_BOOKED(3004, "该座位在所选时段已被预约"),
    BOOKING_TIME_CONFLICT(3005, "你在该时段已有其他预约"),
    BOOKING_DAILY_LIMIT(3006, "今日预约次数已达上限"),
    CREDIT_NOT_ENOUGH(3007, "信用分不足，暂时无法预约"),
    BOOKING_STATUS_NOT_ALLOWED(3008, "当前预约状态不允许此操作"),
    CHECK_IN_WINDOW_CLOSED(3009, "不在签到时间窗口内"),
    CHECK_IN_CODE_ERROR(3010, "签到码不正确"),
    BOOKING_ALREADY_STARTED(3011, "预约已开始，无法取消"),
    BOOKING_TIME_PAST(3012, "只能预约未来的时间段"),
    BOOKING_DURATION_TOO_SHORT(3013, "预约时长太短"),

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
