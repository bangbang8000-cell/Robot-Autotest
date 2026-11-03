package com.qa.autotest.domain.common;

import java.util.List;

/**
 * 分页结果（应用层与接口层共用；纯数据，不含任何框架类型）。
 *
 * @param items 当前页数据
 * @param total 满足条件的总条数
 * @param page  页码，从 1 开始
 * @param size  每页大小
 * @param <T>   元素类型
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0, page, size);
    }
}
