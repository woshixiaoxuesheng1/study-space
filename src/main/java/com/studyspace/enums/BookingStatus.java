package com.studyspace.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 预约单状态（需求说明 3.2）
 * <p>五个状态只能按下面的方向走：
 * PENDING_CHECKIN → CHECKED_IN / CANCELLED / NO_SHOW，CHECKED_IN → COMPLETED，
 * 后三个是终态，不再变化。</p>
 * <p>把"谁能变成谁"集中写在枚举里，业务代码只有一处能做状态判断，
 * 避免东一处西一处改状态，最后出现"已完成又变成违约"的脏数据。</p>
 */
public enum BookingStatus {

    /** 待签到：刚预约成功 */
    PENDING_CHECKIN("待签到"),
    /** 使用中：已签到 */
    CHECKED_IN("使用中"),
    /** 已完成：签退或到点自动结束 */
    COMPLETED("已完成"),
    /** 已取消：学生或管理员取消 */
    CANCELLED("已取消"),
    /** 违约：超过签到截止时间没签到 */
    NO_SHOW("违约");

    private final String text;

    BookingStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /** 允许的状态流转，终态对应空集合 */
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED.put(PENDING_CHECKIN, EnumSet.of(CHECKED_IN, CANCELLED, NO_SHOW));
        ALLOWED.put(CHECKED_IN, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(NO_SHOW, EnumSet.noneOf(BookingStatus.class));
    }

    /** 当前状态能否流转到目标状态 */
    public boolean canTransitTo(BookingStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 字符串转枚举，无法识别时返回 null（数据库里出现脏值的兜底） */
    public static BookingStatus of(String value) {
        if (value == null) {
            return null;
        }
        for (BookingStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }

    /** 状态中文，前端直接显示 */
    public static String textOf(String value) {
        BookingStatus status = of(value);
        return status == null ? value : status.getText();
    }

    /** 是否为"有效占用"状态：这两种状态才占座位，其余状态座位已经释放 */
    public static boolean isActive(String value) {
        return PENDING_CHECKIN.name().equals(value) || CHECKED_IN.name().equals(value);
    }
}
