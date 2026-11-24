package com.studyspace.constants;

/**
 * 自习室与座位相关常量
 * <p>取值与 init.sql、room.status / seat.status / seat.seat_type 字段保持一致。</p>
 */
public final class RoomConstants {

    private RoomConstants() {
    }

    /** 自习室状态：开放 */
    public static final String ROOM_STATUS_OPEN = "OPEN";

    /** 座位状态：空闲 */
    public static final String SEAT_STATUS_FREE = "FREE";

    /** 座位状态：已预约（有人在未来的时段订了） */
    public static final String SEAT_STATUS_BOOKED = "BOOKED";

    /** 座位状态：使用中（已签到） */
    public static final String SEAT_STATUS_IN_USE = "IN_USE";

    /** 座位状态：停用（维修中，不参与查询和预约） */
    public static final String SEAT_STATUS_DISABLED = "DISABLED";

    /** 座位类型：靠窗（每行第一列） */
    public static final String SEAT_TYPE_WINDOW = "WINDOW";

    /** 座位类型：带插座（每行最后一列） */
    public static final String SEAT_TYPE_POWER = "POWER";

    /** 座位类型：普通 */
    public static final String SEAT_TYPE_NORMAL = "NORMAL";
}
