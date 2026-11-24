package com.studyspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyspace.common.BusinessException;
import com.studyspace.common.ResultCode;
import com.studyspace.constants.BookingConstants;
import com.studyspace.constants.CommonConstants;
import com.studyspace.constants.RoomConstants;
import com.studyspace.constants.UserConstants;
import com.studyspace.dto.BookingVO;
import com.studyspace.dto.CreateBookingRequest;
import com.studyspace.dto.PageResult;
import com.studyspace.dto.SeatOccupancy;
import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.entity.User;
import com.studyspace.entity.Violation;
import com.studyspace.enums.BookingStatus;
import com.studyspace.mapper.BookingMapper;
import com.studyspace.mapper.RoomMapper;
import com.studyspace.mapper.SeatMapper;
import com.studyspace.mapper.UserMapper;
import com.studyspace.mapper.ViolationMapper;
import com.studyspace.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 预约业务实现（F-09 ~ F-13）
 *
 * <p>整个模块只有三条铁律：</p>
 * <ol>
 *   <li><b>冲突判断</b>：新开始 &lt; 旧结束 且 新结束 &gt; 旧开始，全程只认 booking 表；</li>
 *   <li><b>并发安全</b>：创建预约的第一条 SQL 就锁座位行（FOR UPDATE），查冲突和插入在同一个事务里；</li>
 *   <li><b>状态不乱跳</b>：所有状态变更前都过一遍 BookingStatus.canTransitTo，
 *       并且 UPDATE 语句带上 "AND status = 原状态"，并发下只有一个人能改成功。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingMapper bookingMapper;
    private final SeatMapper seatMapper;
    private final RoomMapper roomMapper;
    private final UserMapper userMapper;
    private final ViolationMapper violationMapper;

    /** 每人每天最多预约几次（需求第 3 章第 5 步"今天订了几次"） */
    @Value("${booking.daily-limit:3}")
    private int dailyLimit;
    /** 单次预约最短时长（分钟）：40 分钟的预约刚坐下就得走，不如不订 */
    @Value("${booking.min-duration-minutes:60}")
    private int minDurationMinutes;
    /** 开始前多少分钟以内取消算违约 */
    @Value("${booking.cancel-free-minutes:30}")
    private int cancelFreeMinutes;
    /** 签到窗口：开始前多少分钟开放签到 */
    @Value("${booking.check-in-early-minutes:15}")
    private int checkInEarlyMinutes;
    /** 签到窗口：开始后多少分钟截止签到 */
    @Value("${booking.check-in-late-minutes:15}")
    private int checkInLateMinutes;
    /** 信用分限制线，低于它不能预约 */
    @Value("${booking.credit-limit:60}")
    private int creditLimit;
    /** 信用分低于限制线后禁止预约多少天 */
    @Value("${booking.ban-days:7}")
    private int banDays;
    /** 违约一次扣多少分 */
    @Value("${booking.no-show-deduct:10}")
    private int noShowDeduct;
    /** 临近开始取消扣多少分 */
    @Value("${booking.late-cancel-deduct:10}")
    private int lateCancelDeduct;

    // ------------------------------------------------------------------
    // 创建预约（A-14）
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVO create(Long userId, CreateBookingRequest request) {
        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        LocalDateTime now = LocalDateTime.now();

        if (!start.isBefore(end)) {
            throw new BusinessException(ResultCode.BOOKING_TIME_INVALID);
        }
        // 最短时长校验：时长从配置读，改 yaml 即可调整，不用动代码
        if (Duration.between(start, end).toMinutes() < minDurationMinutes) {
            throw new BusinessException(ResultCode.BOOKING_DURATION_TOO_SHORT,
                    "单次预约时长不能少于 " + minDurationMinutes + " 分钟");
        }
        // 不限制跨天，开放时间校验就会失真（22:00-次日01:00 会被当成合法），这里直接拒绝
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "一次预约必须在同一天内开始和结束");
        }
        if (!start.isAfter(now)) {
            throw new BusinessException(ResultCode.BOOKING_TIME_PAST);
        }

        // 第 1 条 SQL 就锁座位：同一个座位的第二个请求会在这一行排队，
        // 等第一个人提交后才继续往下走，下面查冲突一定能看到刚插入的那条预约。
        Seat seat = seatMapper.selectByIdForUpdate(request.getSeatId());
        if (seat == null) {
            throw new BusinessException(ResultCode.SEAT_NOT_FOUND);
        }
        if (RoomConstants.SEAT_STATUS_DISABLED.equals(seat.getStatus())) {
            throw new BusinessException(ResultCode.SEAT_DISABLED);
        }

        Room room = roomMapper.selectById(seat.getRoomId());
        if (room == null) {
            throw new BusinessException(ResultCode.ROOM_NOT_FOUND);
        }
        if (!RoomConstants.ROOM_STATUS_OPEN.equals(room.getStatus())) {
            throw new BusinessException(ResultCode.ROOM_CLOSED);
        }
        if (start.toLocalTime().isBefore(room.getOpenTime()) || end.toLocalTime().isAfter(room.getCloseTime())) {
            throw new BusinessException(ResultCode.OUT_OF_OPEN_TIME,
                    "预约时间需在开放时间 " + room.getOpenTime() + " - " + room.getCloseTime() + " 之内");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        checkCanBook(user, now);

        // 每日次数限制：按"预约开始的那一天"算，已取消的不占名额
        long sameDayCount = bookingMapper.countUserBookingsOnDay(userId,
                start.toLocalDate().atStartOfDay(), start.toLocalDate().plusDays(1).atStartOfDay());
        if (sameDayCount >= dailyLimit) {
            throw new BusinessException(ResultCode.BOOKING_DAILY_LIMIT,
                    start.toLocalDate() + " 已预约 " + sameDayCount + " 次，每人每天最多 " + dailyLimit + " 次");
        }

        if (bookingMapper.countSeatOverlap(seat.getId(), start, end) > 0) {
            throw new BusinessException(ResultCode.SEAT_ALREADY_BOOKED,
                    "该座位 " + fmt(start) + " - " + fmt(end) + " 已被预约。" + freeSeatHint(room, start, end));
        }
        if (bookingMapper.countUserOverlap(userId, start, end) > 0) {
            throw new BusinessException(ResultCode.BOOKING_TIME_CONFLICT);
        }

        Booking booking = new Booking();
        booking.setBookingNo(nextBookingNo());
        booking.setUserId(userId);
        booking.setSeatId(seat.getId());
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setStatus(BookingStatus.PENDING_CHECKIN.name());
        booking.setCheckInCode(randomCheckInCode());
        bookingMapper.insert(booking);

        log.info("预约成功：bookingNo={}, userId={}, seatId={}, {} - {}",
                booking.getBookingNo(), userId, seat.getId(), fmt(start), fmt(end));
        return assemble(List.of(booking), userId, false).get(0);
    }

    // ------------------------------------------------------------------
    // 查询（A-15 / A-16）
    // ------------------------------------------------------------------

    @Override
    public PageResult<BookingVO> pageMine(Long userId, int page, int size, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), CommonConstants.MAX_PAGE_SIZE);

        LambdaQueryWrapper<Booking> wrapper = new LambdaQueryWrapper<Booking>()
                .eq(Booking::getUserId, userId)
                .eq(StringUtils.hasText(status), Booking::getStatus, status)
                .orderByDesc(Booking::getCreateTime)
                .orderByDesc(Booking::getId);

        Long total = bookingMapper.selectCount(wrapper);
        if (total == null || total == 0) {
            return PageResult.empty(safePage, safeSize);
        }

        // 与资源模块一致：没引分页插件，用 LIMIT 手动分页，两个数字都是后端算的 int
        wrapper.last("LIMIT " + (long) (safePage - 1) * safeSize + ", " + safeSize);
        List<Booking> bookings = bookingMapper.selectList(wrapper);
        return new PageResult<>(total, safePage, safeSize, assemble(bookings, userId, false));
    }

    @Override
    public BookingVO detail(Long bookingId, Long viewerId, boolean admin) {
        Booking booking = requireBooking(bookingId);
        if (!admin && !booking.getUserId().equals(viewerId)) {
            // 不是本人也不是管理员：不能返回别人的预约（需求说明 V-03 越权测试）
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return assemble(List.of(booking), viewerId, admin).get(0);
    }

    // ------------------------------------------------------------------
    // 取消（A-17）
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVO cancel(Long bookingId, Long operatorId, boolean admin, String reason) {
        Booking booking = requireBooking(bookingId);
        if (!admin && !booking.getUserId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        assertCanTransit(booking, BookingStatus.CANCELLED, "取消");

        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(booking.getStartTime())) {
            // 已经到点了就别取消了，让他去签到；否则会出现"开始后又免费取消"的漏洞
            throw new BusinessException(ResultCode.BOOKING_ALREADY_STARTED);
        }

        // 开始前 30 分钟以外免费取消；以内算违约；管理员代取消不扣学生分
        boolean penalty = !admin && !now.isBefore(booking.getStartTime().minusMinutes(cancelFreeMinutes));

        String finalReason = StringUtils.hasText(reason)
                ? truncate(reason.trim())
                : (admin ? "管理员取消" : "用户主动取消");
        if (bookingMapper.markCancelled(bookingId, finalReason) == 0) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约状态已变化，请刷新后重试");
        }

        if (penalty) {
            User user = userMapper.selectById(booking.getUserId());
            if (user != null) {
                deductCredit(user, lateCancelDeduct, bookingId, BookingConstants.VIOLATION_LATE_CANCEL,
                        "开始前 " + cancelFreeMinutes + " 分钟内取消预约，扣 " + lateCancelDeduct + " 分");
            }
        }

        BookingVO vo = assemble(List.of(requireBooking(bookingId)), operatorId, admin).get(0);
        vo.setPenaltyPoints(penalty ? lateCancelDeduct : 0);
        log.info("取消预约：bookingId={}, 操作人={}, 是否扣分={}", bookingId, operatorId, penalty);
        return vo;
    }

    // ------------------------------------------------------------------
    // 签到 / 签退（A-18 / A-19）
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVO checkIn(Long bookingId, Long userId, String code) {
        Booking booking = requireBooking(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (BookingStatus.CHECKED_IN.name().equals(booking.getStatus())) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "已签到，无需重复签到");
        }
        if (BookingStatus.NO_SHOW.name().equals(booking.getStatus())) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约已超时未签到，座位已释放，请重新预约");
        }
        assertCanTransit(booking, BookingStatus.CHECKED_IN, "签到");

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
        if (code == null || !code.trim().equals(booking.getCheckInCode())) {
            throw new BusinessException(ResultCode.CHECK_IN_CODE_ERROR);
        }

        if (bookingMapper.markCheckedIn(bookingId, now) == 0) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约状态已变化，请刷新后重试");
        }
        log.info("签到成功：bookingId={}, userId={}", bookingId, userId);
        return assemble(List.of(requireBooking(bookingId)), userId, false).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVO checkOut(Long bookingId, Long userId) {
        Booking booking = requireBooking(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        assertCanTransit(booking, BookingStatus.COMPLETED, "签退");

        if (bookingMapper.markCompleted(bookingId, LocalDateTime.now()) == 0) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED, "预约状态已变化，请刷新后重试");
        }
        log.info("签退成功：bookingId={}, userId={}", bookingId, userId);
        return assemble(List.of(requireBooking(bookingId)), userId, false).get(0);
    }

    // ------------------------------------------------------------------
    // 定时任务（F-14 + 流程第 9 步）
    // ------------------------------------------------------------------

    @Override
    public List<Long> findTimeoutBookingIds() {
        // 签到截止 = 开始 + 15 分钟，等价于 start_time < now - 15 分钟
        return bookingMapper.selectTimeoutIds(LocalDateTime.now().minusMinutes(checkInLateMinutes));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNoShow(Long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null || !BookingStatus.PENDING_CHECKIN.name().equals(booking.getStatus())) {
            return;
        }
        // 条件更新：如果用户刚好在这一秒签到成功，这里返回 0，不会把"使用中"改成"违约"
        if (bookingMapper.markNoShow(bookingId) == 0) {
            return;
        }
        User user = userMapper.selectById(booking.getUserId());
        if (user != null) {
            deductCredit(user, noShowDeduct, bookingId, BookingConstants.VIOLATION_NO_SHOW,
                    "超过签到截止时间仍未签到，座位自动释放，扣 " + noShowDeduct + " 分");
        }
        log.info("超时未签到判违约：bookingId={}, userId={}", bookingId, booking.getUserId());
    }

    @Override
    public List<Long> findFinishedBookingIds() {
        return bookingMapper.selectFinishedIds(LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeExpired(Long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null || !BookingStatus.CHECKED_IN.name().equals(booking.getStatus())) {
            return;
        }
        // 自动结束时把签退时间记成预约结束时间，比"发现时的时间"更准确
        if (bookingMapper.markCompleted(bookingId, booking.getEndTime()) == 0) {
            return;
        }
        log.info("预约到点自动完成：bookingId={}", bookingId);
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private Booking requireBooking(Long bookingId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new BusinessException(ResultCode.BOOKING_NOT_FOUND);
        }
        return booking;
    }

    /** 信用分 / 禁用 / 禁约期校验 */
    private void checkCanBook(User user, LocalDateTime now) {
        if (user.getStatus() != null && user.getStatus() != UserConstants.STATUS_NORMAL) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        int score = creditOf(user);
        if (user.getBanUntil() != null && user.getBanUntil().isAfter(now)) {
            throw new BusinessException(ResultCode.CREDIT_NOT_ENOUGH,
                    "信用分 " + score + " 分，处于限制预约期，将于 " + fmt(user.getBanUntil()) + " 自动恢复");
        }
        if (score < creditLimit) {
            throw new BusinessException(ResultCode.CREDIT_NOT_ENOUGH,
                    "信用分 " + score + " 分，低于 " + creditLimit + " 分，暂时不能预约");
        }
    }

    /** 状态流转统一入口：先查枚举规则，再让 UPDATE 语句带原状态兜底 */
    private void assertCanTransit(Booking booking, BookingStatus target, String action) {
        BookingStatus current = BookingStatus.of(booking.getStatus());
        if (current == null || !current.canTransitTo(target)) {
            throw new BusinessException(ResultCode.BOOKING_STATUS_NOT_ALLOWED,
                    "当前预约状态为「" + BookingStatus.textOf(booking.getStatus()) + "」，不能" + action);
        }
    }

    /**
     * 扣信用分 + 写违约记录
     * <p>扣到 60 分以下时写入 ban_until = 现在 + 7 天，到期后比较自然失效，
     * 不需要额外的"解禁"定时任务。</p>
     */
    private void deductCredit(User user, int points, Long bookingId, String type, String reason) {
        int oldScore = creditOf(user);
        int newScore = Math.max(0, oldScore - points);
        user.setCreditScore(newScore);
        if (newScore < creditLimit) {
            user.setBanUntil(LocalDateTime.now().plusDays(banDays));
        }
        userMapper.updateById(user);

        Violation violation = new Violation();
        violation.setUserId(user.getId());
        violation.setBookingId(bookingId);
        violation.setType(type);
        violation.setDeductScore(points);
        violation.setHappenTime(LocalDateTime.now());
        violation.setReason(reason);
        violation.setRevoked(0);
        violationMapper.insert(violation);

        log.info("信用分扣减：userId={}, {} -> {}（{}）", user.getId(), oldScore, newScore, type);
    }

    /**
     * 批量组装 VO：先一次性把座位、自习室、用户查出来，避免每条预约查三次（N+1 查询）
     */
    private List<BookingVO> assemble(List<Booking> bookings, Long viewerId, boolean admin) {
        if (bookings == null || bookings.isEmpty()) {
            return List.of();
        }
        Set<Long> seatIds = bookings.stream().map(Booking::getSeatId).collect(Collectors.toSet());
        Map<Long, Seat> seatMap = seatIds.isEmpty() ? Map.of()
                : seatMapper.selectBatchIds(seatIds).stream().collect(Collectors.toMap(Seat::getId, s -> s));

        Set<Long> roomIds = seatMap.values().stream().map(Seat::getRoomId).collect(Collectors.toSet());
        Map<Long, Room> roomMap = roomIds.isEmpty() ? Map.of()
                : roomMapper.selectBatchIds(roomIds).stream().collect(Collectors.toMap(Room::getId, r -> r));

        Set<Long> userIds = bookings.stream().map(Booking::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        LocalDateTime now = LocalDateTime.now();
        return bookings.stream().map(booking -> {
            Seat seat = seatMap.get(booking.getSeatId());
            Room room = seat == null ? null : roomMap.get(seat.getRoomId());
            BookingVO vo = BookingVO.from(booking, seat, room, userMap.get(booking.getUserId()));
            fillActions(vo, booking, now, viewerId, admin);
            return vo;
        }).toList();
    }

    /** 计算前端要用的"能不能签到 / 能不能取消 / 取消扣不扣分" */
    private void fillActions(BookingVO vo, Booking booking, LocalDateTime now, Long viewerId, boolean admin) {
        boolean owner = booking.getUserId().equals(viewerId);
        boolean pending = BookingStatus.PENDING_CHECKIN.name().equals(booking.getStatus());
        LocalDateTime earliest = booking.getStartTime().minusMinutes(checkInEarlyMinutes);
        LocalDateTime latest = booking.getStartTime().plusMinutes(checkInLateMinutes);

        vo.setCheckInDeadline(fmt(latest));
        vo.setCanCheckIn(owner && pending && !now.isBefore(earliest) && !now.isAfter(latest));
        vo.setCanCancel((owner || admin) && pending && now.isBefore(booking.getStartTime()));
        vo.setCancelPenalty(!now.isBefore(booking.getStartTime().minusMinutes(cancelFreeMinutes)));
        if (!owner && !admin) {
            vo.setCheckInCode(null);
        }
    }

    /**
     * 座位冲突时给一句"换哪个座位"的提示（需求第 3 章第 4 步）
     * <p>最多列 3 个同房间、同时段空闲的座位号；一个都没有就提醒换时间。</p>
     */
    private String freeSeatHint(Room room, LocalDateTime start, LocalDateTime end) {
        List<Seat> seats = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getRoomId, room.getId())
                .ne(Seat::getStatus, RoomConstants.SEAT_STATUS_DISABLED)
                .orderByAsc(Seat::getRowNo)
                .orderByAsc(Seat::getColNo));
        Set<Long> occupied = bookingMapper.selectOccupancy(room.getId(), start, end).stream()
                .map(SeatOccupancy::getSeatId)
                .collect(Collectors.toSet());
        String free = seats.stream()
                .filter(seat -> !occupied.contains(seat.getId()))
                .limit(3)
                .map(Seat::getSeatNo)
                .collect(Collectors.joining("、"));
        return free.isEmpty() ? "该时段本自习室已无空位，请换个时间" : "同房间还有空位：" + free + "，可以换一个";
    }

    /**
     * 预约编号：BK + 秒级时间戳 + 2 位随机数
     * <p>不用"当天第几单"的序号，是因为两个不同座位同时下单时都会算到同一个序号，
     * 撞了唯一索引就只能让用户重试；时间戳 + 随机数在实际并发量下不会重复。</p>
     */
    private String nextBookingNo() {
        return BookingConstants.BOOKING_NO_PREFIX + BookingConstants.BOOKING_NO_TIME_FORMATTER.format(LocalDateTime.now())
                + String.format("%02d", ThreadLocalRandom.current().nextInt(100));
    }

    private String randomCheckInCode() {
        return String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private int creditOf(User user) {
        return user.getCreditScore() == null ? UserConstants.DEFAULT_CREDIT_SCORE : user.getCreditScore();
    }

    private String truncate(String text) {
        return text.length() <= BookingConstants.MAX_REASON_LENGTH ? text : text.substring(0, BookingConstants.MAX_REASON_LENGTH);
    }

    private String fmt(LocalDateTime time) {
        return time == null ? null : CommonConstants.DATE_TIME_FORMATTER.format(time);
    }
}
