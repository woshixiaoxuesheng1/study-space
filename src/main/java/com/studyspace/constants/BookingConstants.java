package com.studyspace.constants;

import java.time.format.DateTimeFormatter;

/**
 * 预约与签到相关常量
 * <p>违约类型不再用字符串常量：它是有固定取值集合、还要带中文文案的，放在 ViolationType 枚举里。</p>
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

    /** 预约状态字符串：待签到（与 BookingStatus 枚举名一致，供只比较数据库字符串的地方使用） */
    public static final String STATUS_PENDING_CHECKIN = "PENDING_CHECKIN";

    /** 预约状态字符串：使用中 */
    public static final String STATUS_CHECKED_IN = "CHECKED_IN";
}
