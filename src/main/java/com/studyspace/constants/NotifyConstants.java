package com.studyspace.constants;

import java.time.Duration;

/**
 * 通知模块常量
 *
 * <p>交换机、队列、路由键的名字集中在这里，声明队列的配置类和发送消息的生产者读同一份，
 * 改名字只要动一处，不会出现"生产者发到 A 队列、消费者听着 B 队列"这种查半天的问题。</p>
 *
 * <p>命名沿用 RabbitMQ 官方教程的 study.* 前缀，装好管理台后一眼能看出这些是项目自己的资源。</p>
 */
public final class NotifyConstants {

    private NotifyConstants() {
    }

    // ------------------------------------------------------------------
    // 交换机与队列
    // ------------------------------------------------------------------

    /** 通知交换机：topic 类型，按路由键把消息分发到不同队列 */
    public static final String NOTIFY_EXCHANGE = "study.notify.exchange";

    /** 通知主队列：消费者真正监听的就是它 */
    public static final String NOTIFY_QUEUE = "study.notify.queue";

    /** 死信交换机：消费失败重试用尽的消息转到这里，不会被直接丢弃 */
    public static final String NOTIFY_DLX = "study.notify.dlx";

    /** 死信队列：没有消费者，只用来留存和排查，管理台里能看到堆积了多少条 */
    public static final String NOTIFY_DLQ = "study.notify.dlq";

    // ------------------------------------------------------------------
    // 路由键
    // ------------------------------------------------------------------

    /** 路由键前缀：具体事件在 NotifyEvent.routingKey() 里拼成 notify.booking.created 这种形式 */
    public static final String ROUTING_KEY_PREFIX = "notify.";

    /** 主队列的绑定规则：notify.# 匹配 notify 开头的所有键，新增事件类型不用再改绑定 */
    public static final String ROUTING_BINDING = "notify.#";

    /** 死信队列绑定键 */
    public static final String ROUTING_DLQ = "notify.dead";

    // ------------------------------------------------------------------
    // 字段长度与状态值（与 notification 表定义对齐）
    // ------------------------------------------------------------------

    /** notification.title varchar(100) */
    public static final int MAX_TITLE_LENGTH = 100;

    /** notification.content varchar(500) */
    public static final int MAX_CONTENT_LENGTH = 500;

    /** 未读 */
    public static final int UNREAD = 0;

    /** 已读 */
    public static final int READ = 1;

    // ------------------------------------------------------------------
    // 提醒任务的幂等键
    // ------------------------------------------------------------------

    /**
     * 已发送提醒的标记键前缀：notify:remind:{bookingId}
     *
     * <p>提醒任务每分钟扫描一次，但同一条预约只应该提醒一次。
     * 用 Redis 的 SET NX 做标记，比给 booking 表加一个 reminded 字段更轻——
     * 提醒只是一次性副作用，不值得为它在核心业务表上增加列。</p>
     */
    public static final String REMIND_KEY_PREFIX = "notify:remind:";

    /** 提醒标记的存活时间：预约结束后这个标记就没用了，留一天足够覆盖跨零点的情况 */
    public static final Duration REMIND_KEY_TTL = Duration.ofDays(1);

    /**
     * 已消费消息的标记键前缀：notify:message:{messageId}
     *
     * <p>RabbitMQ 是"至少一次"投递：消费者处理完但还没来得及 ack 时进程挂了，
     * 消息会被重新投递，通知就会重复入库。用消费端幂等把这种情况挡住。</p>
     */
    public static final String MESSAGE_KEY_PREFIX = "notify:message:";

    /** 消息去重标记存活 3 天，远大于消息在队列里可能停留的时间 */
    public static final Duration MESSAGE_KEY_TTL = Duration.ofDays(3);

    /**
     * 即将开始提醒的提前量（分钟）
     * <p>默认值与签到窗口的开放时间一致：签到窗口一开就提醒，学生随手就能签。</p>
     */
    public static final int DEFAULT_REMIND_MINUTES = 15;
}
