package com.studyspace.constants;

import java.time.format.DateTimeFormatter;

/**
 * 预约、签到与违约记录相关常量
 */
public final class BookingConstants {

    private BookingConstants() {
    }

    /** 预约编号前缀，完整形态如 BK2026010112000017 */
    public static final String BOOKING_NO_PREFIX = "BK";

    /** 预约编号里的时间部分格式 */
    public static final DateTimeFormatter BOOKING_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 取消 / 违约原因最长字符数，与 booking.cancel_reason varchar(255) 对齐 */
    public static final int MAX_REASON_LENGTH = 255;

    /** 违约类型：超时未签到 */
    public static final String VIOLATION_NO_SHOW = "NO_SHOW";

    /** 违约类型：临近开始才取消 */
    public static final String VIOLATION_LATE_CANCEL = "LATE_CANCEL";

    /** 预约状态字符串：待签到（与 BookingStatus 枚举名一致，供只比较数据库字符串的地方使用） */
    public static final String STATUS_PENDING_CHECKIN = "PENDING_CHECKIN";

    /** 预约状态字符串：使用中 */
    public static final String STATUS_CHECKED_IN = "CHECKED_IN";
}
