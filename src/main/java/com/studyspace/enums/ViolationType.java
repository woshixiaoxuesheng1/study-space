package com.studyspace.enums;

/**
 * 违约类型（FR-05-05）
 *
 * <p>三个取值分别对应三种产生方式：</p>
 * <ul>
 *   <li>{@link #NO_SHOW}：签到截止后仍未签到，由定时任务判定；</li>
 *   <li>{@link #LATE_CANCEL}：临近开始才取消，由取消接口判定；</li>
 *   <li>{@link #MANUAL}：管理员手动扣分，没有关联预约。</li>
 * </ul>
 *
 * <p>类型名直接落库（varchar），中文文案只在这里维护一份，
 * 前端拿 typeText 直接显示，不用自己维护一份"NO_SHOW → 未签到"的映射表。</p>
 */
public enum ViolationType {

    /** 超时未签到 */
    NO_SHOW("超时未签到"),
    /** 临近开始才取消 */
    LATE_CANCEL("临近开始取消"),
    /** 管理员手动扣分 */
    MANUAL("管理员手动扣分");

    private final String text;

    ViolationType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /** 字符串转枚举，无法识别时返回 null（数据库脏值兜底） */
    public static ViolationType of(String value) {
        if (value == null) {
            return null;
        }
        for (ViolationType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }

    /** 类型中文，前端直接显示；脏值原样返回，不吞掉问题 */
    public static String textOf(String value) {
        ViolationType type = of(value);
        return type == null ? value : type.getText();
    }

    /** 是否是合法的违约类型，用于查询参数校验 */
    public static boolean isValid(String value) {
        return of(value) != null;
    }
}
