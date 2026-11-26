package com.studyspace.enums;

/**
 * 通知分类（对应 notification.type 字段）
 *
 * <p>只做"这条消息归哪一类"的划分，决定前端消息中心用哪种颜色/图标展示，
 * 也决定用户可以按类型筛选。真正决定标题文案的是 NotifyEvent。</p>
 */
public enum NotifyType {

    /** 预约相关：预约成功、即将开始、已取消、已完成 */
    BOOKING("预约通知"),
    /** 违约相关：超时未签到、临近取消扣分 */
    VIOLATION("违约提醒"),
    /** 信用分相关：分数变动、禁约与恢复 */
    CREDIT("信用分通知"),
    /** 系统消息：管理员公告一类 */
    SYSTEM("系统消息");

    private final String text;

    NotifyType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /** 字符串转枚举，无法识别时返回 null（数据库里出现脏值的兜底） */
    public static NotifyType of(String value) {
        if (value == null) {
            return null;
        }
        for (NotifyType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }

    /** 类型中文，前端直接显示 */
    public static String textOf(String value) {
        NotifyType type = of(value);
        return type == null ? value : type.getText();
    }

    /** 是否是合法的类型值，查询接口用它判断要不要按类型过滤 */
    public static boolean isValid(String value) {
        return of(value) != null;
    }
}
