package com.autotest.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置类
 * 
 * 用于支持 @ServerEndpoint 注解的 WebSocket 端点
 *
 * @author auto-test-platform
 */
@Configuration
public class WebSocketConfig {

    /**
     * 注入 ServerEndpointExporter
     * 自动注册使用 @ServerEndpoint 注解的 WebSocket 端点
     * 
     * 注意：ServerEndpointExporter 在测试环境（MockMvc）中不可用，
     * 通过属性控制是否启用，测试环境禁用
     */
    @Bean
    @ConditionalOnProperty(name = "websocket.enabled", havingValue = "true", matchIfMissing = true)
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
