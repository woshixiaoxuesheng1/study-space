package com.studyspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约表实体（F-09 ~ F-13）
 * <p>系统核心表。时间冲突判断不靠额外的时间片表，直接用 start_time / end_time 比较：
 * 新开始 &lt; 旧结束 且 新结束 &gt; 旧开始（需求说明 5.2）。</p>
 * <p>booking 表没有 deleted 字段，预约记录不允许删除，只能改状态，所以不加 @TableLogic。</p>
 */
@Data
@TableName("booking")
public class Booking {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预约编号，给学生看的，如 BK2026112516023347 */
    private String bookingNo;

    /** 预约人 id，取自登录令牌，不接受前端传值 */
    private Long userId;

    /** 座位 id */
    private Long seatId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：见 BookingStatus 枚举 */
    private String status;

    /** 签到码，4 位数字 */
    private String checkInCode;

    /** 实际签到时间 */
    private LocalDateTime checkInTime;

    /** 实际签退时间 */
    private LocalDateTime checkOutTime;

    /** 取消原因 */
    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
