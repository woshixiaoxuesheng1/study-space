package com.studyspace.controller;

import com.studyspace.common.Result;
import com.studyspace.constants.UserConstants;
import com.studyspace.dto.BookingVO;
import com.studyspace.dto.CheckInCodeVO;
import com.studyspace.dto.CheckInRequest;
import com.studyspace.security.LoginUser;
import com.studyspace.security.SecurityUtils;
import com.studyspace.service.CheckInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签到签退接口（F-12 ~ F-14，A-18 / A-19 / A-25）
 *
 * <p>为什么路径还是 /api/bookings/...：需求文档第 7 章和前端都按这套地址写好了，
 * 模块拆分只动代码结构、不动接口契约，前端一行都不用改。</p>
 *
 * <p>和预约模块的分工：预约模块负责"下单、改时间、取消"，
 * 这里负责"人来了没有、走了没有"，各自有独立的 Service 和 Mapper。</p>
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    /** A-18 签到：校验归属、状态、座位可用、签到窗口和 4 位签到码 */
    @PostMapping("/{id}/check-in")
    public Result<BookingVO> checkIn(@PathVariable Long id, @Valid @RequestBody CheckInRequest request) {
        BookingVO vo = checkInService.checkIn(id, SecurityUtils.currentUserId(), request.getCode());
        String message = vo.isLateCheckIn()
                ? "签到成功，比预约时间晚了 " + vo.getLateMinutes() + " 分钟"
                : "签到成功";
        return Result.success(message, vo);
    }

    /** A-25 签到凭证：签到码 + 签到窗口 + 倒计时，签到页只调这一个接口 */
    @GetMapping("/{id}/check-in-code")
    public Result<CheckInCodeVO> checkInCode(@PathVariable Long id) {
        LoginUser user = SecurityUtils.currentUser();
        return Result.success(checkInService.checkInCode(id, user.getId(), isAdmin(user)));
    }

    /** A-19 签退：使用中 → 已完成，提前签退座位立即释放 */
    @PostMapping("/{id}/check-out")
    public Result<BookingVO> checkOut(@PathVariable Long id) {
        BookingVO vo = checkInService.checkOut(id, SecurityUtils.currentUserId());
        String message = vo.isEarlyCheckOut()
                ? "签退成功，本次预约已提前结束，座位已释放"
                : "签退成功，本次预约已完成";
        return Result.success(message, vo);
    }

    private static boolean isAdmin(LoginUser user) {
        return UserConstants.ROLE_ADMIN.equals(user.getRole());
    }
}
