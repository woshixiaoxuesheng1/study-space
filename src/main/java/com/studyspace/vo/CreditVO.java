package com.studyspace.vo;

import lombok.Data;

import java.util.List;

/**
 * 信用分与预约权限（FR-05-06 / FR-05-07，A-05）
 *
 * <p>需求 5.5 要求这个接口返回三样东西：当前信用分、最近 90 天违约明细、预约权限状态。
 * 把三者放在一个返回里，学生打开"我的信用分"页面只发一次请求，
 * 不用先查分数、再查明细、再自己推断"我还能不能预约"。</p>
 */
@Data
public class CreditVO {

    /** 当前信用分 */
    private Integer creditScore;

    /** 禁约限制线，低于它不能预约（来自 booking.credit-limit） */
    private Integer limitScore;

    /** 现在能不能预约：既看分数，也看禁约期有没有过 */
    private Boolean canBook;

    /** 禁止预约截止时间，null 表示没被限制 */
    private String banUntil;

    /** 给前端直接显示的说明文字，前端不用自己拼文案 */
    private String message;

    /** 最近 N 天内违约次数（撤销的不算），N 见 violation.recent-days */
    private Integer recentViolationCount;

    /** 最近 N 天违约明细，按发生时间倒序，条数上限见 violation.recent-limit */
    private List<ViolationVO> recentViolations;
}
