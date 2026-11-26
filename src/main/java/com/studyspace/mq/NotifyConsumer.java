package com.studyspace.mq;

import com.studyspace.constants.NotifyConstants;
import com.studyspace.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 通知消费者
 *
 * <p>做得很薄——只把消息转给 service，一行业务逻辑都不写。
 * 好处是消费失败时的重试、死信这些运行期行为完全由 Spring AMQP 的容器控制，
 * 不会因为这里写了 try-catch 把异常吞掉，导致消息被"当作成功"而真正丢失。</p>
 *
 * <p><b>失败后会怎样：</b>handleEvent 抛异常 → 监听容器按 application.yaml 里的
 * {@code spring.rabbitmq.listener.simple.retry} 配置重试（默认 3 次，间隔递增）
 * → 仍然失败则拒绝消息且不重回队列 → 由队列的 dead-letter-exchange 参数转投死信队列
 * {@code study.notify.dlq} 留存，管理台里可以看到并手工处理。</p>
 *
 * <p>所以这个类里<b>绝对不要</b>写 {@code try { ... } catch (Exception e) { log.error(...) } }：
 * 一旦吞掉异常，容器认为消费成功并 ack，消息就彻底没了。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyConsumer {

    private final NotificationService notificationService;

    /**
     * 监听通知主队列
     * @param message 由 JacksonJsonMessageConverter 从 JSON 反序列化而来
     */
    @RabbitListener(queues = NotifyConstants.NOTIFY_QUEUE)
    public void onMessage(BookingNotifyMessage message) {
        log.info("收到通知消息：event={}, userId={}, bookingId={}, messageId={}",
                message.getEvent(), message.getUserId(), message.getBookingId(), message.getMessageId());
        notificationService.handleEvent(message);
    }
}
