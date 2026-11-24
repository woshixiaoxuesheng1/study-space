package com.studyspace.dto;

import lombok.Data;

/**
 * 座位占用情况（内部查询用，不直接返回给前端）
 * <p>从 booking 表查出"某个座位在某段时间被哪种状态占用"，用于算座位显示状态和查空闲座位</p>
 */
@Data
public class SeatOccupancy {

    private Long seatId;

    /** 预约状态：PENDING_CHECKIN 待签到 / CHECKED_IN 使用中 */
    private String status;
}
