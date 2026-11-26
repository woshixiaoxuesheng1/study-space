package com.studyspace.vo;

import com.studyspace.constants.CommonConstants;
import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.entity.User;
import com.studyspace.entity.Violation;
import com.studyspace.enums.ViolationType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 返回给前端的违约记录（FR-05-05）
 *
 * <p>在违约记录表的基础上补了"谁违约、哪次预约、坐哪个座位"，
 * 管理员在一张表里就能看清一条违约的来龙去脉，不用再点进预约详情里对照。</p>
 *
 * <p>违约人和操作人都只取昵称和学号，不把整个 User 对象丢给前端——
 * 实体里有 password 字段，直接序列化会把密码哈希泄露出去。</p>
 */
@Data
public class ViolationVO {

    private Long id;

    // ---------------- 违约人 ----------------
    private Long userId;
    /** 违约人学号，管理员核对身份用 */
    private String studentNo;
    /** 违约人昵称 */
    private String userName;

    // ---------------- 关联预约 ----------------
    /** 关联预约 id，管理员手动扣分时为空 */
    private Long bookingId;
    private String bookingNo;
    /** 座位号，如 A03；手动扣分没有座位信息时为 null */
    private String seatNo;
    /** 自习室名称 */
    private String roomName;
    /** 预约时间段，如 "2026-11-24 14:00 - 16:00" */
    private String bookingTime;

    // ---------------- 违约本身 ----------------
    /** NO_SHOW / LATE_CANCEL / MANUAL */
    private String type;
    /** 类型中文，前端直接显示 */
    private String typeText;
    /** 本次扣减的信用分 */
    private Integer deductScore;
    /** 违约发生时间 */
    private String happenTime;
    /** 违约说明（撤销后末尾会追加撤销说明） */
    private String reason;

    // ---------------- 撤销信息 ----------------
    /** 是否已撤销 */
    private boolean revoked;
    private String revokeTime;
    /** 撤销 / 手动扣分的操作管理员 id */
    private Long operatorId;
    private String operatorName;

    private String createTime;

    /**
     * 组装 VO
     *
     * @param violation 违约记录
     * @param violator  违约人，查不到时传 null
     * @param operator  操作管理员，系统自动判违约时为 null
     * @param booking   关联预约，手动扣分时为 null
     * @param seat      座位，预约或座位已被删除时为 null
     * @param room      自习室
     */
    public static ViolationVO from(Violation violation, User violator, User operator,
                                   Booking booking, Seat seat, Room room) {
        ViolationVO vo = new ViolationVO();
        vo.setId(violation.getId());
        vo.setUserId(violation.getUserId());
        vo.setBookingId(violation.getBookingId());
        vo.setType(violation.getType());
        vo.setTypeText(ViolationType.textOf(violation.getType()));
        vo.setDeductScore(violation.getDeductScore());
        vo.setHappenTime(format(violation.getHappenTime()));
        vo.setReason(violation.getReason());
        vo.setRevoked(violation.getRevoked() != null && violation.getRevoked() == 1);
        vo.setRevokeTime(format(violation.getRevokeTime()));
        vo.setOperatorId(violation.getOperatorId());
        vo.setCreateTime(format(violation.getCreateTime()));

        if (violator != null) {
            vo.setStudentNo(violator.getStudentNo());
            vo.setUserName(violator.getNickname());
        }
        if (operator != null) {
            vo.setOperatorName(operator.getNickname());
        }
        if (booking != null) {
            vo.setBookingNo(booking.getBookingNo());
            vo.setBookingTime(format(booking.getStartTime()) + " - " + formatTime(booking.getEndTime()));
        }
        if (seat != null) {
            vo.setSeatNo(seat.getSeatNo());
        }
        if (room != null) {
            vo.setRoomName(room.getName());
        }
        return vo;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : CommonConstants.DATE_TIME_FORMATTER.format(time);
    }

    /** 预约时间段只显示到分钟不够直观，这里统一成 HH:mm，前端拼在日期后面 */
    private static String formatTime(LocalDateTime time) {
        return time == null ? null : String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
