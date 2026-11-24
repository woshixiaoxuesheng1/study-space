package com.studyspace.dto;

import com.studyspace.entity.Seat;
import lombok.Data;

/**
 * 返回给前端的座位信息（A-11 / A-13）
 * <p>status 是"当前这一刻"的显示状态，由预约表算出来，不完全等于 seat 表里存的字段</p>
 */
@Data
public class SeatVO {

    private Long id;
    private Long roomId;
    private String seatNo;
    private Integer rowNo;
    private Integer colNo;
    private String seatType;
    /** FREE 空闲 / BOOKED 已预约 / IN_USE 使用中 / DISABLED 停用 */
    private String status;
    /** 状态中文，前端直接显示，不用再写一遍映射 */
    private String statusText;

    public static SeatVO from(Seat seat, String status) {
        SeatVO vo = new SeatVO();
        vo.setId(seat.getId());
        vo.setRoomId(seat.getRoomId());
        vo.setSeatNo(seat.getSeatNo());
        vo.setRowNo(seat.getRowNo());
        vo.setColNo(seat.getColNo());
        vo.setSeatType(seat.getSeatType());
        vo.setStatus(status);
        vo.setStatusText(text(status));
        return vo;
    }

    private static String text(String status) {
        return switch (status == null ? "FREE" : status) {
            case "BOOKED" -> "已预约";
            case "IN_USE" -> "使用中";
            case "DISABLED" -> "停用";
            default -> "空闲";
        };
    }
}
