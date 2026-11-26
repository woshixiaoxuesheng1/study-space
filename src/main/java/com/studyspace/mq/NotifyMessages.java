package com.studyspace.mq;

import com.studyspace.constants.CommonConstants;
import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.enums.NotifyEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 通知消息工厂
 *
 * <p>业务代码里只写 {@code notifyProducer.publishAfterCommit(NotifyMessages.created(booking, seat, room, ...))}，
 * 至于"座位展示名怎么拼""时段怎么格式化"这些琐事全在这里做一遍，
 * 预约、签到、违约三个模块不用各写一份、也不会各写出一种格式。</p>
 *
 * <p>每个方法都返回完整消息（messageId、事件名、发生时间都填好），
 * 调用方拿到就能直接投递。</p>
 */
public final class NotifyMessages {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private NotifyMessages() {
    }

    /** 预约创建成功：带上签到码和签到窗口，学生拿到通知就知道什么时候去、拿什么码 */
    public static BookingNotifyMessage created(Booking booking, Seat seat, Room room,
                                               LocalDateTime windowStart, LocalDateTime windowEnd) {
        return base(NotifyEvent.BOOKING_CREATED, booking, seat, room)
                .checkInCode(booking.getCheckInCode())
                .remark("签到窗口 " + clock(windowStart) + " - " + clock(windowEnd))
                .build();
    }

    /** 即将开始：提醒去签到，同样带签到码，免得学生到门口再翻订单 */
    public static BookingNotifyMessage remind(Booking booking, Seat seat, Room room,
                                              LocalDateTime windowStart, LocalDateTime windowEnd) {
        return base(NotifyEvent.BOOKING_REMIND, booking, seat, room)
                .checkInCode(booking.getCheckInCode())
                .remark("签到窗口 " + clock(windowStart) + " - " + clock(windowEnd)
                        + "，超时未签到将记为违约并扣分")
                .build();
    }

    /** 预约已取消：说明是谁取消的、有没有扣分 */
    public static BookingNotifyMessage cancelled(Booking booking, Seat seat, Room room,
                                                 String reason, Integer deductPoints, Integer creditScore) {
        return base(NotifyEvent.BOOKING_CANCELLED, booking, seat, room)
                .deductPoints(deductPoints)
                .creditScore(creditScore)
                .remark(reason)
                .build();
    }

    /** 超时未签到判违约：说明扣了多少分、现在还剩多少 */
    public static BookingNotifyMessage noShow(Booking booking, Seat seat, Room room,
                                              Integer deductPoints, Integer creditScore, String remark) {
        return base(NotifyEvent.BOOKING_NO_SHOW, booking, seat, room)
                .deductPoints(deductPoints)
                .creditScore(creditScore)
                .remark(remark)
                .build();
    }

    /** 预约正常结束 */
    public static BookingNotifyMessage completed(Booking booking, Seat seat, Room room) {
        return base(NotifyEvent.BOOKING_COMPLETED, booking, seat, room).build();
    }

    // ------------------------------------------------------------------
    // 公共部分
    // ------------------------------------------------------------------

    /** 五种预约类通知共用的字段：把重复的 builder 赋值收在一处 */
    private static BookingNotifyMessage.BookingNotifyMessageBuilder base(NotifyEvent event, Booking booking,
                                                                        Seat seat, Room room) {
        return BookingNotifyMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .event(event.name())
                .userId(booking.getUserId())
                .bookingId(booking.getId())
                .seatLabel(seatLabel(seat, room))
                .timeRange(timeRange(booking))
                .happenAt(CommonConstants.DATE_TIME_FORMATTER.format(LocalDateTime.now()));
    }

    /** 座位展示名：正常是「图书馆三楼自习室 A02」；座位或自习室已被删掉时退化成能看懂的兜底文案 */
    private static String seatLabel(Seat seat, Room room) {
        if (seat == null) {
            return "已释放的座位";
        }
        return room == null ? seat.getSeatNo() : room.getName() + " " + seat.getSeatNo();
    }

    /** 时段展示：预约不允许跨天，所以日期只写一次「2026-11-26 15:00 - 17:00」 */
    private static String timeRange(Booking booking) {
        if (booking.getStartTime() == null || booking.getEndTime() == null) {
            return "";
        }
        return DAY.format(booking.getStartTime())
                + " " + HOUR_MINUTE.format(booking.getStartTime())
                + " - " + HOUR_MINUTE.format(booking.getEndTime());
    }

    private static String clock(LocalDateTime time) {
        return time == null ? "" : HOUR_MINUTE.format(time);
    }
}
