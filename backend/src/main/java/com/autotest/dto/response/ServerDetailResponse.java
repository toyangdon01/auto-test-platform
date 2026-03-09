package com.autotest.dto.response;

import com.autotest.entity.Server;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务器详情响应
 *
 * @author auto-test-platform
 */
@Data
public class ServerDetailResponse {

    private Long id;
    private String name;
    private String host;
    private Integer port;
    private String username;
    private String authType;
    private String osType;
    private String osVersion;
    private Integer cpuCores;
    private String cpuModel;
    private String memorySize;
    private Long memoryTotalMb;
    private Object diskInfo;
    private Long groupId;
    private String groupName;
    private List<String> tags;
    private String remark;
    private String status;
    private LocalDateTime lastCheckAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ServerDetailResponse fromEntity(Server server) {
        ServerDetailResponse response = new ServerDetailResponse();
        response.setId(server.getId());
        response.setName(server.getName());
        response.setHost(server.getHost());
        response.setPort(server.getPort());
        response.setUsername(server.getUsername());
        response.setAuthType(server.getAuthType());
        response.setOsType(server.getOsType());
        response.setOsVersion(server.getOsVersion());
        response.setCpuCores(server.getCpuCores());
        response.setCpuModel(server.getCpuModel());
        response.setMemorySize(server.getMemorySize());
        response.setMemoryTotalMb(server.getMemoryTotalMb());
        response.setDiskInfo(server.getDiskInfo());
        response.setGroupId(server.getGroupId());
        response.setTags(server.getTags());
        response.setRemark(server.getRemark());
        response.setStatus(server.getStatus());
        response.setLastCheckAt(server.getLastCheckAt());
        response.setCreatedAt(server.getCreatedAt());
        response.setUpdatedAt(server.getUpdatedAt());
        return response;
    }
}
