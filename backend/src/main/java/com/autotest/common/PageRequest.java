package com.autotest.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页请求参数
 *
 * @author auto-test-platform
 */
@Data
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer size = 20;

    /**
     * 排序字段
     */
    private String sort;

    /**
     * 排序方向: asc/desc
     */
    private String order = "desc";

    /**
     * 获取 MyBatis-Plus 分页对象
     */
    @SuppressWarnings("unchecked")
    public <T> com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> toPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
    }

    /**
     * 获取偏移量
     */
    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
