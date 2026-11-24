package com.studyspace.dto;

import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.enums.BookingStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 签到凭证（A-25）
 * <p>学生端"签到页"只用这一个接口：拿到 4 位签到码、签到窗口和倒计时，
 * 不用为了显示一个数字去拉整条预约详情。</p>
 * <p>签到码只在"待签到且签到窗口还没关闭"时下发，签到成功后预约里存的码会被清空，
 * 相当于一次性使用，别人捡到截图也签不了第二次。</p>
 */
@Data
public class CheckInCodeVO {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long bookingId;
    private String bookingNo;
    private String status;
    private String statusText;
    private String seatNo;
    private String roomName;
    private String building;
    private String startTime;
    private String endTime;
    /** 签到窗口开始 = 预约开始 - 15 分钟 */
    private String windowStart;
    /** 签到窗口结束 = 预约开始 + 15 分钟 */
    private String windowEnd;
    /** 距离签到窗口关闭还有多少秒；<= 0 表示已过截止时间，前端倒计时到 0 就停 */
    private long remainSeconds;
    /** 现在能不能签到（状态待签到 + 在窗口内） */
    private boolean canCheckIn;
    /** 4 位签到码；不能签到时为 null */
    private String checkInCode;
    private String checkInTime;

    public static CheckInCodeVO of(Booking booking, Seat seat, Room room,
                                   LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime now) {
        CheckInCodeVO vo = new CheckInCodeVO();
        vo.setBookingId(booking.getId());
        vo.setBookingNo(booking.getBookingNo());
        vo.setStatus(booking.getStatus());
        vo.setStatusText(BookingStatus.textOf(booking.getStatus()));
        vo.setStartTime(format(booking.getStartTime()));
        vo.setEndTime(format(booking.getEndTime()));
        vo.setWindowStart(format(windowStart));
        vo.setWindowEnd(format(windowEnd));
        vo.setCheckInTime(format(booking.getCheckInTime()));
        if (seat != null) {
            vo.setSeatNo(seat.getSeatNo());
        }
        if (room != null) {
            vo.setRoomName(room.getName());
            vo.setBuilding(room.getBuilding());
        }

        long remain = java.time.Duration.between(now, windowEnd).getSeconds();
        vo.setRemainSeconds(Math.max(remain, 0));
        boolean windowOpen = !now.isBefore(windowStart) && !now.isAfter(windowEnd);
        vo.setCanCheckIn(BookingStatus.PENDING_CHECKIN.name().equals(booking.getStatus()) && windowOpen);
        // 只有"待签到 + 窗口内"才下发签到码，其他状态一律不给
        vo.setCheckInCode(vo.isCanCheckIn() ? booking.getCheckInCode() : null);
        return vo;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
