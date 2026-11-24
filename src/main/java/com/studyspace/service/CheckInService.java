package com.studyspace.service;

import com.studyspace.dto.BookingVO;
import com.studyspace.dto.CheckInCodeVO;

import java.util.List;

/**
 * 签到签退业务（F-12 ~ F-14，A-18 / A-19 / A-25）
 *
 * <p>这个模块只管"人来了没有、走了没有"：签到、签退、签到凭证，
 * 以及超时没签到自动释放座位。下单、改时间、取消都属于预约模块，不在这里。</p>
 */
public interface CheckInService {

    /** 签到：校验归属、状态、座位可用、签到窗口、签到码（A-18） */
    BookingVO checkIn(Long bookingId, Long userId, String code);

    /** 签到凭证：签到码 + 签到窗口 + 倒计时，本人或管理员可查（A-25） */
    CheckInCodeVO checkInCode(Long bookingId, Long viewerId, boolean admin);

    /** 签退：使用中 → 已完成，提前签退会立即释放座位（A-19） */
    BookingVO checkOut(Long bookingId, Long userId);

    /** 定时任务用：找出超过签到截止时间还是待签到的预约 id */
    List<Long> findTimeoutBookingIds();

    /** 定时任务用：把一条超时预约判为违约、扣分并释放座位（单独事务） */
    void markNoShow(Long bookingId);

    /** 定时任务用：找出已签到且已过结束时间的预约 id */
    List<Long> findFinishedBookingIds();

    /** 定时任务用：把一条到点的预约自动完成（单独事务） */
    void completeExpired(Long bookingId);
}
