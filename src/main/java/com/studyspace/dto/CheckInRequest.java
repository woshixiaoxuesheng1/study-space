package com.studyspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 签到请求（A-18）：需要提交预约时下发的 4 位签到码
 */
@Data
public class CheckInRequest {

    @NotBlank(message = "签到码不能为空")
    @Pattern(regexp = "\\d{4}", message = "签到码为 4 位数字")
    private String code;
}
