package com.autotest.websocket;

import com.autotest.service.LogCacheService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务日志 WebSocket 端点
 * 
 * 端点：/ws/task-log/{taskId}
 * 
 * 消息格式：
 * - 服务端 → 客户端：
 *   - "history:..." 历史日志
 *   - "log:..." 单行日志
 *   - "chunk:..." 日志块
 *   - "complete:..." 任务完成
 *   - "cancel:..." 任务取消
 * - 客户端 → 服务端：
 *   - "ping" 心跳
 */
@Slf4j
@ServerEndpoint(value = "/ws/task-log/{taskId}", configurator = TaskLogEndpoint.SpringConfigurator.class)
@Component
public class TaskLogEndpoint {

    private static LogCacheService logCacheService;

    @Autowired
    public void setLogCacheService(LogCacheService logCacheService) {
        TaskLogEndpoint.logCacheService = logCacheService;
    }

    /** 会话到 taskId 的映射 */
    private static final Map<String, Long> sessionTaskMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("taskId") Long taskId) {
        log.info("TaskLog WebSocket 连接建立: sessionId={}, taskId={}", session.getId(), taskId);
        
        if (taskId == null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "缺少 taskId"));
            } catch (Exception e) {
                log.error("关闭会话失败", e);
            }
            return;
        }
        
        sessionTaskMap.put(session.getId(), taskId);
        
        if (logCacheService != null) {
            logCacheService.registerSession(taskId, session);
        } else {
            log.warn("LogCacheService 未注入");
        }
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        // 处理心跳
        if ("ping".equals(message)) {
            try {
                session.getBasicRemote().sendText("pong");
            } catch (Exception e) {
                log.error("发送心跳响应失败", e);
            }
            return;
        }
        
        log.debug("收到 TaskLog WebSocket 消息: sessionId={}, message={}", session.getId(), message);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("TaskLog WebSocket 错误: sessionId={}", session.getId(), error);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        log.info("TaskLog WebSocket 连接关闭: sessionId={}, reason={}", session.getId(), reason);
        
        Long taskId = sessionTaskMap.remove(session.getId());
        if (taskId != null && logCacheService != null) {
            logCacheService.removeSession(taskId, session);
        }
    }
    
    /**
     * Spring 配置器，确保获取 Spring 管理的 Bean
     */
    public static class SpringConfigurator extends ServerEndpointConfig.Configurator {
        // 使用默认配置，让静态字段注入工作
    }
}
