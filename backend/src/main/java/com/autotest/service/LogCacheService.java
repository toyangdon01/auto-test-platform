package com.autotest.service;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 任务日志缓存服务
 * 
 * 功能：
 * 1. 内存缓存日志（环形缓冲区，限制大小）
 * 2. WebSocket 实时推送
 * 3. 任务完成后写入数据库
 */
@Slf4j
@Service
public class LogCacheService {

    /** 日志缓存，key 为 taskId */
    private final ConcurrentHashMap<Long, LogBuffer> logBuffers = new ConcurrentHashMap<>();
    
    /** WebSocket 会话，key 为 taskId */
    private final ConcurrentHashMap<Long, Set<Session>> wsSessions = new ConcurrentHashMap<>();
    
    /** 单个任务日志最大缓存大小（1MB） */
    private static final int MAX_LOG_SIZE = 1024 * 1024;
    
    /** 环形缓冲区保留尾部大小（超出时保留最新的 800KB） */
    private static final int TRUNCATE_SIZE = 800 * 1024;

    /**
     * 日志缓冲区（线程安全）
     */
    public static class LogBuffer {
        private final StringBuilder buffer = new StringBuilder();
        private final Object lock = new Object();
        private volatile boolean completed = false;
        private volatile int totalLines = 0;
        
        public void append(String line) {
            synchronized (lock) {
                buffer.append(line).append("\n");
                totalLines++;
            }
        }
        
        public void appendChunk(String chunk) {
            synchronized (lock) {
                buffer.append(chunk);
            }
        }
        
        public String getLog() {
            synchronized (lock) {
                return buffer.toString();
            }
        }
        
        public int getLength() {
            synchronized (lock) {
                return buffer.length();
            }
        }
        
        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
        
        public boolean isCompleted() {
            return completed;
        }
        
        public int getTotalLines() {
            return totalLines;
        }
        
        /**
         * 检查并限制大小，超出时保留尾部
         */
        public void checkAndTruncate(int maxSize, int truncateSize) {
            synchronized (lock) {
                if (buffer.length() > maxSize) {
                    String truncated = "... [日志过长，已截取最新部分] ...\n" + 
                            buffer.substring(buffer.length() - truncateSize);
                    buffer.setLength(0);
                    buffer.append(truncated);
                }
            }
        }
        
        /**
         * 清空日志内容
         */
        public void clear() {
            synchronized (lock) {
                buffer.setLength(0);
                totalLines = 0;
                completed = false;
            }
        }
    }

    /**
     * 添加日志行
     */
    public void appendLog(Long taskId, String line) {
        LogBuffer buffer = logBuffers.computeIfAbsent(taskId, k -> new LogBuffer());
        buffer.append(line);
        
        // 检查大小限制
        buffer.checkAndTruncate(MAX_LOG_SIZE, TRUNCATE_SIZE);
        
        // 推送到 WebSocket
        pushToWebSocket(taskId, "log:" + line);
    }

    /**
     * 添加日志块（原始内容）
     */
    public void appendChunk(Long taskId, String chunk) {
        LogBuffer buffer = logBuffers.computeIfAbsent(taskId, k -> new LogBuffer());
        buffer.appendChunk(chunk);
        
        // 推送到 WebSocket（按行推送）
        pushToWebSocket(taskId, "chunk:" + chunk);
    }

    /**
     * 获取日志内容
     */
    public String getLog(Long taskId) {
        LogBuffer buffer = logBuffers.get(taskId);
        return buffer != null ? buffer.getLog() : null;
    }

    /**
     * 检查任务是否已完成
     */
    public boolean isTaskCompleted(Long taskId) {
        LogBuffer buffer = logBuffers.get(taskId);
        return buffer != null && buffer.isCompleted();
    }

    /**
     * 标记任务完成
     */
    public void completeTask(Long taskId) {
        LogBuffer buffer = logBuffers.get(taskId);
        if (buffer != null) {
            buffer.setCompleted(true);
        }
        
        // 通知 WebSocket 客户端
        pushToWebSocket(taskId, "complete:任务执行完成");
        
        // 延迟清理缓存（保留 30 分钟供查看）
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                logBuffers.remove(taskId);
                log.debug("清理任务 {} 的日志缓存", taskId);
            }
        }, 30 * 60 * 1000);
    }

    /**
     * 清理任务缓存（取消时使用）
     */
    public void clearCache(Long taskId) {
        logBuffers.remove(taskId);
        pushToWebSocket(taskId, "cancel:任务已取消");
        closeWebSocketSessions(taskId);
    }

    /**
     * 清空任务日志缓存（重试时使用）
     * 只清空日志内容，保留 WebSocket 会话
     */
    public void clearTaskLogs(Long taskId) {
        LogBuffer buffer = logBuffers.get(taskId);
        if (buffer != null) {
            buffer.clear();
            log.debug("已清空任务 {} 的日志缓存", taskId);
        }
    }

    /**
     * 注册 WebSocket 会话（Jakarta WebSocket Session）
     */
    public void registerSession(Long taskId, Session session) {
        wsSessions.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("WebSocket 会话注册: taskId={}, sessionId={}", taskId, session.getId());
        
        // 发送已有日志
        LogBuffer buffer = logBuffers.get(taskId);
        if (buffer != null && buffer.getLength() > 0) {
            try {
                // 分批发送历史日志（避免消息过大）
                String history = buffer.getLog();
                int chunkSize = 32 * 1024; // 32KB 每块
                for (int i = 0; i < history.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, history.length());
                    String chunk = history.substring(i, end);
                    session.getBasicRemote().sendText("history:" + chunk);
                }
                
                // 发送完成标记
                if (buffer.isCompleted()) {
                    session.getBasicRemote().sendText("complete:任务执行完成");
                }
            } catch (IOException e) {
                log.error("发送历史日志失败", e);
            }
        }
    }

    /**
     * 移除 WebSocket 会话
     */
    public void removeSession(Long taskId, Session session) {
        Set<Session> sessions = wsSessions.get(taskId);
        if (sessions != null) {
            sessions.remove(session);
            log.debug("WebSocket 会话移除: taskId={}, sessionId={}", taskId, session.getId());
        }
    }

    /**
     * 推送消息到 WebSocket
     */
    private void pushToWebSocket(Long taskId, String message) {
        Set<Session> sessions = wsSessions.get(taskId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        List<Session> toRemove = new ArrayList<>();
        
        for (Session session : sessions) {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                } else {
                    toRemove.add(session);
                }
            } catch (IOException e) {
                log.error("WebSocket 推送失败: sessionId={}", session.getId(), e);
                toRemove.add(session);
            }
        }
        
        // 清理已关闭的会话
        for (Session session : toRemove) {
            sessions.remove(session);
        }
    }

    /**
     * 关闭任务的所有 WebSocket 会话
     */
    private void closeWebSocketSessions(Long taskId) {
        Set<Session> sessions = wsSessions.remove(taskId);
        if (sessions != null) {
            for (Session session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (IOException e) {
                    log.error("关闭 WebSocket 会话失败", e);
                }
            }
        }
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedTasks", logBuffers.size());
        stats.put("totalSessions", wsSessions.values().stream().mapToInt(Set::size).sum());
        
        Map<Long, Integer> taskLogSizes = new HashMap<>();
        logBuffers.forEach((taskId, buffer) -> taskLogSizes.put(taskId, buffer.getLength()));
        stats.put("logSizes", taskLogSizes);
        
        return stats;
    }
}
