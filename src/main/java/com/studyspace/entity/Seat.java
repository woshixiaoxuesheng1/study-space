package com.studyspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 座位表实体（F-06 / F-07）
 * <p>row_no / col_no 给前端画座位图用；status 只用于页面展示，
 * 真正判断"能不能订"要去 booking 表查时间重叠，这一点在需求说明 3.3 里写得很清楚。</p>
 */
@Data
@TableName("seat")
public class Seat {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属自习室 id */
    private Long roomId;

    /** 座位号，如 A01 */
    private String seatNo;

    /** 第几行，从 1 开始 */
    private Integer rowNo;

    /** 第几列，从 1 开始 */
    private Integer colNo;

    /** 座位类型：NORMAL 普通 / WINDOW 靠窗 / POWER 带插座 */
    private String seatType;

    /** 显示状态：FREE / BOOKED / IN_USE / DISABLED */
    private String status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
