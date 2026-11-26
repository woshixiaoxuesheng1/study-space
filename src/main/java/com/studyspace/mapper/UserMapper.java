package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户 Mapper
 * <p>继承 BaseMapper 就有单表增删改查，不需要写 XML</p>
 *
 * <p>信用分的三条 UPDATE 用"数据库里直接加减"而不是"先查出来改好再存回去"：
 * 后者在管理员手动扣分和学生同时被定时任务判违约时，后写的一方会把先写的覆盖掉。
 * 放在这里而不是违约模块自己的 Mapper，是因为它们改的是 user 表，
 * 按"一个 Mapper 管一张表"的分法不该跨表写 SQL。</p>
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 扣信用分
     * <p>GREATEST(0, ...) 保证最低扣到 0 分，不会出现负分</p>
     */
    @Update("""
            UPDATE `user`
               SET credit_score = GREATEST(0, credit_score - #{points}),
                   update_time = NOW()
             WHERE id = #{userId}
            """)
    int decreaseCredit(@Param("userId") Long userId, @Param("points") int points);

    /**
     * 返还信用分（撤销违约时用）
     * <p>LEAST(maxScore, ...) 保证不会超过满分，比如撤销一次 10 分的违约不能让 95 分变成 105 分</p>
     */
    @Update("""
            UPDATE `user`
               SET credit_score = LEAST(#{maxScore}, credit_score + #{points}),
                   update_time = NOW()
             WHERE id = #{userId}
            """)
    int increaseCredit(@Param("userId") Long userId, @Param("points") int points, @Param("maxScore") int maxScore);

    /** 写入 / 清空禁止预约截止时间（传 null 表示解除限制） */
    @Update("""
            UPDATE `user`
               SET ban_until = #{banUntil},
                   update_time = NOW()
             WHERE id = #{userId}
            """)
    int updateBanUntil(@Param("userId") Long userId, @Param("banUntil") LocalDateTime banUntil);
}
