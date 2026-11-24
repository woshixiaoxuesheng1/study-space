package com.studyspace.constants;

import java.time.format.DateTimeFormatter;

/**
 * 全局通用常量
 * <p>与具体业务无关、多个模块都要用的值放这里，避免各 impl 各写一份。</p>
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 分页查询一页最多返回多少条，防止前端传 size=99999 把数据库拖垮 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 日期时间统一格式 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 日期时间格式化器（DateTimeFormatter 不可变、线程安全，可以放心做成静态常量） */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
}
