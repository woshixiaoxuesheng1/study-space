package com.studyspace.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预约通知消息体（在 RabbitMQ 里传输的就是它）
 *
 * <p>设计原则：<b>消息自包含</b>。生产者把渲染通知需要的字段一次性塞满，
 * 消费端拿到消息就能直接拼出通知，不需要再回数据库查一遍。</p>
 *
 * <p>为什么不只传 bookingId 让消费者自己查？两个原因：</p>
 * <ol>
 *   <li>消费者查库就多了一次依赖，MQ 的作用本来是把业务主流程和通知解开，
 *       结果消费端又去读业务库，等于没解开；</li>
 *   <li>消息在队列里可能停留一段时间，等被消费时预约可能已经改了状态，
 *       查出来的就不再是"事件发生那一刻"的样子。</li>
 * </ol>
 *
 * <p>时间统一用字符串（yyyy-MM-dd HH:mm:ss），不用 LocalDateTime：
 * 消息跨进程传输，字符串没有时区和序列化模块的歧义，管理台里看到的也是人能读的内容。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingNotifyMessage {

    /** 消息唯一标识（UUID），消费端用它做幂等：同一条消息重复投递只落一条通知 */
    private String messageId;

    /** 事件类型，存 NotifyEvent 的枚举名，消费端用 NotifyEvent.of() 转回来 */
    private String event;

    /** 接收人 id */
    private Long userId;

    /** 关联的预约 id，纯粹为了排查问题用 */
    private Long bookingId;

    /** 座位展示名，形如「图书馆三楼自习室 A02」 */
    private String seatLabel;

    /** 时段展示，形如「2026-11-26 15:00 - 17:00」 */
    private String timeRange;

    /** 签到码，只有"预约成功"类通知会带，其余为 null */
    private String checkInCode;

    /** 本次扣减的信用分，违约/迟到取消才有值 */
    private Integer deductPoints;

    /** 变动后的信用分，配合 deductPoints 让学生知道现在还剩多少 */
    private Integer creditScore;

    /** 补充说明：取消原因、违约原因、禁约截止时间等 */
    private String remark;

    /** 事件发生时间 */
    private String happenAt;
}
