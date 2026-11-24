package com.studyspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 批量生成座位请求（A-12 / F-06）
 * <p>按"几行几列"生成，座位号规则与 init.sql 保持一致：A01、A02 ……</p>
 */
@Data
public class SeatBatchRequest {

    /** 行数，最多 26 行（座位号用字母 A-Z） */
    @NotNull(message = "行数不能为空")
    @Min(value = 1, message = "行数最小为 1")
    @Max(value = 26, message = "行数最多 26 行")
    private Integer rows;

    /** 列数 */
    @NotNull(message = "列数不能为空")
    @Min(value = 1, message = "列数最小为 1")
    @Max(value = 30, message = "列数最多 30 列")
    private Integer cols;
}
