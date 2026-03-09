package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.dto.request.ServerCreateRequest;
import com.autotest.dto.request.ServerQueryRequest;
import com.autotest.dto.response.ServerDetailResponse;
import com.autotest.entity.Server;

/**
 * 服务器服务接口
 *
 * @author auto-test-platform
 */
public interface ServerService {

    /**
     * 分页查询服务器
     */
    PageResult<Server> listServers(ServerQueryRequest request);

    /**
     * 获取服务器详情
     */
    ServerDetailResponse getServerDetail(Long id);

    /**
     * 创建服务器
     */
    Server createServer(ServerCreateRequest request);

    /**
     * 更新服务器
     */
    Server updateServer(Long id, ServerCreateRequest request);

    /**
     * 删除服务器
     */
    void deleteServer(Long id);

    /**
     * 测试服务器连接
     */
    boolean testConnection(Long id);

    /**
     * 刷新服务器信息
     */
    Server refreshServerInfo(Long id);

    /**
     * 获取服务器状态
     */
    Server getServerStatus(Long id);

    /**
     * 批量导入服务器
     */
    void importServers(byte[] fileData);
}
