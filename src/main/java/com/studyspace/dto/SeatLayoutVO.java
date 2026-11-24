package com.studyspace.dto;

import lombok.Data;

import java.util.List;

/**
 * 座位布局（A-11）
 * <p>前端拿 rowCount / colCount 建网格，再按每个座位的 rowNo、colNo 往里放，
 * 空位置自然留白，不需要后端拼二维数组</p>
 */
@Data
public class SeatLayoutVO {

    private Long roomId;
    private String roomName;
    /** 最大行号 */
    private Integer rowCount;
    /** 最大列号 */
    private Integer colCount;
    /** 座位总数 */
    private Integer seatCount;
    private List<SeatVO> seats;
}
