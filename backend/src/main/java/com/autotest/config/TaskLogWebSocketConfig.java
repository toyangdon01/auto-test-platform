package com.autotest.config;

/**
 * 任务日志 WebSocket 配置
 * 
 * 注意：使用 @ServerEndpoint 注解方式，由 WebSocketConfig 中的 ServerEndpointExporter 自动注册
 * 端点定义在 TaskLogEndpoint.java
 */
// @Configuration  // 不需要，使用 @ServerEndpoint 注解方式
// @EnableWebSocket
public class TaskLogWebSocketConfig {
    // 配置由 WebSocketConfig.serverEndpointExporter() 自动处理
}
