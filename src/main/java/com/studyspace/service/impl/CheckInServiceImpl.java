package com.studyspace.service.impl;

import com.studyspace.common.BusinessException;
import com.studyspace.common.ResultCode;
import com.studyspace.constants.BookingConstants;
import com.studyspace.constants.CommonConstants;
import com.studyspace.constants.RoomConstants;
import com.studyspace.dto.BookingVO;
import com.studyspace.dto.CheckInCodeVO;
import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.entity.User;
import com.studyspace.enums.BookingStatus;
import com.studyspace.mapper.CheckInMapper;
import com.studyspace.mapper.RoomMapper;
import com.studyspace.mapper.SeatMapper;
import com.studyspace.mapper.UserMapper;
import com.studyspace.service.BookingService;
import com.studyspace.service.CheckInService;
import com.studyspace.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到签退业务实现（F-12 ~ F-14）
 *
 * <p>签到要按顺序过四关，哪一关不过就返回对应的原因，不要笼统地说"签到失败"：</p>
 * <ol>
 *   <li><b>是谁</b>：预约必须是自己的，前端传别人的 id 也签不了（身份从令牌取）；</li>
 *   <li><b>状态</b>：只有"待签到"能签到，已签到/已违约/已完成/已取消都给出人话提示；</li>
 *   <li><b>场地</b>：座位被停用、自习室临时关闭时，签到了也没地方坐，先拦住；</li>
 *   <li><b>时间 + 签到码</b>：窗口是 [开始-15 分钟, 开始+15 分钟]，以服务端时间为准；码对了才改状态。</li>
 * </ol>
 *
 * <p>签退则只有一条路：使用中 → 已完成。状态变成已完成，这张预约就不再占座，
 * 提前签退等于把剩余时间立刻还给别人——不需要额外去改 seat 表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final CheckInMapper checkInMapper;
    private final SeatMapper seatMapper;
    private final RoomMapper roomMapper;
    private final UserMapper userMapper;
    /** 签到成功后要返回完整的预约信息，直接复用预约模块的详情组装，不重复写一遍 */
    private final BookingService bookingService;
    private final CreditService creditService;

    /** 签到窗口：开始前多少分钟开放签到 */
    @Value("${booking.check-in-early-minutes:15}")
    private int checkInEarlyMinutes;
    /** 签到窗口：开始后多少分钟截止签到 */
    @Value("${booking.check-in-late-minutes:15}")
    private int checkInLateMinutes;
    /** 超时未签到扣多少分 */
    @Value("${booking.no-show-deduct:10}")
    private int noShowDeduct;

    // ------------------------------------------------------------------
    // 签到（A-18）
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVO checkIn(Long bookingId, Long userId, String code) {
        Booking booking = requireBooking(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 第 1 关：状态。每种状态说清楚为什么不行，别让学生看到"状态不允许"后一脸懵
        String status = booking.getStatus();
        if (BookingStatus.CHECKED_IN.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "已签到，无需重复签到");
        }
        if (BookingStatus.NO_SHOW.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约已超时未签到，座位已释放，请重新预约");
        }
        if (BookingStatus.COMPLETED.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "本次预约已结束，不能再签到");
        }
        if (BookingStatus.CANCELLED.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约已取消，不能签到");
        }
        if (!BookingStatus.PENDING_CHECKIN.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED,
                    "当前预约状态为「" + BookingStatus.textOf(status) + "」，不能签到");
        }

        // 第 2 关：座位和自习室还在不在。座位停用、房间临时关闭时，
        // 放人进去也没地方坐，不如在门口就拦住并让他找管理员。
        Seat seat = seatMapper.selectById(booking.getSeatId());
        if (seat == null) {
            throw new BusinessException(ResultCode.SEAT_NOT_FOUND, "座位已被移除，请联系管理员处理");
        }
        if (RoomConstants.SEAT_STATUS_DISABLED.equals(seat.getStatus())) {
            throw new BusinessException(ResultCode.SEAT_DISABLED, "该座位已停用，签到失败，请联系管理员换座");
        }
        Room room = roomMapper.selectById(seat.getRoomId());
        if (room == null) {
            throw new BusinessException(ResultCode.ROOM_NOT_FOUND);
        }
        if (!RoomConstants.ROOM_STATUS_OPEN.equals(room.getStatus())) {
            throw new BusinessException(ResultCode.ROOM_CLOSED, "自习室当前未开放，暂时不能签到");
        }

        // 第 3 关：签到窗口 [开始-15 分钟, 开始+15 分钟]。时间以服务端为准，
        // 学生把手机时间改到明天也签到不了。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = booking.getStartTime().minusMinutes(checkInEarlyMinutes);
        LocalDateTime latest = booking.getStartTime().plusMinutes(checkInLateMinutes);
        if (now.isBefore(earliest)) {
            throw new BusinessException(ResultCode.CHECK_IN_WINDOW_CLOSED, "签到还没开始，最早 " + fmt(earliest) + " 可签到");
        }
        if (now.isAfter(latest)) {
            throw new BusinessException(ResultCode.CHECK_IN_WINDOW_CLOSED,
                    "签到已截止（" + fmt(latest) + "），座位将被自动释放，请重新预约");
        }

        // 第 4 关：签到码。前三关都过了才比对，避免用错误信息"试"出码对不对
        if (code == null || !code.trim().equals(booking.getCheckInCode())) {
            throw new BusinessException(ResultCode.CHECK_IN_CODE_ERROR);
        }

        if (checkInMapper.markCheckedIn(bookingId, now) == 0) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约状态已变化，请刷新后重试");
        }
        long lateMinutes = Duration.between(booking.getStartTime(), now).toMinutes();
        if (lateMinutes > 0) {
            // 迟到但在窗口内：不扣分，只记日志，答辩时可以演示"迟到签到"
            log.info("签到成功（迟到 {} 分钟）：bookingId={}, userId={}", lateMinutes, bookingId, userId);
        } else {
            log.info("签到成功：bookingId={}, userId={}", bookingId, userId);
        }
        return bookingService.detail(bookingId, userId, false);
    }

    // ------------------------------------------------------------------
    // 签到凭证（A-25）
    // ------------------------------------------------------------------

    @Override
    public CheckInCodeVO checkInCode(Long bookingId, Long viewerId, boolean admin) {
        Booking booking = requireBooking(bookingId);
        // 本人看自己的码；管理员能看是为了在服务台帮学生核对，但不给他签到的权限
        if (!admin && !booking.getUserId().equals(viewerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        Seat seat = seatMapper.selectById(booking.getSeatId());
        Room room = seat == null ? null : roomMapper.selectById(seat.getRoomId());
        LocalDateTime windowStart = booking.getStartTime().minusMinutes(checkInEarlyMinutes);
        LocalDateTime windowEnd = booking.getStartTime().plusMinutes(checkInLateMinutes);
        return CheckInCodeVO.of(booking, seat, room, windowStart, windowEnd, LocalDateTime.now());
    }

    // ------------------------------------------------------------------
    // 签退（A-19）
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVO checkOut(Long bookingId, Long userId) {
        Booking booking = requireBooking(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 先把"不该签退"的几种情况分开说清楚，比统一报"状态不允许"好排查
        String status = booking.getStatus();
        if (BookingStatus.PENDING_CHECKIN.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "还没有签到，不能签退");
        }
        if (BookingStatus.COMPLETED.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "本次预约已结束，无需重复签退");
        }
        if (BookingStatus.NO_SHOW.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约已超时未签到，座位已释放，无需签退");
        }
        if (BookingStatus.CANCELLED.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约已取消，无需签退");
        }
        if (!BookingStatus.CHECKED_IN.name().equals(status)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED,
                    "当前预约状态为「" + BookingStatus.textOf(status) + "」，不能签退");
        }

        // 签退时间取"现在"和"预约结束时间"里更早的那个：
        // 已经过了结束时间才点签退的，记成结束时间，避免统计出"用了 3 小时"的假数据
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime outTime = now.isAfter(booking.getEndTime()) ? booking.getEndTime() : now;

        if (checkInMapper.markCompleted(bookingId, outTime) == 0) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约状态已变化，请刷新后重试");
        }
        log.info("签退成功：bookingId={}, userId={}, 是否提前={}", bookingId, userId, outTime.isBefore(booking.getEndTime()));
        return bookingService.detail(bookingId, userId, false);
    }

    // ------------------------------------------------------------------
    // 超时释放 / 到点自动结束（F-14）
    // ------------------------------------------------------------------

    @Override
    public List<Long> findTimeoutBookingIds() {
        // 签到截止 = 开始 + 15 分钟，等价于 start_time < now - 15 分钟
        return checkInMapper.selectTimeoutIds(LocalDateTime.now().minusMinutes(checkInLateMinutes));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNoShow(Long bookingId) {
        Booking booking = checkInMapper.selectById(bookingId);
        if (booking == null || !BookingStatus.PENDING_CHECKIN.name().equals(booking.getStatus())) {
            return;
        }
        // 条件更新：如果用户刚好在这一秒签到成功，这里返回 0，不会把"使用中"改成"违约"
        if (checkInMapper.markNoShow(bookingId) == 0) {
            return;
        }
        User user = userMapper.selectById(booking.getUserId());
        if (user != null) {
            creditService.deduct(user, noShowDeduct, bookingId, BookingConstants.VIOLATION_NO_SHOW,
                    "超过签到截止时间仍未签到，座位自动释放，扣 " + noShowDeduct + " 分");
        }
        log.info("超时未签到判违约：bookingId={}, userId={}", bookingId, booking.getUserId());
    }

    @Override
    public List<Long> findFinishedBookingIds() {
        return checkInMapper.selectFinishedIds(LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeExpired(Long bookingId) {
        Booking booking = checkInMapper.selectById(bookingId);
        if (booking == null || !BookingStatus.CHECKED_IN.name().equals(booking.getStatus())) {
            return;
        }
        // 自动结束时把签退时间记成预约结束时间，比"发现时的时间"更准确
        if (checkInMapper.markCompleted(bookingId, booking.getEndTime()) == 0) {
            return;
        }
        log.info("预约到点自动完成：bookingId={}", bookingId);
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private Booking requireBooking(Long bookingId) {
        Booking booking = checkInMapper.selectById(bookingId);
        if (booking == null) {
            throw new BusinessException(ResultCode.BOOKING_NOT_FOUND);
        }
        return booking;
    }

    private String fmt(LocalDateTime time) {
        return time == null ? null : CommonConstants.DATE_TIME_FORMATTER.format(time);
    }
}
