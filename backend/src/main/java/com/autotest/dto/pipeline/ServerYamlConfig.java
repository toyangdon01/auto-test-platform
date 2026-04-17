package com.autotest.dto.pipeline;

import com.autotest.dto.request.ServerCreateRequest;
import lombok.Data;

import java.util.List;

/**
 * 服务器 YAML 配置
 *
 * @author auto-test-platform
 */
@Data
public class ServerYamlConfig {
    /**
     * 服务器名称
     */
    private String name;

    /**
     * 主机地址
     */
    private String host;

    /**
     * SSH端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 认证类型: password / ssh_key
     */
    private String authType;

    /**
     * 认证密钥（密码或私钥）
     */
    private String authSecret;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 备注
     */
    private String remark;

    /**
     * 转换为 ServerCreateRequest
     * 用于复用 ServerService 的创建/更新逻辑
     */
    public ServerCreateRequest toCreateRequest() {
        ServerCreateRequest request = new ServerCreateRequest();
        request.setName(this.name);
        request.setHost(this.host);
        request.setPort(this.port != null ? this.port : 22);
        request.setUsername(this.username);
        request.setAuthType(this.authType != null ? this.authType : "password");
        request.setAuthSecret(this.authSecret);
        request.setGroupId(this.groupId);
        request.setTags(this.tags);
        request.setRemark(this.remark);
        return request;
    }
}
