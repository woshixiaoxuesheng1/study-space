package com.studyspace.dto;

import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.entity.User;
import com.studyspace.enums.BookingStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端的预约信息（A-14 ~ A-19）
 * <p>在预约单的基础上补了座位号、自习室名、预约人昵称，前端不用再发三次请求。</p>
 * <p>canCheckIn / canCancel 是后端算好的"现在能不能操作"，前端直接控制按钮置灰，
 * 不用自己再写一遍时间判断（写错了就会出现"按钮能点但接口报错"）。</p>
 */
@Data
public class BookingVO {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String bookingNo;
    private Long userId;
    private String userName;
    private String userStudentNo;
    private Long seatId;
    private String seatNo;
    private String seatType;
    private Long roomId;
    private String roomName;
    private String building;
    private String startTime;
    private String endTime;
    /** PENDING_CHECKIN / CHECKED_IN / COMPLETED / CANCELLED / NO_SHOW */
    private String status;
    private String statusText;
    /** 签到码，本人或管理员可见 */
    private String checkInCode;
    private String checkInTime;
    private String checkOutTime;
    /** 签到截止时间 = 开始时间 + 15 分钟，前端可以显示倒计时 */
    private String checkInDeadline;
    private String cancelReason;
    /** 现在能不能签到（本人在签到窗口内、且状态是待签到） */
    private boolean canCheckIn;
    /** 现在能不能取消（本人或管理员、状态待签到、还没到开始时间） */
    private boolean canCancel;
    /** 现在取消会不会扣分（开始前 30 分钟以内） */
    private boolean cancelPenalty;
    /** 本次取消实际扣了多少分，0 表示没扣；只在取消接口的返回里有值 */
    private Integer penaltyPoints;
    private String createTime;

    public static BookingVO from(Booking booking, Seat seat, Room room, User owner) {
        BookingVO vo = new BookingVO();
        vo.setId(booking.getId());
        vo.setBookingNo(booking.getBookingNo());
        vo.setUserId(booking.getUserId());
        vo.setSeatId(booking.getSeatId());
        vo.setStartTime(format(booking.getStartTime()));
        vo.setEndTime(format(booking.getEndTime()));
        vo.setStatus(booking.getStatus());
        vo.setStatusText(BookingStatus.textOf(booking.getStatus()));
        vo.setCheckInCode(booking.getCheckInCode());
        vo.setCheckInTime(format(booking.getCheckInTime()));
        vo.setCheckOutTime(format(booking.getCheckOutTime()));
        vo.setCancelReason(booking.getCancelReason());
        vo.setCreateTime(format(booking.getCreateTime()));

        if (owner != null) {
            vo.setUserName(owner.getNickname());
            vo.setUserStudentNo(owner.getStudentNo());
        }
        if (seat != null) {
            vo.setSeatNo(seat.getSeatNo());
            vo.setSeatType(seat.getSeatType());
        }
        if (room != null) {
            vo.setRoomId(room.getId());
            vo.setRoomName(room.getName());
            vo.setBuilding(room.getBuilding());
        }
        return vo;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
