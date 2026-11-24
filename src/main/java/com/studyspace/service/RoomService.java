package com.studyspace.service;

import com.studyspace.dto.PageResult;
import com.studyspace.dto.RoomRequest;
import com.studyspace.vo.RoomVO;
import com.studyspace.dto.SeatBatchRequest;
import com.studyspace.vo.SeatLayoutVO;
import com.studyspace.vo.SeatVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 自习室与座位相关业务（F-05 / F-06 / F-07 / F-08）
 */
public interface RoomService {

    /** 自习室列表，带分页和状态、关键字筛选（A-06） */
    PageResult<RoomVO> pageRooms(int page, int size, String status, String keyword);

    /** 自习室详情（A-07） */
    RoomVO getRoom(Long roomId);

    /** 新增自习室（A-08，管理员） */
    RoomVO createRoom(RoomRequest request);

    /** 修改自习室（A-09，管理员） */
    RoomVO updateRoom(Long roomId, RoomRequest request);

    /** 删除自习室，逻辑删除并连带删掉座位（A-10，管理员） */
    void deleteRoom(Long roomId);

    /** 座位布局和当前状态（A-11） */
    SeatLayoutVO getSeatLayout(Long roomId);

    /** 按几行几列批量生成座位（A-12，管理员） */
    SeatLayoutVO generateSeats(Long roomId, SeatBatchRequest request);

    /** 查某天某时段的空闲座位（A-13） */
    List<SeatVO> listFreeSeats(Long roomId, LocalDate date, String startTime, String endTime);
}
