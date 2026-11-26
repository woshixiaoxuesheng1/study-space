package com.studyspace.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 撤销违约请求（FR-05-05 的"申诉撤销"）
 *
 * <p>撤销是管理员复核后的动作：确认学生有正当理由（系统故障、临时就医等）后，
 * 把这条违约标记为已撤销并返还信用分。撤销说明会追加到原违约原因的后面，
 * 形成"学生看到的原因 + 管理员为什么撤销"的完整链条。</p>
 */
@Data
public class RevokeViolationRequest {

    /** 撤销说明，可以不填；填了就会追加到违约原因末尾留痕 */
    @Size(max = 100, message = "撤销说明最多 100 个字")
    private String reason;
}
