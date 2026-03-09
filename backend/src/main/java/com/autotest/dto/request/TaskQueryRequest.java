package com.autotest.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询任务请求
 *
 * @author auto-test-platform
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskQueryRequest extends PageRequest {

    private static final long serialVersionUID = 1L;

    /**
     * 名称（模糊搜索）
     */
    private String name;

    /**
     * 状态
     */
    private String status;

    /**
     * 脚本ID
     */
    private Long scriptId;
}
