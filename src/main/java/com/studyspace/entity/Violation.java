package com.studyspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违约记录实体（F-15）
 * <p>预约模块里只在两种情况下写入：临近开始时间取消（LATE_CANCEL）、超时未签到（NO_SHOW）。
 * 管理员手动扣分（MANUAL）属于违约管理模块。</p>
 */
@Data
@TableName("violation")
public class Violation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 违约人 id */
    private Long userId;

    /** 关联预约 id，管理员手动扣分时为空 */
    private Long bookingId;

    /** 类型：NO_SHOW / LATE_CANCEL / MANUAL */
    private String type;

    /** 本次扣减信用分 */
    private Integer deductScore;

    /** 违约发生时间 */
    private LocalDateTime happenTime;

    /** 说明 */
    private String reason;

    /** 是否已撤销：0 否 / 1 是 */
    private Integer revoked;

    /** 撤销时间 */
    private LocalDateTime revokeTime;

    /** 操作人（撤销或手动扣分的管理员）id */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
