package com.studyspace.controller;

import com.studyspace.common.Result;
import com.studyspace.vo.BookingVO;
import com.studyspace.dto.CreateBookingRequest;
import com.studyspace.dto.PageResult;
import com.studyspace.security.LoginUser;
import com.studyspace.security.SecurityUtils;
import com.studyspace.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约接口（A-14 ~ A-17）
 * <p>当前用户身份一律从令牌里取（SecurityUtils），接口参数里没有 userId，
 * 学生就算改请求体也动不了别人的预约。</p>
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** A-14 创建预约：冲突检查、本人重复检查、每日次数、信用分都在 service 里 */
    @PostMapping
    public Result<BookingVO> create(@Valid @RequestBody CreateBookingRequest request) {
        LoginUser user = SecurityUtils.currentUser();
        return Result.success("预约成功", bookingService.create(user.getId(), request));
    }

    /** A-15 我的预约列表，可按状态筛选 */
    @GetMapping
    public Result<PageResult<BookingVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String status) {
        return Result.success(bookingService.pageMine(SecurityUtils.currentUserId(), page, size, status));
    }

    /** A-16 预约详情：本人或管理员 */
    @GetMapping("/{id}")
    public Result<BookingVO> detail(@PathVariable Long id) {
        LoginUser user = SecurityUtils.currentUser();
        return Result.success(bookingService.detail(id, user.getId(), SecurityUtils.isAdmin(user)));
    }

    /** A-17 取消预约：开始前 30 分钟以内取消会扣 10 分，管理员代取消不扣 */
    @DeleteMapping("/{id}")
    public Result<BookingVO> cancel(@PathVariable Long id,
                                    @RequestParam(required = false) String reason) {
        LoginUser user = SecurityUtils.currentUser();
        BookingVO vo = bookingService.cancel(id, user.getId(), SecurityUtils.isAdmin(user), reason);
        boolean penalty = vo.getPenaltyPoints() != null && vo.getPenaltyPoints() > 0;
        return Result.success(penalty ? "取消成功，因临近开始时间，已扣除信用分 " + vo.getPenaltyPoints() + " 分" : "取消成功", vo);
    }
}
