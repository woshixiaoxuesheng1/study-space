package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.Violation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 违约记录 Mapper（FR-05-05）
 *
 * <p>普通查询直接用 BaseMapper 的条件构造器，这里只写两条必须放在 SQL 里的：</p>
 * <ul>
 *   <li>按预约单查重：保证一张预约单最多一条违约记录；</li>
 *   <li>撤销：条件更新 {@code AND revoked = 0}，两个人同时点撤销只有一个人能成功。</li>
 * </ul>
 */
public interface ViolationMapper extends BaseMapper<Violation> {

    /**
     * 这张预约单是否已经记过违约
     * <p>超时扫描任务每 10 分钟跑一次，可能对同一条预约重复调用判定逻辑；
     * 插入前先问一句，从源头上保证"一份预约单至多产生一条违约记录"。</p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM violation
            WHERE booking_id = #{bookingId}
            """)
    long countByBooking(@Param("bookingId") Long bookingId);

    /**
     * 撤销违约：未撤销 → 已撤销
     * <p>撤销时间、操作人、追加了撤销说明的 reason 一起写入；
     * {@code AND revoked = 0} 是幂等的关键——已经撤销过的记录再点一次会返回 0，
     * 不会出现"同一笔违约返还两次分数"。</p>
     *
     * @param id         违约记录 id
     * @param revokeTime 撤销时间
     * @param operatorId 操作的管理员 id
     * @param reason     追加撤销说明后的完整说明
     * @return 影响行数，0 表示记录不存在或已被撤销
     */
    @Update("""
            UPDATE violation
               SET revoked = 1,
                   revoke_time = #{revokeTime},
                   operator_id = #{operatorId},
                   reason = #{reason},
                   update_time = NOW()
             WHERE id = #{id}
               AND revoked = 0
            """)
    int markRevoked(@Param("id") Long id,
                    @Param("revokeTime") LocalDateTime revokeTime,
                    @Param("operatorId") Long operatorId,
                    @Param("reason") String reason);
}
