package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.Booking;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到签退 Mapper（F-12 ~ F-14）
 *
 * <p>预约模块的 BookingMapper 只管"下单"：查冲突、插预约、改取消状态；
 * 签到相关的四条状态流转和两条定时扫描 SQL 都放这里，两个模块互不掺杂。</p>
 *
 * <p>所有 UPDATE 都带 {@code AND status = 原状态}，相当于乐观锁：
 * 用户点签退的同时定时任务也在改同一条预约，只有一个人能改成功，不会把状态覆盖乱。</p>
 */
public interface CheckInMapper extends BaseMapper<Booking> {

    /**
     * 签到：待签到 → 使用中
     * <p>顺手把签到码置空：签到码一次性使用，签完就作废，
     * 就算截图被别人看到也签不了第二次。</p>
     */
    @Update("""
            UPDATE booking
               SET status = 'CHECKED_IN',
                   check_in_time = #{time},
                   check_in_code = NULL,
                   update_time = NOW()
             WHERE id = #{id}
               AND status = 'PENDING_CHECKIN'
            """)
    int markCheckedIn(@Param("id") Long id, @Param("time") LocalDateTime time);

    /**
     * 签退 / 到点自动结束：使用中 → 已完成
     * <p>状态一变，座位就不再被这张预约占用了（冲突查询只认待签到和使用中），
     * 所以提前签退等于立刻把座位还回去，不需要再改 seat 表。</p>
     */
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
