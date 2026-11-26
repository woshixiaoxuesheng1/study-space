package com.studyspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyspace.constants.CommonConstants;
import com.studyspace.constants.NotifyConstants;
import com.studyspace.dto.PageResult;
import com.studyspace.entity.Notification;
import com.studyspace.enums.NotifyEvent;
import com.studyspace.enums.NotifyType;
import com.studyspace.mapper.NotificationMapper;
import com.studyspace.mq.BookingNotifyMessage;
import com.studyspace.service.NotificationService;
import com.studyspace.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 通知业务实现
 *
 * <p>写入口 {@link #handleEvent} 是 MQ 消费端唯一调用的方法，它做了三件事，
 * 顺序不能换：</p>
 * <ol>
 *   <li><b>认事件</b>：消息里的 event 转不成枚举说明是脏数据，记日志跳过，不能让消费者抛异常；</li>
 *   <li><b>去重</b>：用 messageId 在 Redis 里占位。RabbitMQ 是"至少一次"投递，
 *       消费者处理完还没 ack 就挂掉的话消息会重投，不去重就会出现两条一模一样的通知；</li>
 *   <li><b>渲染并落库</b>：正文按事件类型拼，长度超过 500 字截断（对齐表字段长度）。</li>
 * </ol>
 *
 * <p>去重标记的补偿：如果落库失败，必须把刚写下的 Redis 标记删掉再抛异常。
 * 不删的话消息重投时会被当成"已处理过"，这条通知就永久丢失了。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    /** 只用来存消息去重标记，不参与业务数据 */
    private final StringRedisTemplate redis;

    // ------------------------------------------------------------------
    // 学生侧读接口
    // ------------------------------------------------------------------

    @Override
    public PageResult<NotificationVO> pageMine(Long userId, int page, int size, String type, Boolean unreadOnly) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), CommonConstants.MAX_PAGE_SIZE);
        boolean filterType = StringUtils.hasText(type) && NotifyType.isValid(type);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(filterType, Notification::getType, type)
                .eq(Boolean.TRUE.equals(unreadOnly), Notification::getIsRead, NotifyConstants.UNREAD)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId);

        Long total = notificationMapper.selectCount(wrapper);
        if (total == null || total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        // 与其他模块保持一致：没引分页插件，LIMIT 由后端算好拼进 SQL
        wrapper.last("LIMIT " + (long) (safePage - 1) * safeSize + ", " + safeSize);
        List<NotificationVO> list = notificationMapper.selectList(wrapper).stream()
                .map(NotificationVO::from)
                .toList();
        return new PageResult<>(total, safePage, safeSize, list);
    }

    @Override
    public int unreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long notificationId, Long userId) {
        // 条件更新里带了 user_id，改不到别人的通知；带 is_read = 0，重复点击不会刷新 read_time
        return notificationMapper.markRead(notificationId, userId, LocalDateTime.now()) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId, LocalDateTime.now());
    }

    // ------------------------------------------------------------------
    // MQ 消费入口
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleEvent(BookingNotifyMessage message) {
        if (message == null) {
            log.warn("收到空通知消息，已忽略");
            return false;
        }
        NotifyEvent event = NotifyEvent.of(message.getEvent());
        if (event == null) {
            log.warn("通知事件类型无法识别，已忽略：event={}, messageId={}",
                    message.getEvent(), message.getMessageId());
            return false;
        }
        if (message.getUserId() == null) {
            log.warn("通知缺少接收人，已忽略：event={}, messageId={}", event, message.getMessageId());
            return false;
        }

        String dedupKey = dedupKey(message.getMessageId());
        if (dedupKey != null && !tryMarkConsumed(dedupKey)) {
            log.info("通知消息已处理过，跳过：messageId={}", message.getMessageId());
            return false;
        }

        try {
            Notification notification = new Notification();
            notification.setUserId(message.getUserId());
            notification.setTitle(truncate(event.getTitle(), NotifyConstants.MAX_TITLE_LENGTH));
            notification.setContent(truncate(render(event, message), NotifyConstants.MAX_CONTENT_LENGTH));
            notification.setType(event.getType().name());
            notification.setIsRead(NotifyConstants.UNREAD);
            notificationMapper.insert(notification);

            log.info("通知已入库：id={}, event={}, userId={}, bookingId={}",
                    notification.getId(), event, message.getUserId(), message.getBookingId());
            return true;
        } catch (RuntimeException e) {
            // 落库失败要让消息重投，所以异常继续往上抛；但去重标记必须撤销，
            // 否则重投的那次会被自己刚写的标记挡住，通知永远进不来
            releaseConsumed(dedupKey);
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // 去重
    // ------------------------------------------------------------------

    /** 没有 messageId 的老消息无法去重，返回 null 表示不做这一步 */
    private String dedupKey(String messageId) {
        return StringUtils.hasText(messageId) ? NotifyConstants.MESSAGE_KEY_PREFIX + messageId : null;
    }

    /**
     * 占位：SET NX 是原子操作，并发消费同一条消息时只有一个能抢到
     * @return true 抢到了（第一次见），false 已经被别人处理过
     */
    private boolean tryMarkConsumed(String key) {
        try {
            Boolean first = redis.opsForValue()
                    .setIfAbsent(key, "1", NotifyConstants.MESSAGE_KEY_TTL);
            return !Boolean.FALSE.equals(first);
        } catch (RuntimeException e) {
            // Redis 挂了不能连通知一起停摆：退化成"当成第一次见"，宁可极少数情况下重复一条，
            // 也不要整批通知都进不来
            log.warn("通知去重检查失败，按首次消费处理：key={}, 原因={}", key, e.getMessage());
            return true;
        }
    }

    private void releaseConsumed(String key) {
        if (key == null) {
            return;
        }
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("撤销通知去重标记失败（该消息重投时可能被误判为重复）：key={}, 原因={}",
                    key, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 正文渲染
    // ------------------------------------------------------------------

    /**
     * 按事件类型拼正文
     * <p>句式统一成「时间地点 + 发生了什么 + 接下来该做什么」，
     * 学生扫一眼就知道要不要行动，而不是读一段系统日志。</p>
     */
    private String render(NotifyEvent event, BookingNotifyMessage message) {
        String seat = StringUtils.hasText(message.getSeatLabel()) ? message.getSeatLabel() : "座位";
        String time = message.getTimeRange();
        String where = "【" + seat + "】" + (StringUtils.hasText(time) ? "（" + time + "）" : "");
        String code = message.getCheckInCode();
        String remark = message.getRemark();

        List<String> parts = new ArrayList<>();
        switch (event) {
            case BOOKING_CREATED -> {
                parts.add("您已成功预约" + where + "。");
                addSentence(parts, remark);
                if (StringUtils.hasText(code)) {
                    parts.add("签到码 " + code + "。");
                }
                parts.add("请按时到场，超过签到截止时间未签到将记为违约。");
            }
            case BOOKING_REMIND -> {
                parts.add("提醒您：预约的" + where + "即将开始。");
                addSentence(parts, remark);
                if (StringUtils.hasText(code)) {
                    parts.add("签到码 " + code + "。");
                }
                parts.add("请尽快到座位完成签到。");
            }
            case BOOKING_CANCELLED -> {
                parts.add("您的预约" + where + "已取消。");
                if (StringUtils.hasText(remark)) {
                    parts.add("原因：" + remark + "。");
                }
                parts.add(deductText(message.getDeductPoints(), message.getCreditScore()));
            }
            case BOOKING_NO_SHOW -> {
                parts.add("您未在签到窗口内签到，预约" + where + "已记为违约，座位已释放。");
                parts.add(deductText(message.getDeductPoints(), message.getCreditScore()));
                addSentence(parts, remark);
            }
            case BOOKING_COMPLETED -> {
                parts.add("您的预约" + where + "已正常结束，感谢使用。");
                parts.add("请记得带走随身物品。");
            }
            case CREDIT_CHANGED -> {
                parts.add("您的信用分发生变动");
                if (message.getCreditScore() != null) {
                    parts.add("，当前信用分 " + message.getCreditScore() + " 分");
                }
                parts.add("。");
                addSentence(parts, remark);
            }
        }
        return String.join("", parts);
    }

    /** 补一句说明，自动带句号；空内容直接跳过，避免正文出现"。。" */
    private void addSentence(List<String> parts, String text) {
        if (StringUtils.hasText(text)) {
            parts.add(text.endsWith("。") ? text : text + "。");
        }
    }

    /** 扣分说明：没扣分也要明确说出来，否则学生会以为扣了分只是没写 */
    private String deductText(Integer points, Integer creditScore) {
        if (points == null || points <= 0) {
            return "本次未扣除信用分。";
        }
        StringBuilder sb = new StringBuilder("本次扣除信用分 ").append(points).append(" 分");
        if (creditScore != null) {
            sb.append("，当前信用分 ").append(creditScore).append(" 分");
        }
        return sb.append("。").toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
