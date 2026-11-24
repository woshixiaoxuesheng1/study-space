package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.Seat;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 座位 Mapper
 */
public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 把座位这一行锁住，锁到事务提交才释放（SELECT ... FOR UPDATE）
     * <p>创建预约的第一步就调它：同一个座位上的第二个请求会在这里排队等，
     * 等第一个人提交后再去查"这段时间有没有人订"，查到的是最新数据，
     * 于是返回"已被预约"。这样不用 Redis，也能保证两个人不会订到同一个座位。</p>
     * <p>注意：自定义 SQL 不会被 MyBatis-Plus 自动追加逻辑删除条件，所以这里手写 deleted = 0。</p>
     */
    @Select("SELECT * FROM `seat` WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Seat selectByIdForUpdate(@Param("id") Long id);

    /**
     * 物理删除某自习室的全部座位
     * <p>为什么不用逻辑删除：seat 表有唯一索引 uk_room_seat_no(room_id, seat_no)，
     * 逻辑删除后旧行还在，重新生成同号座位会撞唯一索引。重新生成前已确认这些座位
     * 没有任何预约记录，物理删掉不会丢业务数据。</p>
     */
    @Delete("DELETE FROM `seat` WHERE room_id = #{roomId}")
    int physicalDeleteByRoomId(@Param("roomId") Long roomId);
}
