package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.dto.SeatOccupancy;
import com.studyspace.entity.Booking;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约 Mapper
 * <p>重叠判断公式来自需求说明 5.2：新开始 &lt; 旧结束 且 新结束 &gt; 旧开始，
 * 两个符号都是"小于/大于"，不能写成小于等于——12:00 结束、12:00 开始应该是合法接续。</p>
 * <p>状态更新统一带 "AND status = 原状态" 条件（类似乐观锁）：
 * 定时任务和用户操作同时改同一条预约时，只有一个人能改成功，不会出现状态互相覆盖。</p>
 */
public interface BookingMapper extends BaseMapper<Booking> {

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

    /** 这个座位在这段时间有没有被有效预约占住（创建预约的核心检查） */
    @Select("""
            SELECT COUNT(*)
            FROM booking
            WHERE seat_id = #{seatId}
              AND status IN ('PENDING_CHECKIN', 'CHECKED_IN')
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    long countSeatOverlap(@Param("seatId") Long seatId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /** 这个人在这段时间有没有别的有效预约（防止一人同时占两个座位） */
    @Select("""
            SELECT COUNT(*)
            FROM booking
            WHERE user_id = #{userId}
              AND status IN ('PENDING_CHECKIN', 'CHECKED_IN')
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    long countUserOverlap(@Param("userId") Long userId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /** 某人某一天已经订了几次（已取消的不算），用于每日预约次数限制 */
    @Select("""
            SELECT COUNT(*)
            FROM booking
            WHERE user_id = #{userId}
              AND status <> 'CANCELLED'
              AND start_time >= #{dayStart}
              AND start_time < #{dayEnd}
            """)
    long countUserBookingsOnDay(@Param("userId") Long userId,
                               @Param("dayStart") LocalDateTime dayStart,
                               @Param("dayEnd") LocalDateTime dayEnd);

    /** 取消：只有"待签到"能取消，条件写在 WHERE 里避免并发下重复取消 */
    @Update("""
            UPDATE booking
               SET status = 'CANCELLED',
                   cancel_reason = #{reason},
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 'PENDING_CHECKIN'
            """)
    int markCancelled(@Param("id") Long id, @Param("reason") String reason);

    /** 签到：待签到 → 使用中 */
    @Update("""
            UPDATE booking
               SET status = 'CHECKED_IN',
                   check_in_time = #{time},
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 'PENDING_CHECKIN'
            """)
    int markCheckedIn(@Param("id") Long id, @Param("time") LocalDateTime time);

    /** 签退 / 到点自动结束：使用中 → 已完成 */
    @Update("""
            UPDATE booking
               SET status = 'COMPLETED',
                   check_out_time = #{time},
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 'CHECKED_IN'
            """)
    int markCompleted(@Param("id") Long id, @Param("time") LocalDateTime time);

    /** 超时未签到：待签到 → 违约 */
    @Update("""
            UPDATE booking
               SET status = 'NO_SHOW',
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 'PENDING_CHECKIN'
            """)
    int markNoShow(@Param("id") Long id);

    /**
     * 找出超过签到截止时间还是"待签到"的预约
     * <p>签到截止 = 开始时间 + check-in-late-minutes，所以条件是 start_time &lt; now - late</p>
     */
    @Select("""
            SELECT id
            FROM booking
            WHERE status = 'PENDING_CHECKIN'
              AND start_time < #{deadline}
            ORDER BY start_time
            """)
    List<Long> selectTimeoutIds(@Param("deadline") LocalDateTime deadline);

    /** 找出已经签到、但结束时间已过的预约，交给定时任务自动完成 */
    @Select("""
            SELECT id
            FROM booking
            WHERE status = 'CHECKED_IN'
              AND end_time < #{now}
            ORDER BY end_time
            """)
    List<Long> selectFinishedIds(@Param("now") LocalDateTime now);
}
