package com.studyspace.mapper;

import com.studyspace.dto.SeatOccupancy;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约相关查询（资源模块只用到"某座位某时段有没有被占"这一个能力，所以还不需要完整的 Booking 实体）
 * <p>重叠判断公式来自需求说明 5.2：新开始 &lt; 旧结束 且 新结束 &gt; 旧开始</p>
 */
public interface BookingMapper {

    /**
     * 查某自习室在指定时段被有效预约占用的座位
     * <p>只算"待签到"和"使用中"两种状态，已完成 / 已取消 / 违约都不占座</p>
     */
    @Select("""
            SELECT b.seat_id AS seatId, b.status AS status
            FROM booking b
            JOIN seat s ON s.id = b.seat_id AND s.deleted = 0
            WHERE s.room_id = #{roomId}
              AND b.status IN ('PENDING_CHECKIN', 'CHECKED_IN')
              AND b.start_time < #{endTime}
              AND b.end_time > #{startTime}
            """)
    List<SeatOccupancy> selectOccupancy(@Param("roomId") Long roomId,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /** 某自习室有没有"待签到 / 使用中"的预约，删除自习室前要问一句 */
    @Select("""
            SELECT COUNT(*)
            FROM booking b
            JOIN seat s ON s.id = b.seat_id
            WHERE s.room_id = #{roomId}
              AND b.status IN ('PENDING_CHECKIN', 'CHECKED_IN')
            """)
    long countActiveByRoom(@Param("roomId") Long roomId);

    /** 某自习室历史上有没有任何预约记录，重新生成座位前要问一句 */
    @Select("""
            SELECT COUNT(*)
            FROM booking b
            JOIN seat s ON s.id = b.seat_id
            WHERE s.room_id = #{roomId}
            """)
    long countAllByRoom(@Param("roomId") Long roomId);
}
