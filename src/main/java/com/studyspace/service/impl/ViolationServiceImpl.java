package com.studyspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyspace.common.BusinessException;
import com.studyspace.common.ResultCode;
import com.studyspace.constants.CommonConstants;
import com.studyspace.constants.UserConstants;
import com.studyspace.dto.ManualDeductRequest;
import com.studyspace.dto.PageResult;
import com.studyspace.entity.Booking;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.entity.User;
import com.studyspace.entity.Violation;
import com.studyspace.enums.ViolationType;
import com.studyspace.mapper.BookingMapper;
import com.studyspace.mapper.RoomMapper;
import com.studyspace.mapper.SeatMapper;
import com.studyspace.mapper.UserMapper;
import com.studyspace.mapper.ViolationMapper;
import com.studyspace.service.ViolationService;
import com.studyspace.vo.CreditVO;
import com.studyspace.vo.ViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 违约与信用分业务实现（FR-05-05 / FR-05-06 / FR-05-07）
 *
 * <p>四条贯穿全类的约定：</p>
 * <ol>
 *   <li><b>一份预约单至多一条违约记录</b>：插入前按 bookingId 查重，
 *       定时扫描任务重复跑到同一条预约也不会扣两次分；</li>
 *   <li><b>分数用数据库原子加减</b>：{@code credit_score = GREATEST(0, credit_score - N)}，
 *       避免"先查后改再存"在并发下互相覆盖；</li>
 *   <li><b>撤销是逻辑撤销</b>：{@code revoked = 1} + 条件更新，不删记录、不返两次分；</li>
 *   <li><b>禁约到期自动失效</b>：只写 ban_until，不在读的时候跑"解禁任务"，
 *       时间一过比较一下就自然恢复。</li>
 * </ol>
 *
 * <p>信用分变动没有单独的流水表：每一次变动都由一条违约记录承载，
 * deduct_score 是扣的分、revoked 标记这笔分有没有还回去，两者合起来就是完整的流水。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViolationServiceImpl implements ViolationService {

    /** 违约说明最长字符数，与 violation.reason varchar(255) 对齐 */
    private static final int MAX_REASON_LENGTH = 255;

    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;
    private final BookingMapper bookingMapper;
    private final SeatMapper seatMapper;
    private final RoomMapper roomMapper;

    /** 信用分限制线，低于它不能预约（与预约模块读同一份配置） */
    @Value("${booking.credit-limit:60}")
    private int creditLimit;
    /** 低于限制线后禁约多少天 */
    @Value("${booking.ban-days:7}")
    private int banDays;
    /** 开始前多少分钟内取消算违约，只用于生成给学生看的原因文案 */
    @Value("${booking.cancel-free-minutes:15}")
    private int cancelFreeMinutes;
    /** 超时未签到扣多少分 */
    @Value("${violation.no-show-deduct:10}")
    private int noShowDeduct;
    /** 临近开始取消扣多少分 */
    @Value("${violation.late-cancel-deduct:10}")
    private int lateCancelDeduct;
    /** 管理员手动扣分单次上限 */
    @Value("${violation.manual-max-points:100}")
    private int manualMaxPoints;
    /** 信用分页展示最近多少天的违约明细 */
    @Value("${violation.recent-days:90}")
    private int recentDays;
    /** 信用分页最多带多少条明细 */
    @Value("${violation.recent-limit:20}")
    private int recentLimit;

    // ------------------------------------------------------------------
    // 记账：给预约 / 签到模块调用
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recordBookingViolation(Long userId, Long bookingId, ViolationType type) {
        if (userId == null || bookingId == null) {
            log.warn("记违约参数不完整，已跳过：userId={}, bookingId={}", userId, bookingId);
            return 0;
        }
        if (type == null || type == ViolationType.MANUAL) {
            // MANUAL 必须走管理员手动扣分入口，那里会校验操作人和扣分上限
            throw new BusinessException(ResultCode.PARAM_ERROR, "自动违约类型只能是 NO_SHOW 或 LATE_CANCEL");
        }
        // 幂等：一张预约单只记一条违约，定时任务重复扫描同一条预约时直接返回
        if (violationMapper.countByBooking(bookingId) > 0) {
            log.info("该预约已记过违约，跳过：bookingId={}", bookingId);
            return 0;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            // 用户被删除属于数据异常，记日志即可，不能让定时任务因此中断
            log.warn("违约人不存在，跳过记违约：userId={}, bookingId={}", userId, bookingId);
            return 0;
        }

        int points = pointsOf(type);
        if (points <= 0) {
            log.warn("违约扣分配置为 0，跳过记违约：type={}", type);
            return 0;
        }

        Violation violation = new Violation();
        violation.setUserId(userId);
        violation.setBookingId(bookingId);
        violation.setType(type.name());
        violation.setDeductScore(points);
        violation.setHappenTime(LocalDateTime.now());
        violation.setReason(reasonOf(type, points));
        violation.setRevoked(0);
        violationMapper.insert(violation);

        applyDeduct(userId, points);
        log.info("记违约成功：userId={}, bookingId={}, type={}, 扣分={}", userId, bookingId, type, points);
        return points;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Override
    public CreditVO myCredit(Long userId) {
        User user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now();
        int score = creditOf(user);
        LocalDateTime banUntil = user.getBanUntil();
        boolean banned = banUntil != null && banUntil.isAfter(now);
        boolean canBook = score >= creditLimit && !banned;

        // 最近 N 天的违约明细，撤销掉的不算：撤销意味着这次违约不成立，不该再挂在学生名下
        LocalDateTime since = now.minusDays(recentDays);
        LambdaQueryWrapper<Violation> wrapper = new LambdaQueryWrapper<Violation>()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getRevoked, 0)
                .ge(Violation::getHappenTime, since)
                .orderByDesc(Violation::getHappenTime)
                .orderByDesc(Violation::getId);

        Long count = violationMapper.selectCount(wrapper);
        int recentCount = count == null ? 0 : count.intValue();

        List<Violation> recent = List.of();
        if (recentCount > 0) {
            wrapper.last("LIMIT " + recentLimit);
            recent = violationMapper.selectList(wrapper);
        }

        CreditVO vo = new CreditVO();
        vo.setCreditScore(score);
        vo.setLimitScore(creditLimit);
        vo.setCanBook(canBook);
        vo.setBanUntil(banUntil == null ? null : format(banUntil));
        vo.setRecentViolationCount(recentCount);
        vo.setRecentViolations(assemble(recent));
        vo.setMessage(creditMessage(score, banned, banUntil, recentCount));
        return vo;
    }

    @Override
    public PageResult<ViolationVO> pageMine(Long userId, int page, int size, String type) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), CommonConstants.MAX_PAGE_SIZE);
        boolean filterType = StringUtils.hasText(type) && ViolationType.isValid(type);

        LambdaQueryWrapper<Violation> wrapper = new LambdaQueryWrapper<Violation>()
                .eq(Violation::getUserId, userId)
                .eq(filterType, Violation::getType, type)
                .orderByDesc(Violation::getHappenTime)
                .orderByDesc(Violation::getId);

        Long total = violationMapper.selectCount(wrapper);
        if (total == null || total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        wrapper.last("LIMIT " + (long) (safePage - 1) * safeSize + ", " + safeSize);
        return new PageResult<>(total, safePage, safeSize, assemble(violationMapper.selectList(wrapper)));
    }

    @Override
    public PageResult<ViolationVO> pageAll(int page, int size, String type, Integer revoked, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), CommonConstants.MAX_PAGE_SIZE);
        boolean filterType = StringUtils.hasText(type) && ViolationType.isValid(type);

        LambdaQueryWrapper<Violation> wrapper = new LambdaQueryWrapper<Violation>()
                .eq(filterType, Violation::getType, type)
                .eq(revoked != null, Violation::getRevoked, revoked);

        // 关键字搜的是用户表（学号或昵称），先查出命中的用户 id 再过滤违约记录。
        // 没命中任何一个用户时直接返回空页，不用再查一次 count
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .and(w -> w.like(User::getNickname, kw).or().like(User::getStudentNo, kw)))
                    .stream().map(User::getId).toList();
            if (matchedUserIds.isEmpty()) {
                return PageResult.empty(safePage, safeSize);
            }
            wrapper.in(Violation::getUserId, matchedUserIds);
        }

        wrapper.orderByDesc(Violation::getHappenTime).orderByDesc(Violation::getId);
        Long total = violationMapper.selectCount(wrapper);
        if (total == null || total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        wrapper.last("LIMIT " + (long) (safePage - 1) * safeSize + ", " + safeSize);
        return new PageResult<>(total, safePage, safeSize, assemble(violationMapper.selectList(wrapper)));
    }

    // ------------------------------------------------------------------
    // 纠错：撤销 / 手动扣分
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViolationVO revoke(Long violationId, Long operatorId, String reason) {
        Violation violation = violationMapper.selectById(violationId);
        if (violation == null) {
            throw new BusinessException(ResultCode.VIOLATION_NOT_FOUND);
        }
        if (violation.getRevoked() != null && violation.getRevoked() == 1) {
            throw new BusinessException(ResultCode.VIOLATION_ALREADY_REVOKED);
        }

        // 条件更新：两个人同时点撤销，只有一个能把 revoked 从 0 改成 1
        int rows = violationMapper.markRevoked(violationId, LocalDateTime.now(), operatorId,
                withRevokeNote(violation.getReason(), reason));
        if (rows == 0) {
            throw new BusinessException(ResultCode.VIOLATION_ALREADY_REVOKED);
        }

        // 返还信用分：撤销意味着这次违约不成立，扣掉的分要还回去（上限 100）
        int points = violation.getDeductScore() == null ? 0 : violation.getDeductScore();
        if (points > 0) {
            userMapper.increaseCredit(violation.getUserId(), points, UserConstants.DEFAULT_CREDIT_SCORE);
            User after = userMapper.selectById(violation.getUserId());
            // 分数回到限制线以上，禁约也就没有依据了，顺手解除
            if (after != null && creditOf(after) >= creditLimit && after.getBanUntil() != null) {
                userMapper.updateBanUntil(violation.getUserId(), null);
                log.info("撤销后信用分恢复，解除禁约：userId={}", violation.getUserId());
            }
        }

        log.info("撤销违约：violationId={}, 操作人={}, 返还分数={}", violationId, operatorId, points);
        return assemble(List.of(violationMapper.selectById(violationId))).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViolationVO manualDeduct(Long operatorId, ManualDeductRequest request) {
        User target = userMapper.selectById(request.getUserId());
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "被扣分的学生不存在");
        }
        if (UserConstants.ROLE_ADMIN.equals(target.getRole())) {
            // 管理员之间互相扣分没有意义，也容易变成"人情分"，直接拦住
            throw new BusinessException(ResultCode.PARAM_ERROR, "只能给学生扣分，不能扣管理员");
        }

        int points = request.getPoints();
        if (points > manualMaxPoints) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多扣 " + manualMaxPoints + " 分");
        }

        LocalDateTime happenTime = request.getHappenTime() == null ? LocalDateTime.now() : request.getHappenTime();
        if (happenTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "违约发生时间不能晚于当前时间");
        }

        Violation violation = new Violation();
        violation.setUserId(target.getId());
        violation.setBookingId(null);
        violation.setType(ViolationType.MANUAL.name());
        violation.setDeductScore(points);
        violation.setHappenTime(happenTime);
        violation.setReason(truncate(request.getReason().trim()));
        violation.setRevoked(0);
        violation.setOperatorId(operatorId);
        violationMapper.insert(violation);

        applyDeduct(target.getId(), points);
        log.info("管理员手动扣分：userId={}, 扣分={}, 操作人={}, 原因={}",
                target.getId(), points, operatorId, request.getReason());
        return assemble(List.of(violation)).get(0);
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    /** 按类型取配置的扣分值 */
    private int pointsOf(ViolationType type) {
        return switch (type) {
            case NO_SHOW -> noShowDeduct;
            case LATE_CANCEL -> lateCancelDeduct;
            case MANUAL -> 0;
        };
    }

    /** 自动违约的原因文案只在这里生成，两个调用方不用各写一份 */
    private String reasonOf(ViolationType type, int points) {
        return switch (type) {
            case NO_SHOW -> "超过签到截止时间仍未签到，座位自动释放，扣 " + points + " 分";
            case LATE_CANCEL -> "开始前 " + cancelFreeMinutes + " 分钟内取消预约，扣 " + points + " 分";
            case MANUAL -> "管理员手动扣分 " + points + " 分";
        };
    }

    /**
     * 扣分并视情况禁约
     * <p>先原子扣分，再读一次结果决定要不要写 ban_until——
     * 读到的分数是本事务刚改过的值，不会有并发覆盖问题。</p>
     */
    private void applyDeduct(Long userId, int points) {
        userMapper.decreaseCredit(userId, points);
        User after = userMapper.selectById(userId);
        if (after == null || creditOf(after) >= creditLimit) {
            return;
        }
        LocalDateTime banUntil = LocalDateTime.now().plusDays(banDays);
        // 已经有一个更长的禁约期就不缩短：连续违约时按"最后一次违约 + N 天"顺延
        if (after.getBanUntil() == null || after.getBanUntil().isBefore(banUntil)) {
            userMapper.updateBanUntil(userId, banUntil);
            log.info("信用分 {} 低于 {}，禁止预约至 {}：userId={}",
                    creditOf(after), creditLimit, banUntil, userId);
        }
    }

    /** 撤销说明追加到原违约原因后面，形成完整留痕；超出字段长度就截断 */
    private String withRevokeNote(String original, String note) {
        String base = original == null ? "" : original;
        String suffix = StringUtils.hasText(note) ? "；已撤销：" + note.trim() : "；已撤销";
        return truncate(base + suffix);
    }

    private String truncate(String text) {
        return text.length() <= MAX_REASON_LENGTH ? text : text.substring(0, MAX_REASON_LENGTH);
    }

    /** 信用分页的说明文字：能预约 / 禁约期内 / 分数不足三种情况 */
    private String creditMessage(int score, boolean banned, LocalDateTime banUntil, int recentCount) {
        if (!banned && score >= creditLimit) {
            return recentCount == 0
                    ? "信用分正常，可以预约座位"
                    : "信用分正常，最近 " + recentDays + " 天有 " + recentCount + " 次违约记录";
        }
        if (banned && banUntil != null) {
            return "信用分 " + score + " 分，处于限制预约期，将于 " + format(banUntil) + " 自动恢复";
        }
        return "信用分 " + score + " 分低于 " + creditLimit + " 分，暂时无法预约";
    }

    /**
     * 批量组装 VO
     * <p>违约人、操作人、预约、座位、自习室各查一次批量取回，
     * 一页 10 条记录只发 5 条 SQL，而不是每条记录查 5 次。</p>
     */
    private List<ViolationVO> assemble(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> bookingIds = new HashSet<>();
        for (Violation v : violations) {
            if (v.getUserId() != null) {
                userIds.add(v.getUserId());
            }
            if (v.getOperatorId() != null) {
                userIds.add(v.getOperatorId());
            }
            if (v.getBookingId() != null) {
                bookingIds.add(v.getBookingId());
            }
        }

        // 用 Collections.emptyMap() 而不是 Map.of()：operatorId 可能为 null，
        // Map.of() 返回的不可变集合 get(null) 会抛 NPE
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Booking> bookingMap = bookingIds.isEmpty() ? Collections.emptyMap()
                : bookingMapper.selectBatchIds(bookingIds).stream()
                .collect(Collectors.toMap(Booking::getId, b -> b));

        Set<Long> seatIds = bookingMap.values().stream()
                .map(Booking::getSeatId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Seat> seatMap = seatIds.isEmpty() ? Collections.emptyMap()
                : seatMapper.selectBatchIds(seatIds).stream()
                .collect(Collectors.toMap(Seat::getId, s -> s));

        Set<Long> roomIds = seatMap.values().stream()
                .map(Seat::getRoomId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Room> roomMap = roomIds.isEmpty() ? Collections.emptyMap()
                : roomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, r -> r));

        return violations.stream().map(v -> {
            Booking booking = v.getBookingId() == null ? null : bookingMap.get(v.getBookingId());
            Seat seat = booking == null || booking.getSeatId() == null ? null : seatMap.get(booking.getSeatId());
            Room room = seat == null || seat.getRoomId() == null ? null : roomMap.get(seat.getRoomId());
            User operator = v.getOperatorId() == null ? null : userMap.get(v.getOperatorId());
            return ViolationVO.from(v, userMap.get(v.getUserId()), operator, booking, seat, room);
        }).toList();
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private int creditOf(User user) {
        return user.getCreditScore() == null ? UserConstants.DEFAULT_CREDIT_SCORE : user.getCreditScore();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : CommonConstants.DATE_TIME_FORMATTER.format(time);
    }
}
