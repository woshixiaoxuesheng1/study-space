package com.studyspace.service;

import com.studyspace.dto.PageResult;
import com.studyspace.mq.BookingNotifyMessage;
import com.studyspace.vo.NotificationVO;

/**
 * 通知业务（消息中心）
 *
 * <p>分两块：</p>
 * <ul>
 *   <li>面向学生的读接口：分页列表、未读数、标记已读；</li>
 *   <li>面向 MQ 消费端的写入口：{@link #handleEvent}，把一条消息落成一条通知。</li>
 * </ul>
 *
 * <p>业务模块（预约、签到、违约）不会调这里的任何写方法，它们只发 MQ 消息——
 * 这样通知出问题时，业务链路完全不知情，也不会被拖慢。</p>
 */
public interface NotificationService {

    /** 我的通知列表，可按分类筛选、只看未读 */
    PageResult<NotificationVO> pageMine(Long userId, int page, int size, String type, Boolean unreadOnly);

    /** 未读条数，前端消息中心的小红点 */
    int unreadCount(Long userId);

    /** 单条标记已读，返回 false 表示这条不是你的、或本来就已读 */
    boolean markRead(Long notificationId, Long userId);

    /** 全部标记已读，返回本次真正由未读变已读的条数 */
    int markAllRead(Long userId);

    /**
     * 消费一条通知消息并落库（由 MQ 监听器调用）
     *
     * @return true 表示确实新增了一条通知；false 表示重复消息被忽略或事件类型不认识
     */
    boolean handleEvent(BookingNotifyMessage message);
}
