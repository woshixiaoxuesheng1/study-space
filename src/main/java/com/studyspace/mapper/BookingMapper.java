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
 * 预约 Mapper（F-09 ~ F-11）
 *
 * <p>只放"下单"一侧的 SQL：查冲突、查每日次数、锁座位、改取消状态。
 * 签到、签退、超时违约的更新语句在 CheckInMapper 里，两个模块各管各的。</p>
 *
 * <p>重叠判断公式来自需求说明 5.2：新开始 &lt; 旧结束 且 新结束 &gt; 旧开始，
 * 两个符号都是"小于/大于"，不能写成小于等于——12:00 结束、12:00 开始应该是合法接续。</p>
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

    /**
     * 找出"即将开始"的预约，给提醒任务用
     *
     * <p>条件是 start_time 落在 {@code [from, to)} 这个左闭右开的一分钟区间里。
     * 提醒任务每分钟跑一次、每次都把 now 截断到整分钟再算区间，
     * 所以连续两轮的区间首尾相接、不重不漏，每条预约只会被扫到一次。
     * 即使任务执行时刻有几十毫秒偏差，也不会因为小数点后的时间把边界预约漏掉。</p>
     *
     * <p>只要"待签到"的：已经签到的不需要提醒，已取消、已违约的更不需要。</p>
     */
    @Select("""
            SELECT *
            FROM booking
            WHERE status = 'PENDING_CHECKIN'
              AND start_time >= #{from}
              AND start_time < #{to}
            ORDER BY start_time
            """)
    List<Booking> selectUpcoming(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
