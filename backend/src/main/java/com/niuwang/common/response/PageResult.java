package com.niuwang.common.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 分页结果封装类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        long pages = (total + size - 1) / size;
        return new PageResult<>(records, total, page, size, pages);
    }
}
