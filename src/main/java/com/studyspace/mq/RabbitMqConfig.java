package com.studyspace.mq;

import com.studyspace.constants.NotifyConstants;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：连接、模板、监听容器、交换机队列绑定
 *
 * <p><b>为什么这些要手写，而不是靠 Spring Boot 自动配置？</b>
 * Spring Boot 4 按 AMQP 协议把集成拆成了两条线：</p>
 * <ul>
 *   <li>{@code spring-boot-starter-amqp-rabbitmq} —— AMQP 1.0，自动配置 {@code RabbitAmqpTemplate}；</li>
 *   <li>{@code spring-boot-starter-amqp} —— 只带 AMQP 的通用抽象，<b>不含</b> RabbitMQ 0-9-1 客户端；</li>
 * </ul>
 * <p>而传统项目里用的 {@code RabbitTemplate}（{@code spring-rabbit}，AMQP 0-9-1）在 Boot 4.2 里
 * 已经没有对应的自动配置类了。所以这里显式声明连接工厂、RabbitTemplate、
 * 监听容器工厂和 RabbitAdmin，一共几十行，换来的好处是走的仍是那套最通用、
 * 资料最全的 API，升级或迁移时不会因为"自动配置默认值"踩坑。</p>
 *
 * <p><b>@EnableRabbit 必须加：</b>它负责注册 {@code RabbitListenerAnnotationBeanPostProcessor}，
 * 没有它 {@code @RabbitListener} 只是一个不会被谁来处理的注解——表现是消息明明进了队列
 * （管理台里看得见 ready=1），但 consumers 一直是 0，业务方法一次都没被调用。
 * 在 Boot 4.2 之前这个注解由 Boot 的自动配置代劳，现在得自己写。</p>
 *
 * <p><b>拓扑结构：</b></p>
 * <pre>
 *                                routing key: notify.booking.created / notify.booking.cancelled ...
 *   NotifyProducer ──► study.notify.exchange (topic)
 *                              │  绑定 notify.#
 *                              ▼
 *                       study.notify.queue ──► NotifyConsumer ──► notification 表
 *                              │  消费失败、重试用尽
 *                              ▼  (dead-letter-exchange / dead-letter-routing-key)
 *                       study.notify.dlx (direct) ──► study.notify.dlq（只留存，不消费）
 * </pre>
 *
 * <p>为什么用 topic 交换机：路由键是 {@code notify.<模块>.<动作>} 三段式，
 * 主队列绑定 {@code notify.#} 一次就能收下所有事件；将来把"违约类"单独拆一个队列，
 * 加一条 {@code notify.violation.*} 的绑定即可，生产者一行都不用改。</p>
 *
 * <p>为什么要有死信队列：消费者处理消息时如果数据库正好不可用，
 * 消息会重试 3 次（次数在 application.yaml 的 {@code notify.consumer.*} 里配），
 * 仍然失败就转到死信队列留存。没有死信队列的话，这类消息要么被无限重投把队列堵死，
 * 要么被直接丢弃再也找不回来。</p>
 */
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    // ------------------------------------------------------------------
    // 连接与模板
    // ------------------------------------------------------------------

    /**
     * 连接工厂
     * <p>{@link CachingConnectionFactory} 会缓存 channel 和连接，不是每次发消息都重新握手。
     * 参数直接读 {@code spring.rabbitmq.*}，保持和 Spring Boot 的标准属性名一致，
     * 以后 Boot 恢复了自动配置也能无缝切回去。</p>
     */
    @Bean
    public ConnectionFactory rabbitConnectionFactory(
            @Value("${spring.rabbitmq.host:127.0.0.1}") String host,
            @Value("${spring.rabbitmq.port:5672}") int port,
            @Value("${spring.rabbitmq.username:guest}") String username,
            @Value("${spring.rabbitmq.password:guest}") String password,
            @Value("${spring.rabbitmq.virtual-host:/}") String virtualHost) {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        return factory;
    }

    /** 发送端模板：生产者用它把消息投到交换机，序列化方式必须和消费端一致 */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter());
        return template;
    }

    /**
     * 管理端：应用启动连上 broker 后，自动创建下面声明的交换机、队列和绑定
     * <p>已存在且属性一致时是空操作，所以重复启动不会报错；
     * 属性不一致（比如队列原来没配死信、现在配了）时 broker 会报
     * {@code PRECONDITION_FAILED}，这时候需要去管理台把旧队列删掉让它重建。</p>
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    /**
     * 监听容器工厂
     * <p>{@code @RabbitListener} 默认就找这个名字的 bean，所以不用在注解上显式指定。</p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${notify.consumer.prefetch:10}") int prefetch,
            @Value("${notify.consumer.max-retries:2}") int maxRetries,
            @Value("${notify.consumer.initial-interval-ms:1000}") long initialInterval,
            @Value("${notify.consumer.multiplier:2.0}") double multiplier,
            @Value("${notify.consumer.max-interval-ms:10000}") long maxInterval) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter());
        // 一次预取 10 条，避免一个消费者把队列全揽走
        factory.setPrefetchCount(prefetch);
        // 关键：重试用尽后不把消息塞回队列。队列配了死信交换机，被拒绝的消息会转到死信队列；
        // 设成 true 的话失败消息会在主队列里无限循环，把后面的正常消息全堵住
        factory.setDefaultRequeueRejected(false);
        // 自动 ack：方法正常返回就确认；抛异常则走下面的重试拦截器
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        factory.setAdviceChain(retryAdvice(maxRetries, initialInterval, multiplier, maxInterval));
        return factory;
    }

    /**
     * 消费失败重试
     * <p>无状态重试：不保存上下文，每次重试都重新调一次监听方法，
     * 对于"写一条通知"这种幂等操作完全够用，也不占内存。</p>
     *
     * <p>注意 Spring AMQP 4 的方法名是 {@code maxRetries}（首次之外的重试次数），
     * 不再是旧版本的 {@code maxAttempts}（总尝试次数）：
     * maxRetries(2) 表示首次 + 重试 2 次 = 最多 3 次。</p>
     *
     * <p>重试次数用尽后由 {@link RejectAndDontRequeueRecoverer} 处理：
     * 抛一个 AmqpRejectAndDontRequeueException，让容器 reject 消息且不重回队列，
     * broker 随即把它按队列上的 dead-letter 配置转进死信队列。</p>
     */
    private Advice retryAdvice(int maxRetries, long initialInterval, double multiplier, long maxInterval) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(maxRetries)
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    // ------------------------------------------------------------------
    // 交换机与队列
    // ------------------------------------------------------------------

    /**
     * 通知主交换机
     * @param durable 持久化：broker 重启后交换机还在，不会出现"消息发不出去因为交换机没了"
     */
    @Bean
    public TopicExchange notifyExchange() {
        return new TopicExchange(NotifyConstants.NOTIFY_EXCHANGE, true, false);
    }

    /**
     * 通知主队列
     * <p>两个死信参数是关键：消息被 reject 且不重回队列时，broker 会把它转到
     * {@code study.notify.dlx} 并以 {@code notify.dead} 为路由键投递，最终落到死信队列。</p>
     */
    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NotifyConstants.NOTIFY_QUEUE)
                .deadLetterExchange(NotifyConstants.NOTIFY_DLX)
                .deadLetterRoutingKey(NotifyConstants.ROUTING_DLQ)
                .build();
    }

    /** 死信交换机：用 direct 就够了，失败消息只有一个去处 */
    @Bean
    public DirectExchange notifyDlx() {
        return new DirectExchange(NotifyConstants.NOTIFY_DLX, true, false);
    }

    /** 死信队列：故意不写消费者，消息停在这里等人工排查 */
    @Bean
    public Queue notifyDlq() {
        return QueueBuilder.durable(NotifyConstants.NOTIFY_DLQ).build();
    }

    /** 主队列绑定：notify.# 一次覆盖所有 notify 开头的事件键 */
    @Bean
    public Binding notifyBinding(Queue notifyQueue, TopicExchange notifyExchange) {
        return BindingBuilder.bind(notifyQueue).to(notifyExchange).with(NotifyConstants.ROUTING_BINDING);
    }

    /** 死信队列绑定到死信交换机 */
    @Bean
    public Binding notifyDlqBinding(Queue notifyDlq, DirectExchange notifyDlx) {
        return BindingBuilder.bind(notifyDlq).to(notifyDlx).with(NotifyConstants.ROUTING_DLQ);
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    /**
     * JSON 消息转换器
     *
     * <p>用 {@link JacksonJsonMessageConverter} 而不是 {@code Jackson2JsonMessageConverter}：
     * Spring Boot 4 默认用 Jackson 3（{@code tools.jackson.*} 包），
     * 名字里带 2 的那个转换器依赖 Jackson 2（{@code com.fasterxml.jackson.*}），
     * 本项目编译期并没有 Jackson 2，用它会在运行时报类找不到。</p>
     *
     * <p>消息体里的时间字段都是字符串，不依赖 JavaTime 模块，
     * 所以直接用无参构造的默认 JsonMapper 就够了。</p>
     */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
