package com.studyspace.service;

import com.studyspace.entity.User;

/**
 * 信用分与违约记录（F-15）
 *
 * <p>签到超时和临近开始取消都要"扣分 + 写违约记录"，两处逻辑一模一样，
 * 抽成一个服务，谁要用谁来调，避免两个模块各写一份、规则改了一次改两处。</p>
 */
public interface CreditService {

    /**
     * 扣信用分并写一条违约记录
     * <p>扣到 60 分以下时自动写入 ban_until = 现在 + 7 天，到期自然失效，
     * 不需要额外的"解禁"定时任务。</p>
     *
     * @param user      被扣分的用户（调用方已经查出来的对象）
     * @param points    扣多少分
     * @param bookingId 关联的预约 id
     * @param type      违约类型，见 BookingConstants.VIOLATION_*
     * @param reason    给用户看的违约原因
     */
    void deduct(User user, int points, Long bookingId, String type, String reason);
}
