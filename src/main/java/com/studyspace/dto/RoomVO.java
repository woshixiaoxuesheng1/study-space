package com.studyspace.dto;

import com.studyspace.entity.Room;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端自习室信息（A-06 / A-07）
 */
@Data
public class RoomVO {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String name;
    private String building;
    private Integer floor;
    /** 开放时间，如 08:00 */
    private String openTime;
    /** 关闭时间，如 22:00 */
    private String closeTime;
    private Integer seatCount;
    private String status;
    private String description;
    private String createTime;

    public static RoomVO from(Room room) {
        RoomVO vo = new RoomVO();
        vo.setId(room.getId());
        vo.setName(room.getName());
        vo.setBuilding(room.getBuilding());
        vo.setFloor(room.getFloor());
        vo.setOpenTime(format(room.getOpenTime()));
        vo.setCloseTime(format(room.getCloseTime()));
        vo.setSeatCount(room.getSeatCount() == null ? 0 : room.getSeatCount());
        vo.setStatus(room.getStatus());
        vo.setDescription(room.getDescription());
        vo.setCreateTime(format(room.getCreateTime()));
        return vo;
    }

    private static String format(LocalTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : DATE_TIME_FORMATTER.format(time);
    }
}
