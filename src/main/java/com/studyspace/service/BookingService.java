package com.studyspace.service;

import com.studyspace.vo.BookingVO;
import com.studyspace.dto.CreateBookingRequest;
import com.studyspace.dto.PageResult;

/**
 * 预约业务（F-09 ~ F-11，A-14 ~ A-17）
 *
 * <p>只负责"下单"这一侧：创建、查列表、查详情、取消。
 * 签到 / 签退 / 超时释放属于签到签退模块，见 CheckInService。</p>
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
}
