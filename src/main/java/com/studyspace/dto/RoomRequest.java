package com.studyspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * 新增 / 修改自习室请求（A-08 / A-09）
 */
@Data
public class RoomRequest {

    @NotBlank(message = "自习室名称不能为空")
    @Size(max = 50, message = "自习室名称不能超过 50 个字")
    private String name;

    @NotBlank(message = "楼栋不能为空")
    @Size(max = 50, message = "楼栋不能超过 50 个字")
    private String building;

    @NotNull(message = "楼层不能为空")
    @Min(value = 1, message = "楼层最小为 1")
    @Max(value = 100, message = "楼层最大为 100")
    private Integer floor;

    /** 开放时间，前端传 "08:00" 或 "08:00:00" 都能解析 */
    @NotNull(message = "开放时间不能为空")
    private LocalTime openTime;

    @NotNull(message = "关闭时间不能为空")
    private LocalTime closeTime;

    /** 不传默认 OPEN；只能填 OPEN / CLOSED / MAINTENANCE */
    @Pattern(regexp = "OPEN|CLOSED|MAINTENANCE", message = "状态只能是 OPEN、CLOSED、MAINTENANCE")
    private String status;

    @Size(max = 255, message = "备注不能超过 255 个字")
    private String description;
}
