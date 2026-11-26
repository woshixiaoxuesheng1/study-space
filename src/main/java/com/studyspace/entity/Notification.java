package com.studyspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知表实体（消息中心）
 *
 * <p>这张表由 MQ 消费端写入，业务方（预约、签到、违约）只负责发消息，不直接插通知。
 * 通知一旦产生就不再修改内容，唯一会变的只有 is_read / read_time 两个字段。</p>
 *
 * <p>表里没有 deleted 字段，所以这里也不加 @TableLogic：
 * 通知是用户自己的数据，删除就是真删，逻辑删除反而会在"未读数"里留下幽灵记录。</p>
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 id，查询时永远按它过滤，学生看不到别人的通知 */
    private Long userId;

    /** 标题，由 NotifyEvent 决定，不超过 100 字 */
    private String title;

    /** 正文，由消息内容拼成，不超过 500 字（超出会截断） */
    private String content;

    /** 分类：BOOKING / VIOLATION / CREDIT / SYSTEM，见 NotifyType */
    private String type;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer isRead;

    /** 阅读时间，未读时为 null */
    private LocalDateTime readTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
