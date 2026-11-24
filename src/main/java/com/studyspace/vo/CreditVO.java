package com.studyspace.vo;

import lombok.Data;

/**
 * 信用分信息（F-04 / A-05）
 */
@Data
public class CreditVO {

    /** 当前信用分 */
    private Integer creditScore;

    /** 禁约限制线，低于它不能预约 */
    private Integer limitScore;

    /** 现在能不能预约 */
    private Boolean canBook;

    /** 禁止预约截止时间，null 表示没被限制 */
    private String banUntil;

    /** 给前端直接显示的说明文字 */
    private String message;
}
