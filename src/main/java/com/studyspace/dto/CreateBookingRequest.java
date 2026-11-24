package com.studyspace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建预约请求（A-14）
 * <p>只接收座位和时间，<b>不接收 userId</b>：预约人一律从登录令牌里取，
 * 否则学生改一下请求参数就能替别人下单（需求说明 2.2）。</p>
 */
@Data
public class CreateBookingRequest {

    @NotNull(message = "座位不能为空")
    private Long seatId;

    /** 时间格式统一为 2026-11-25 10:00:00 */
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
