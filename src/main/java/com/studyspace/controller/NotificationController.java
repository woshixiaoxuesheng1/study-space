package com.studyspace.controller;

import com.studyspace.common.Result;
import com.studyspace.dto.PageResult;
import com.studyspace.security.SecurityUtils;
import com.studyspace.service.NotificationService;
import com.studyspace.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知接口（消息中心）
 *
 * <p>四个接口全都不用传 userId：接收人一律从令牌里取，
 * 学生改请求参数也看不到别人的通知。</p>
 *
 * <p>这里没有任何"写通知"的接口——通知只能由 MQ 消费端产生，
 * 不开放给前端直接创建，避免出现伪造的系统通知。</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知列表：可按分类筛选，unreadOnly=true 只看未读 */
    @GetMapping
    public Result<PageResult<NotificationVO>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) Boolean unreadOnly) {
        return Result.success(notificationService.pageMine(
                SecurityUtils.currentUserId(), page, size, type, unreadOnly));
    }

    /** 未读条数：前端登录后调一次，用来显示消息中心角标 */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(notificationService.unreadCount(SecurityUtils.currentUserId()));
    }

    /** 单条标记已读 */
    @PutMapping("/{id}/read")
    public Result<Boolean> markRead(@PathVariable Long id) {
        boolean changed = notificationService.markRead(id, SecurityUtils.currentUserId());
        return Result.success(changed ? "已标记为已读" : "该通知不存在或已经读过", changed);
    }

    /** 全部标记已读，返回本次实际处理的条数 */
    @PutMapping("/read-all")
    public Result<Integer> markAllRead() {
        int count = notificationService.markAllRead(SecurityUtils.currentUserId());
        return Result.success(count > 0 ? "已读完 " + count + " 条通知" : "没有未读通知", count);
    }
}
