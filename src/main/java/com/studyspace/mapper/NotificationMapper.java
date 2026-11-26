package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.Notification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 通知 Mapper
 *
 * <p>查询走 MyBatis-Plus 的 Wrapper（列表、未读数都是条件查询）；
 * 三条"改已读"的语句手写，因为要带 {@code AND user_id = 我} 和 {@code AND is_read = 0}
 * 两个条件——前者防止改到别人的通知，后者让重复点击"已读"成为空操作，
 * 不会把 read_time 刷成最后一次点击的时间。</p>
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 单条标记已读
     * @return 受影响行数：0 表示这条通知不是你的、或者本来就已经读过了
     */
    @Update("""
            UPDATE notification
               SET is_read = 1,
                   read_time = #{readTime},
                   update_time = NOW()
             WHERE id = #{id}
               AND user_id = #{userId}
               AND is_read = 0
            """)
    int markRead(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("readTime") LocalDateTime readTime);

    /**
     * 全部标记已读
     * @return 本次真正由未读变成已读的条数，前端可以用来提示"已读 N 条"
     */
    @Update("""
            UPDATE notification
               SET is_read = 1,
                   read_time = #{readTime},
                   update_time = NOW()
             WHERE user_id = #{userId}
               AND is_read = 0
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readTime") LocalDateTime readTime);

    /** 未读数：消息中心角标就靠它，只查未读所以能走 idx_user_read 索引 */
    @Select("""
            SELECT COUNT(*)
            FROM notification
            WHERE user_id = #{userId}
              AND is_read = 0
            """)
    int countUnread(@Param("userId") Long userId);
}
