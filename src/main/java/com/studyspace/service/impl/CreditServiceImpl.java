package com.studyspace.service.impl;

import com.studyspace.constants.UserConstants;
import com.studyspace.entity.User;
import com.studyspace.entity.Violation;
import com.studyspace.mapper.UserMapper;
import com.studyspace.mapper.ViolationMapper;
import com.studyspace.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 信用分与违约记录实现（F-15）
 *
 * <p>规则写在 application.yaml 的 booking 段：低于 credit-limit 分禁止预约 ban-days 天。
 * 用 ban_until 存解禁时间，比用"再跑一个定时任务扫表解禁"简单，到点比较一下时间就自然失效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final UserMapper userMapper;
    private final ViolationMapper violationMapper;

    /** 信用分限制线，低于它不能预约 */
    @Value("${booking.credit-limit:60}")
    private int creditLimit;
    /** 信用分低于限制线后禁止预约多少天 */
    @Value("${booking.ban-days:7}")
    private int banDays;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(User user, int points, Long bookingId, String type, String reason) {
        if (user == null) {
            return;
        }
        int oldScore = user.getCreditScore() == null ? UserConstants.DEFAULT_CREDIT_SCORE : user.getCreditScore();
        int newScore = Math.max(0, oldScore - points);
        user.setCreditScore(newScore);
        if (newScore < creditLimit) {
            user.setBanUntil(LocalDateTime.now().plusDays(banDays));
        }
        userMapper.updateById(user);

        Violation violation = new Violation();
        violation.setUserId(user.getId());
        violation.setBookingId(bookingId);
        violation.setType(type);
        violation.setDeductScore(points);
        violation.setHappenTime(LocalDateTime.now());
        violation.setReason(reason);
        violation.setRevoked(0);
        violationMapper.insert(violation);

        log.info("信用分扣减：userId={}, {} -> {}（{}）", user.getId(), oldScore, newScore, type);
    }
}
