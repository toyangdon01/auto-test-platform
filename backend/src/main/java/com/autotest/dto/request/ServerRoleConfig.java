package com.autotest.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 任务服务器角色配置
 * 创建任务时指定每个服务器的角色
 *
 * @author auto-test-platform
 */
@Data
public class ServerRoleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 服务器ID
     */
    private Long serverId;

    /**
     * 角色名称
     */
    private String role;

    /**
     * 角色特定参数
     */
    private Map<String, Object> roleParams;
}
