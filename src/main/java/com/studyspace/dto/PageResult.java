package com.studyspace.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 统一分页返回：{total, page, size, list}（需求说明第 7 章的接口约定）
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    /** 总条数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long page;

    /** 每页条数 */
    private long size;

    /** 当前页数据 */
    private List<T> list;

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(0, page, size, List.of());
    }
}
