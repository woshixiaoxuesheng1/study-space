package com.studyspace.service;

import com.studyspace.dto.ManualDeductRequest;
import com.studyspace.dto.PageResult;
import com.studyspace.enums.ViolationType;
import com.studyspace.vo.CreditVO;
import com.studyspace.vo.ViolationVO;

/**
 * 违约与信用分业务（FR-05-05 / FR-05-06 / FR-05-07）
 *
 * <p>职责分三块，边界很清楚：</p>
 * <ol>
 *   <li><b>记账</b>：{@link #recordBookingViolation} 给预约、签到两个模块调用，
 *       它们只说明"这条预约违约了、属于哪种类型"，扣多少分、要不要禁约、原因文案怎么写，
 *       全由本模块决定；</li>
 *   <li><b>查询</b>：学生查自己的信用分和违约明细，管理员查全量；</li>
 *   <li><b>纠错</b>：管理员撤销（返还分数）和手动扣分。</li>
 * </ol>
 *
 * <p>为什么把扣分逻辑收在这里：原来预约取消和超时未签到各写了一份"扣分 + 写记录"，
 * 规则一改要动两处，还容易一边改了另一边忘了。现在两处都只调 {@code recordBookingViolation}。</p>
 */
public interface ViolationService {

    /**
     * 记一次预约违约（供预约模块的"临近取消"和签到模块的"超时未签到"调用）
     *
     * <p>内部按类型取配置的分值，写违约记录并扣信用分；扣到限制线以下时自动禁约若干天。
     * 同一张预约单只会记一条，重复调用返回 0，不会重复扣分。</p>
     *
     * @param userId    违约人 id
     * @param bookingId 关联预约 id
     * @param type      违约类型，只能是 NO_SHOW / LATE_CANCEL
     * @return 本次实际扣减的分数，0 表示没有扣（已记过或用户不存在）
     */
    int recordBookingViolation(Long userId, Long bookingId, ViolationType type);

    /**
     * 我的信用分 + 最近若干天的违约明细 + 预约权限（A-05）
     *
     * @param userId 当前登录用户
     */
    CreditVO myCredit(Long userId);

    /**
     * 我的违约记录分页（学生视角，只能看自己的）
     *
     * @param type 违约类型筛选，可空；传非法值按不筛选处理
     */
    PageResult<ViolationVO> pageMine(Long userId, int page, int size, String type);

    /**
     * 全部违约记录分页（管理员视角）
     *
     * @param type    违约类型，可空
     * @param revoked 是否已撤销：0 未撤销 / 1 已撤销 / null 全部
     * @param keyword 学号或昵称关键字，可空
     */
    PageResult<ViolationVO> pageAll(int page, int size, String type, Integer revoked, String keyword);

    /**
     * 撤销一条违约并返还信用分（管理员）
     *
     * <p>只允许撤销一次：已经撤销过的记录再点会报错，不会返还两次。
     * 返还后分数回到限制线以上时，同时解除禁约。</p>
     *
     * @param violationId 违约记录 id
     * @param operatorId  操作管理员 id
     * @param reason      撤销说明，可空
     */
    ViolationVO revoke(Long violationId, Long operatorId, String reason);

    /**
     * 管理员手动扣分（MANUAL 类型，没有关联预约）
     *
     * @param operatorId 操作管理员 id
     */
    ViolationVO manualDeduct(Long operatorId, ManualDeductRequest request);
}
