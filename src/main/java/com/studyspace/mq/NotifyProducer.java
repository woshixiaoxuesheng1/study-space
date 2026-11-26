package com.studyspace.mq;

import com.studyspace.constants.NotifyConstants;
import com.studyspace.enums.NotifyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 通知消息生产者
 *
 * <p>业务代码只调这一个类，不直接碰 RabbitTemplate，好处是"发消息"这件事的纪律
 * （提交后发、失败不抛）集中在一处，不会有人图省事在事务里直接 convertAndSend。</p>
 *
 * <p>两条纪律，缺一不可：</p>
 * <ol>
 *   <li><b>事务提交后才发</b>：预约创建、取消、判违约都在事务里。如果在事务中途发消息，
 *       后面任何一步抛异常导致回滚，消息却已经进了队列，消费者照样落一条通知，
 *       学生就会收到"预约成功"但数据库里根本没有这条预约——这种 bug 查起来极痛苦。</li>
 *   <li><b>投递失败不抛异常</b>：通知属于"锦上添花"。RabbitMQ 挂了、网络抖了，
 *       不应该让一次本来能成功的预约卡在"预约失败"上。降级成一条错误日志即可，
 *       通知丢了总比业务挂了强。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 投递通知：当前有事务就等提交后再发，没有事务就立即发
     *
     * <p>{@link TransactionSynchronizationManager#registerSynchronization} 注册的回调
     * 只在事务真正提交后触发；事务回滚时 afterCommit 不会执行，消息自然也不会发出去。</p>
     */
    public void publishAfterCommit(BookingNotifyMessage message) {
        if (message == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(message);
                }
            });
            return;
        }
        publish(message);
    }

    /**
     * 立即投递
     * <p>路由键由事件枚举推导（{@code notify.booking.created} 这种），
     * 交换机名从常量取，两处都写死字符串迟早会对不上。</p>
     */
    public void publish(BookingNotifyMessage message) {
        NotifyEvent event = NotifyEvent.of(message.getEvent());
        if (event == null) {
            log.warn("未知的通知事件，跳过投递：event={}", message.getEvent());
            return;
        }
        try {
            rabbitTemplate.convertAndSend(NotifyConstants.NOTIFY_EXCHANGE, event.routingKey(), message);
            log.info("通知已投递：event={}, userId={}, bookingId={}, messageId={}",
                    event, message.getUserId(), message.getBookingId(), message.getMessageId());
        } catch (Exception e) {
            log.error("通知投递失败，业务流程不受影响：event={}, userId={}, 原因={}",
                    event, message.getUserId(), e.getMessage());
        }
    }
}
