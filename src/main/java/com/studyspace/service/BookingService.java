package com.studyspace.service;

import com.studyspace.dto.BookingVO;
import com.studyspace.dto.CreateBookingRequest;
import com.studyspace.dto.PageResult;

import java.util.List;

/**
 * 预约业务（F-09 ~ F-13）
 */
public interface BookingService {

    /** 创建预约：行锁 + 时段冲突 + 本人重复 + 每日次数 + 信用分（A-14） */
    BookingVO create(Long userId, CreateBookingRequest request);

    /** 我的预约列表，可按状态筛选（A-15） */
    PageResult<BookingVO> pageMine(Long userId, int page, int size, String status);

    /** 预约详情，本人或管理员才能看（A-16） */
    BookingVO detail(Long bookingId, Long viewerId, boolean admin);

    /** 取消预约，按时间规则决定是否扣分（A-17） */
    BookingVO cancel(Long bookingId, Long operatorId, boolean admin, String reason);

    /** 签到：校验时间窗口和签到码（A-18） */
    BookingVO checkIn(Long bookingId, Long userId, String code);

    /** 签退（A-19） */
    BookingVO checkOut(Long bookingId, Long userId);

    /** 定时任务用：找出超时未签到的预约 id */
    List<Long> findTimeoutBookingIds();

    /** 定时任务用：把一条超时预约判为违约、扣分并释放座位（单独事务） */
    void markNoShow(Long bookingId);

    /** 定时任务用：找出已签到且已过结束时间的预约 id */
    List<Long> findFinishedBookingIds();

    /** 定时任务用：把一条到点的预约自动完成（单独事务） */
    void completeExpired(Long bookingId);
}
