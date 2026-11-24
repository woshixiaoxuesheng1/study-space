package com.studyspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 自习室表实体（F-05 / F-07）
 */
@Data
@TableName("room")
public class Room {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 自习室名称，同一楼栋下不重复 */
    private String name;

    /** 楼栋，如"图书馆" */
    private String building;

    /** 楼层 */
    private Integer floor;

    /** 每日开放时间 */
    private LocalTime openTime;

    /** 每日关闭时间 */
    private LocalTime closeTime;

    /** 座位总数，生成座位后回写 */
    private Integer seatCount;

    /** 状态：OPEN 开放 / CLOSED 关闭 / MAINTENANCE 维护中 */
    private String status;

    /** 备注说明 */
    private String description;

    /**
     * 逻辑删除标记
     * <p>application.yaml 里已配置 logic-delete-field，这里再标一次是为了读代码时一眼看出
     * "删除自习室其实是改标记"，数据还能查到，方便答辩时解释为什么删了还能在数据库里看到</p>
     */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
