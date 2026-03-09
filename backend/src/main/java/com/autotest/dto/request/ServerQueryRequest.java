package com.autotest.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询服务器请求
 *
 * @author auto-test-platform
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServerQueryRequest extends PageRequest {

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
     * 分组ID
     */
    private Long groupId;
}
