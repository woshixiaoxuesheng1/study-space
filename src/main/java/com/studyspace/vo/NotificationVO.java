package com.studyspace.vo;

import com.studyspace.entity.Notification;
import com.studyspace.enums.NotifyType;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端的通知（消息中心列表）
 *
 * <p>字段名用 {@code read} 而不是 {@code isRead}：前端拿到的 JSON 是 {@code "read": false}，
 * Vue 里直接 {@code item.read} 判断样式，不用再记一次"接口里到底叫 isRead 还是 read"。</p>
 */
@Data
public class NotificationVO {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String title;
    private String content;
    /** BOOKING / VIOLATION / CREDIT / SYSTEM */
    private String type;
    /** 分类中文，前端做分组标题用 */
    private String typeText;
    /** 是否已读 */
    private boolean read;
    /** 阅读时间，未读时为 null */
    private String readTime;
    private String createTime;

    public static NotificationVO from(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setType(notification.getType());
        vo.setTypeText(NotifyType.textOf(notification.getType()));
        vo.setRead(notification.getIsRead() != null && notification.getIsRead() == 1);
        vo.setReadTime(format(notification.getReadTime()));
        vo.setCreateTime(format(notification.getCreateTime()));
        return vo;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
