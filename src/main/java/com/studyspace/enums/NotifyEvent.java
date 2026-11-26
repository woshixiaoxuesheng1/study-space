package com.studyspace.enums;

import com.studyspace.constants.NotifyConstants;

import java.util.Locale;

/**
 * 通知事件类型
 *
 * <p>一条通知"是什么事"由这个枚举决定：标题、归类、路由键三样都从这里取，
 * 生产者只说"发生了 BOOKING_CREATED"，具体叫什么标题、发到哪个键交给枚举，
 * 新增一种事件只要在这里加一行，不用去改生产者和消费者的代码。</p>
 *
 * <p>路由键按 {@code notify.<模块>.<动作>} 三段式命名，例如 {@code notify.booking.created}。
 * 主队列绑定的是 {@code notify.#}，所以以后加 {@code notify.violation.*} 这种新模块，
 * 声明部分完全不用动，新消息自动进同一个队列。</p>
 */
public enum NotifyEvent {

    /** 预约创建成功 */
    BOOKING_CREATED("预约成功", NotifyType.BOOKING),
    /** 预约即将开始，提醒去签到 */
    BOOKING_REMIND("即将开始，记得签到", NotifyType.BOOKING),
    /** 预约被取消（学生主动或管理员代取消） */
    BOOKING_CANCELLED("预约已取消", NotifyType.BOOKING),
    /** 预约正常结束 */
    BOOKING_COMPLETED("预约已完成", NotifyType.BOOKING),
    /** 超时未签到，判违约 */
    BOOKING_NO_SHOW("违约提醒", NotifyType.VIOLATION),
    /** 信用分发生变动（扣分、撤销返还、解除禁约） */
    CREDIT_CHANGED("信用分变动", NotifyType.CREDIT);

    /** 通知标题，notification.title 直接用这个值 */
    private final String title;
    /** 通知归类，notification.type 用它的 name() */
    private final NotifyType type;

    NotifyEvent(String title, NotifyType type) {
        this.title = title;
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public NotifyType getType() {
        return type;
    }

    /**
     * 投递用的路由键
     * <p>把 BOOKING_CREATED 转成 notify.booking.created：
     * 全小写 + 下划线换成点，规则统一，队列绑定时不用逐个枚举键。</p>
     */
    public String routingKey() {
        return NotifyConstants.ROUTING_KEY_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '.');
    }

    /**
     * 字符串转枚举
     * <p>消息体里存的是枚举名字符串而不是枚举对象：跨进程传输时字符串最稳，
     * 消费端即使收到一个不认识的旧事件名，也只是返回 null 后被跳过，不会反序列化直接抛异常。</p>
     */
    public static NotifyEvent of(String value) {
        if (value == null) {
            return null;
        }
        for (NotifyEvent event : values()) {
            if (event.name().equals(value)) {
                return event;
            }
        }
        return null;
    }
}
