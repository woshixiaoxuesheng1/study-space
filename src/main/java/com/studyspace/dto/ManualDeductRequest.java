package com.studyspace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员手动扣分请求（FR-05-05 的 MANUAL 类型）
 *
 * <p>用于"系统没记到、但确实该扣"的情况：比如学生占座不来、管理员现场发现后补记一次。
 * 之所以要有这个入口，是因为超时违约只能由定时任务自动判定，
 * 线下的违规行为（代人签到、占座睡觉）系统看不见。</p>
 */
@Data
public class ManualDeductRequest {

    /** 被扣分的学生 id（管理员自己不能给自己扣分，service 里会校验角色） */
    @NotNull(message = "违约人不能为空")
    private Long userId;

    /**
     * 扣多少分
     * <p>下限 1 分用注解拦；上限走配置（violation.manual-max-points），
     * 避免把"最多扣 100"写死在注解里，改规则还要重新编译。</p>
     */
    @NotNull(message = "扣分不能为空")
    @Min(value = 1, message = "扣分至少 1 分")
    private Integer points;

    /** 扣分原因，会原样展示给学生，必须写清楚 */
    @NotBlank(message = "扣分原因不能为空")
    @Size(max = 255, message = "扣分原因最多 255 个字")
    private String reason;

    /**
     * 违约发生时间，格式 2026-11-25 10:00:00
     * <p>不传就按当前时间记；补录昨天的情况可以传昨天的时间，违约记录按真实发生时间排序才准。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenTime;
}
