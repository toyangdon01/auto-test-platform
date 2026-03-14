package com.autotest.service;

import com.autotest.dto.OutputCollectConfig;
import com.autotest.dto.OutputCollectResult;
import com.autotest.entity.Server;

/**
 * 输出收集服务接口
 *
 * @author auto-test-platform
 */
public interface OutputCollectorService {

    /**
     * 收集测试输出文件
     *
     * @param server 目标服务器
     * @param workDir 工作目录
     * @param config 收集配置
     * @param taskId 任务ID
     * @param serverId 服务器ID
     * @return 收集结果
     */
    OutputCollectResult collectOutputs(Server server, String workDir, OutputCollectConfig config, Long taskId, Long serverId);

    /**
     * 解析输出配置
     *
     * @param outputConfigMap 原始配置 Map
     * @return 配置对象
     */
    OutputCollectConfig parseConfig(Object outputConfigMap);
}
