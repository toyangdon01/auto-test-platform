package com.autotest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.autotest.common.PageResult;
import com.autotest.dto.request.ServerCreateRequest;
import com.autotest.dto.request.ServerQueryRequest;
import com.autotest.dto.response.ServerDetailResponse;
import com.autotest.entity.Server;
import com.autotest.entity.ServerGroup;
import com.autotest.exception.BusinessException;
import com.autotest.mapper.ServerGroupMapper;
import com.autotest.mapper.ServerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务器服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerServiceImpl implements ServerService {

    private final ServerMapper serverMapper;
    private final ServerGroupMapper serverGroupMapper;

    @Override
    public PageResult<Server> listServers(ServerQueryRequest request) {
        LambdaQueryWrapper<Server> wrapper = new LambdaQueryWrapper<>();
        
        // 名称模糊搜索
        if (StringUtils.hasText(request.getName())) {
            wrapper.like(Server::getName, request.getName())
                    .or()
                    .like(Server::getHost, request.getName());
        }
        
        // 状态筛选
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(Server::getStatus, request.getStatus());
        }
        
        // 分组筛选
        if (request.getGroupId() != null) {
            wrapper.eq(Server::getGroupId, request.getGroupId());
        }
        
        // 排序
        wrapper.orderByDesc(Server::getCreatedAt);
        
        Page<Server> page = serverMapper.selectPage(request.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public ServerDetailResponse getServerDetail(Long id) {
        Server server = serverMapper.selectById(id);
        if (server == null) {
            throw BusinessException.of("服务器不存在");
        }
        
        ServerDetailResponse response = ServerDetailResponse.fromEntity(server);
        
        // 获取分组名称
        if (server.getGroupId() != null) {
            ServerGroup group = serverGroupMapper.selectById(server.getGroupId());
            if (group != null) {
                response.setGroupName(group.getName());
            }
        }
        
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Server createServer(ServerCreateRequest request) {
        // 检查名称是否重复
        LambdaQueryWrapper<Server> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Server::getName, request.getName());
        if (serverMapper.selectCount(wrapper) > 0) {
            throw BusinessException.of("服务器名称已存在");
        }
        
        Server server = new Server();
        server.setName(request.getName());
        server.setHost(request.getHost());
        server.setPort(request.getPort());
        server.setUsername(request.getUsername());
        server.setAuthType(request.getAuthType());
        server.setAuthSecret(request.getAuthSecret()); // TODO: 加密存储
        server.setGroupId(request.getGroupId());
        server.setTags(request.getTags());
        server.setRemark(request.getRemark());
        server.setStatus("offline");
        server.setCreatedAt(LocalDateTime.now());
        server.setUpdatedAt(LocalDateTime.now());
        
        serverMapper.insert(server);
        
        // 异步刷新服务器信息
        // TODO: 实现异步刷新
        
        return server;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Server updateServer(Long id, ServerCreateRequest request) {
        Server server = serverMapper.selectById(id);
        if (server == null) {
            throw BusinessException.of("服务器不存在");
        }
        
        server.setName(request.getName());
        server.setHost(request.getHost());
        server.setPort(request.getPort());
        server.setUsername(request.getUsername());
        server.setAuthType(request.getAuthType());
        if (StringUtils.hasText(request.getAuthSecret())) {
            server.setAuthSecret(request.getAuthSecret());
        }
        server.setGroupId(request.getGroupId());
        server.setTags(request.getTags());
        server.setRemark(request.getRemark());
        server.setUpdatedAt(LocalDateTime.now());
        
        serverMapper.updateById(server);
        return server;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(Long id) {
        Server server = serverMapper.selectById(id);
        if (server == null) {
            throw BusinessException.of("服务器不存在");
        }
        
        // TODO: 检查是否有关联的任务
        
        serverMapper.deleteById(id);
    }

    @Override
    public boolean testConnection(Long id) {
        Server server = serverMapper.selectById(id);
        if (server == null) {
            throw BusinessException.of("服务器不存在");
        }
        
        // TODO: 实现 SSH 连接测试
        return false;
    }

    @Override
    public Server refreshServerInfo(Long id) {
        Server server = serverMapper.selectById(id);
        if (server == null) {
            throw BusinessException.of("服务器不存在");
        }
        
        // TODO: 实现 SSH 连接并获取系统信息
        
        server.setLastCheckAt(LocalDateTime.now());
        server.setUpdatedAt(LocalDateTime.now());
        serverMapper.updateById(server);
        
        return server;
    }

    @Override
    public Server getServerStatus(Long id) {
        Server server = serverMapper.selectById(id);
        if (server == null) {
            throw BusinessException.of("服务器不存在");
        }
        return server;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importServers(byte[] fileData) {
        // TODO: 实现 CSV 解析和批量导入
        throw BusinessException.of("功能开发中");
    }
}
