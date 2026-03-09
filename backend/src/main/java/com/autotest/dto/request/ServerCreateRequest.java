package com.autotest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建服务器请求
 *
 * @author auto-test-platform
 */
@Data
public class ServerCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 服务器名称
     */
    @NotBlank(message = "服务器名称不能为空")
    @Size(max = 100, message = "服务器名称最长100字符")
    private String name;

    /**
     * 主机地址
     */
    @NotBlank(message = "主机地址不能为空")
    @Size(max = 255, message = "主机地址最长255字符")
    private String host;

    /**
     * SSH端口
     */
    private Integer port = 22;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名最长50字符")
    private String username;

    /**
     * 认证类型
     */
    @NotBlank(message = "认证类型不能为空")
    private String authType;

    /**
     * 认证密钥
     */
    @NotBlank(message = "认证密钥不能为空")
    private String authSecret;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 标签
     */
    private java.util.List<String> tags;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注最长500字符")
    private String remark;
}
