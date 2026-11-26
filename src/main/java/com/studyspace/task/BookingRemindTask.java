package com.studyspace.task;

import com.studyspace.constants.NotifyConstants;
import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.mapper.BookingMapper;
import com.studyspace.mapper.RoomMapper;
import com.studyspace.mapper.SeatMapper;
import com.studyspace.mq.NotifyMessages;
import com.studyspace.mq.NotifyProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预约开始前的签到提醒（F-15 通知模块）
 *
 * <p>每分钟扫一次"再过 15 分钟就要开始、且还没签到"的预约，给每个人投一条提醒到 MQ，
 * 由消费者落成站内通知。提醒里带签到码，学生看到通知就能直接签，不用再翻订单。</p>
 *
 * <p><b>为什么用定时扫描，而不是 RabbitMQ 的延迟队列？</b>
 * 延迟消息的两种常见做法在这里都有硬伤：</p>
 * <ul>
 *   <li><b>TTL + 死信队列</b>：RabbitMQ 只检查队头消息是否过期。先投一条"明天上午"的预约
 *       （TTL 二十多个小时），再投一条"今天下午"的（TTL 两小时），后者会被前者的 TTL 堵在队列里，
 *       等十几个小时才轮到它 —— 提醒直接失效。</li>
 *   <li><b>延迟插件</b>：要额外装 {@code rabbitmq_delayed_message_exchange} 插件，
 *       换台机器部署就得再装一遍，对环境要求变高。</li>
 * </ul>
 * <p>定时扫描没有这两个问题：逻辑一眼看得懂、日志里能看到每轮扫到几条、
 * 不依赖任何插件。扫描的"重复执行"风险用 Redis 的 SET NX 标记兜住。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingRemindTask {

    private final BookingMapper bookingMapper;
    private final SeatMapper seatMapper;
    private final RoomMapper roomMapper;
    private final NotifyProducer notifyProducer;
    /** 只用来存"这条预约已经提醒过"的标记，避免同一分钟重复扫描造成重复提醒 */
    private final StringRedisTemplate redis;

    /** 提前多少分钟提醒 */
    @Value("${notify.remind-minutes:15}")
    private int remindMinutes;
    /** 签到窗口起点：开始前多少分钟（提醒文案里要告诉学生几点能签） */
    @Value("${booking.check-in-early-minutes:15}")
    private int checkInEarlyMinutes;
    /** 签到窗口终点：开始后多少分钟 */
    @Value("${booking.check-in-late-minutes:15}")
    private int checkInLateMinutes;

    /** 每分钟第 0 秒执行 */
    @Scheduled(cron = "0 * * * * ?")
    public void remindUpcoming() {
        // 先截断到整分钟再算区间：任务实际执行时刻可能带毫秒（10:00:00.037），
        // 不截断的话 [10:15:00.037, 10:16:00.037) 会漏掉正好 10:15:00 开始的那条预约
        LocalDateTime from = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(remindMinutes);
        LocalDateTime to = from.plusMinutes(1);

        List<Booking> upcoming = bookingMapper.selectUpcoming(from, to);
        if (upcoming.isEmpty()) {
            return;
        }
        log.info("定时任务：发现 {} 条即将开始的预约，准备投递签到提醒", upcoming.size());

        // 顺序、自习室批量查一次，避免每条预约单独查两次（N+1）
        Set<Long> seatIds = upcoming.stream().map(Booking::getSeatId).collect(Collectors.toSet());
        Map<Long, Seat> seatMap = seatIds.isEmpty() ? Map.of()
                : seatMapper.selectBatchIds(seatIds).stream().collect(Collectors.toMap(Seat::getId, s -> s));
        Set<Long> roomIds = seatMap.values().stream().map(Seat::getRoomId).collect(Collectors.toSet());
        Map<Long, Room> roomMap = roomIds.isEmpty() ? Map.of()
                : roomMapper.selectBatchIds(roomIds).stream().collect(Collectors.toMap(Room::getId, r -> r));

        for (Booking booking : upcoming) {
            if (!markReminded(booking.getId())) {
                log.info("该预约已提醒过，跳过：bookingId={}", booking.getId());
                continue;
            }
            Seat seat = seatMap.get(booking.getSeatId());
            Room room = seat == null ? null : roomMap.get(seat.getRoomId());
            try {
                notifyProducer.publish(NotifyMessages.remind(
                        booking, seat, room,
                        booking.getStartTime().minusMinutes(checkInEarlyMinutes),
                        booking.getStartTime().plusMinutes(checkInLateMinutes)));
            } catch (Exception e) {
                // 单条失败（理论上 publish 内部已经吞了异常，这里是最后一道保险）不能让整轮扫描中断
                log.error("投递签到提醒失败：bookingId={}", booking.getId(), e);
            }
        }
    }

    /**
     * 标记"这条预约已经提醒过"
     *
     * @return true 表示第一次提醒（抢到了标记），false 表示已经提醒过
     */
    private boolean markReminded(Long bookingId) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(
                    NotifyConstants.REMIND_KEY_PREFIX + bookingId, "1", NotifyConstants.REMIND_KEY_TTL);
            return !Boolean.FALSE.equals(first);
        } catch (RuntimeException e) {
            // Redis 不可用时退化成"照常提醒"：宁可极少数情况下重复一条，
            // 也不要因为缓存故障让所有提醒都发不出去
            log.warn("提醒标记写入失败，按未提醒处理：bookingId={}, 原因={}", bookingId, e.getMessage());
            return true;
        }
    }
}
