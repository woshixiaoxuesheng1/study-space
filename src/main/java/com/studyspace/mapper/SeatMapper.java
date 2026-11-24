package com.studyspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studyspace.entity.Seat;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 座位 Mapper
 */
public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 物理删除某自习室的全部座位
     * <p>为什么不用逻辑删除：seat 表有唯一索引 uk_room_seat_no(room_id, seat_no)，
     * 逻辑删除后旧行还在，重新生成同号座位会撞唯一索引。重新生成前已确认这些座位
     * 没有任何预约记录，物理删掉不会丢业务数据。</p>
     */
    @Delete("DELETE FROM `seat` WHERE room_id = #{roomId}")
    int physicalDeleteByRoomId(@Param("roomId") Long roomId);
}
