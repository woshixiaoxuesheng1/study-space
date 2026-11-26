package com.studyspace.controller;

import com.studyspace.common.Result;
import com.studyspace.dto.ManualDeductRequest;
import com.studyspace.dto.PageResult;
import com.studyspace.dto.RevokeViolationRequest;
import com.studyspace.security.LoginUser;
import com.studyspace.security.SecurityUtils;
import com.studyspace.service.ViolationService;
import com.studyspace.vo.CreditVO;
import com.studyspace.vo.ViolationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 违约与信用分接口（FR-05-05 ~ FR-05-07，A-05）
 *
 * <p>路径按"看谁的"划分，学生和管理员不会串：</p>
 * <ul>
 *   <li>{@code /api/violations/me/**}：学生查自己的，用户身份从令牌取；</li>
 *   <li>{@code GET /api/violations}、{@code POST /api/violations/**}：管理员专用，
 *       权限拦在 SecurityConfig 的路径规则里，不在方法里再写一遍角色判断。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;

    /** A-05 我的信用分：分数、能否预约、最近 90 天违约明细，一个接口给全 */
    @GetMapping("/me/credit")
    public Result<CreditVO> myCredit() {
        return Result.success(violationService.myCredit(SecurityUtils.currentUserId()));
    }

    /** 我的违约记录，可按类型筛选（NO_SHOW / LATE_CANCEL / MANUAL），含已撤销的 */
    @GetMapping("/me")
    public Result<PageResult<ViolationVO>> myViolations(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(required = false) String type) {
        return Result.success(violationService.pageMine(SecurityUtils.currentUserId(), page, size, type));
    }

    /** 管理员：全部违约记录，支持类型、撤销状态、学号/昵称关键字筛选 */
    @GetMapping
    public Result<PageResult<ViolationVO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) Integer revoked,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(violationService.pageAll(page, size, type, revoked, keyword));
    }

    /** 管理员：撤销违约（申诉通过），记录标记为已撤销并返还信用分 */
    @PostMapping("/{id}/revoke")
    public Result<ViolationVO> revoke(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) RevokeViolationRequest request) {
        LoginUser admin = SecurityUtils.currentUser();
        String reason = request == null ? null : request.getReason();
        return Result.success("撤销成功，信用分已返还", violationService.revoke(id, admin.getId(), reason));
    }

    /** 管理员：手动扣分（线下违规、系统没记到的情况） */
    @PostMapping("/manual")
    public Result<ViolationVO> manualDeduct(@Valid @RequestBody ManualDeductRequest request) {
        LoginUser admin = SecurityUtils.currentUser();
        return Result.success("扣分成功", violationService.manualDeduct(admin.getId(), request));
    }
}
