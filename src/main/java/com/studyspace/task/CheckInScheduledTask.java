package com.studyspace.task;

import com.studyspace.service.CheckInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 签到相关的定时任务（F-14）
 *
 * <p>每分钟跑一次，两件事：</p>
 * <ul>
 *   <li>超过签到截止时间还是"待签到"的预约 → 判违约、扣 10 分、释放座位；</li>
 *   <li>已签到但过了结束时间的预约 → 自动完成，不用学生手动点签退。</li>
 * </ul>
 *
 * <p>为什么不放在一个事务里批量处理：一条出错不能影响其他条。
 * 这里循环调用 service 的单条方法，每次都是独立事务（Spring 代理生效）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInScheduledTask {

    private final CheckInService checkInService;

    /** 超时未签到自动判违约，座位立即释放给其他人 */
    @Scheduled(cron = "0 * * * * ?")
    public void releaseTimeoutBookings() {
        List<Long> ids = checkInService.findTimeoutBookingIds();
        if (ids.isEmpty()) {
            return;
        }
        log.info("定时任务：发现 {} 条超时未签到的预约，开始处理", ids.size());
        for (Long id : ids) {
            try {
                checkInService.markNoShow(id);
            } catch (Exception e) {
                log.error("定时任务：处理超时预约 {} 失败", id, e);
            }
        }
    }

    /** 到点自动结束，避免"一直使用中"把座位永久占住 */
    @Scheduled(cron = "0 * * * * ?")
    public void completeFinishedBookings() {
        List<Long> ids = checkInService.findFinishedBookingIds();
        if (ids.isEmpty()) {
            return;
        }
        log.info("定时任务：发现 {} 条已到结束时间的预约，自动完成", ids.size());
        for (Long id : ids) {
            try {
                checkInService.completeExpired(id);
            } catch (Exception e) {
                log.error("定时任务：自动完成预约 {} 失败", id, e);
            }
        }
    }
}
