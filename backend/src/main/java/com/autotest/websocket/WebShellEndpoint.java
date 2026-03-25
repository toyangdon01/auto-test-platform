package com.autotest.websocket;

import com.autotest.entity.Server;
import com.autotest.mapper.ServerMapper;
import com.jcraft.jsch.*;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebShell WebSocket 端点
 * 
 * 提供基于 WebSocket 的 SSH 终端功能
 *
 * @author auto-test-platform
 */
@Slf4j
@ServerEndpoint(value = "/webshell/{serverId}", configurator = WebShellConfigurator.class)
@Component
public class WebShellEndpoint {

    private static ServerMapper serverMapper;

    @Autowired
    public void setServerMapper(ServerMapper serverMapper) {
        WebShellEndpoint.serverMapper = serverMapper;
    }

    // 存储所有活跃的会话
    private static final Map<String, WebShellSession> sessions = new ConcurrentHashMap<>();
    
    // 按用户分组的会话（用于连接限制）
    private static final Map<String, Map<String, WebShellSession>> userSessions = new ConcurrentHashMap<>();

    // 线程池用于处理 SSH 输出
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // 单用户最大连接数
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    
    // 全局最大连接数
    private static final int MAX_TOTAL_CONNECTIONS = 100;

    /**
     * WebSocket 连接建立
     */
    @OnOpen
    public void onOpen(jakarta.websocket.Session session, @PathParam("serverId") Long serverId) {
        log.info("WebShell 连接建立: sessionId={}, serverId={}", session.getId(), serverId);

        try {
            // 1. 检查全局连接数限制
            if (sessions.size() >= MAX_TOTAL_CONNECTIONS) {
                sendMessage(session, "\r\n\033[31m错误: 系统连接数已达上限，请稍后再试\033[0m\r\n");
                session.close();
                return;
            }
            
            // 2. 获取用户标识（从 session 中获取，这里暂时使用 IP 作为标识）
            String userId = getClientIdentifier(session);
            
            // 3. 检查单用户连接数限制
            Map<String, WebShellSession> userSessionMap = userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
            if (userSessionMap.size() >= MAX_CONNECTIONS_PER_USER) {
                sendMessage(session, "\r\n\033[31m错误: 您的连接数已达上限（最多" + MAX_CONNECTIONS_PER_USER + "个）\033[0m\r\n");
                session.close();
                return;
            }

            // 4. 获取服务器信息
            Server server = serverMapper.selectById(serverId);
            if (server == null) {
                sendMessage(session, "\r\n\033[31m错误: 服务器不存在\033[0m\r\n");
                session.close();
                return;
            }
            
            // 5. 检查服务器状态
            if (!"online".equals(server.getStatus())) {
                sendMessage(session, "\r\n\033[33m警告: 服务器状态为 " + server.getStatus() + "，可能无法连接\033[0m\r\n");
            }
            
            // 6. 验证认证信息
            if (server.getAuthSecret() == null || server.getAuthSecret().isEmpty()) {
                sendMessage(session, "\r\n\033[31m错误: 服务器认证信息未配置\033[0m\r\n");
                session.close();
                return;
            }

            // 7. 建立 SSH 连接
            JSch jsch = new JSch();
            com.jcraft.jsch.Session sshSession = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
            sshSession.setPassword(server.getAuthSecret());
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.setConfig("userauth.gssapi-with-mic", "no");
            sshSession.connect(10000);

            // 创建 Shell 通道
            ChannelShell channel = (ChannelShell) sshSession.openChannel("shell");
            channel.setPty(true);
            channel.setPtyType("xterm", 120, 40, 1200, 800);
            channel.connect(10000);

            // 获取输入输出流
            InputStream inputStream = channel.getInputStream();
            OutputStream outputStream = channel.getOutputStream();

            // 创建会话对象
            WebShellSession shellSession = new WebShellSession();
            shellSession.setWebSocketSession(session);
            shellSession.setSshSession(sshSession);
            shellSession.setChannel(channel);
            shellSession.setInputStream(inputStream);
            shellSession.setOutputStream(outputStream);
            shellSession.setUserId(userId);

            sessions.put(session.getId(), shellSession);
            userSessionMap.put(session.getId(), shellSession);

            // 启动输出读取线程
            startOutputReader(shellSession);

            // 发送欢迎信息
            sendMessage(session, "\r\n\033[32m连接成功: " + server.getName() + " (" + server.getHost() + ")\033[0m\r\n");

        } catch (Exception e) {
            log.error("WebShell 连接失败: {}", e.getMessage(), e);
            try {
                sendMessage(session, "\r\n\033[31m连接失败: " + e.getMessage() + "\033[0m\r\n");
                session.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 收到客户端消息
     */
    @OnMessage
    public void onMessage(String message, jakarta.websocket.Session session) {
        WebShellSession shellSession = sessions.get(session.getId());
        if (shellSession == null || shellSession.getChannel() == null) {
            return;
        }

        try {
            // 检查是否为 JSON 格式的控制消息
            if (message.startsWith("{") && message.endsWith("}")) {
                // 尝试解析为 JSON 控制消息
                if (message.contains("\"type\":\"resize\"")) {
                    // 解析 resize 消息
                    int colsStart = message.indexOf("\"cols\":") + 7;
                    int rowsStart = message.indexOf("\"rows\":") + 7;
                    
                    int colsEnd = message.indexOf(",", colsStart);
                    if (colsEnd == -1) colsEnd = message.indexOf("}", colsStart);
                    
                    int rowsEnd = message.indexOf("}", rowsStart);
                    
                    if (colsStart > 6 && rowsStart > 6) {
                        int cols = Integer.parseInt(message.substring(colsStart, colsEnd).trim());
                        int rows = Integer.parseInt(message.substring(rowsStart, rowsEnd).trim());
                        
                        // 调整 PTY 大小
                        shellSession.getChannel().setPtySize(cols, rows, cols * 10, rows * 20);
                        log.debug("终端大小调整: {}x{}", cols, rows);
                    }
                    return;
                }
            }
            
            // 普通终端输入，发送到 SSH
            if (shellSession.getOutputStream() != null) {
                shellSession.getOutputStream().write(message.getBytes());
                shellSession.getOutputStream().flush();
            }
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage());
        }
    }

    /**
     * WebSocket 连接关闭
     */
    @OnClose
    public void onClose(jakarta.websocket.Session session) {
        log.info("WebShell 连接关闭: sessionId={}", session.getId());
        closeSession(session.getId());
    }

    /**
     * WebSocket 发生错误
     */
    @OnError
    public void onError(jakarta.websocket.Session session, Throwable error) {
        log.error("WebShell 错误: sessionId={}, error={}", session.getId(), error.getMessage(), error);
        closeSession(session.getId());
    }

    /**
     * 启动 SSH 输出读取线程
     */
    private void startOutputReader(WebShellSession shellSession) {
        executorService.submit(() -> {
            try {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = shellSession.getInputStream().read(buffer)) != -1) {
                    // 将 SSH 输出推送到 WebSocket，使用 UTF-8 编码
                    String output = new String(buffer, 0, len, StandardCharsets.UTF_8);
                    sendMessage(shellSession.getWebSocketSession(), output);
                }
            } catch (Exception e) {
                log.debug("SSH 输出读取结束: {}", e.getMessage());
            } finally {
                closeSession(shellSession.getWebSocketSession().getId());
            }
        });
    }

    /**
     * 发送消息到 WebSocket
     */
    private void sendMessage(jakarta.websocket.Session session, String message) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败: {}", e.getMessage());
        }
    }

    /**
     * 关闭会话
     */
    private void closeSession(String sessionId) {
        WebShellSession shellSession = sessions.remove(sessionId);
        if (shellSession != null) {
            // 从用户会话映射中移除
            String userId = shellSession.getUserId();
            if (userId != null) {
                Map<String, WebShellSession> userSessionMap = userSessions.get(userId);
                if (userSessionMap != null) {
                    userSessionMap.remove(sessionId);
                    // 如果用户没有活跃会话了，移除整个映射
                    if (userSessionMap.isEmpty()) {
                        userSessions.remove(userId);
                    }
                }
            }
            
            try {
                if (shellSession.getChannel() != null) {
                    shellSession.getChannel().disconnect();
                }
                if (shellSession.getSshSession() != null) {
                    shellSession.getSshSession().disconnect();
                }
            } catch (Exception e) {
                log.error("关闭 SSH 会话失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 获取客户端标识符
     * 用于连接限制
     */
    private String getClientIdentifier(jakarta.websocket.Session session) {
        // 尝试从 session 中获取用户信息
        // 这里可以扩展为从 JWT token 或 session 中获取用户 ID
        Object httpSession = session.getUserProperties().get("httpSession");
        if (httpSession != null) {
            // 如果有 HTTP Session，可以从中获取用户 ID
            // 这里暂时返回一个默认值
        }
        
        // 回退方案：使用 WebSocket session ID 的前缀作为标识
        // 实际应用中应该从认证 token 中获取用户 ID
        return "user_" + (session.getId().hashCode() % 1000);
    }

    /**
     * 获取活跃会话数量
     */
    public static int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * 获取指定用户的活跃会话数量
     */
    public static int getUserSessionCount(String userId) {
        Map<String, WebShellSession> userSessionMap = userSessions.get(userId);
        return userSessionMap != null ? userSessionMap.size() : 0;
    }

    /**
     * WebShell 会话信息
     */
    private static class WebShellSession {
        private jakarta.websocket.Session webSocketSession;
        private com.jcraft.jsch.Session sshSession;
        private ChannelShell channel;
        private InputStream inputStream;
        private OutputStream outputStream;
        private String userId;

        public jakarta.websocket.Session getWebSocketSession() {
            return webSocketSession;
        }

        public void setWebSocketSession(jakarta.websocket.Session webSocketSession) {
            this.webSocketSession = webSocketSession;
        }

        public com.jcraft.jsch.Session getSshSession() {
            return sshSession;
        }

        public void setSshSession(com.jcraft.jsch.Session sshSession) {
            this.sshSession = sshSession;
        }

        public ChannelShell getChannel() {
            return channel;
        }

        public void setChannel(ChannelShell channel) {
            this.channel = channel;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public void setInputStream(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public void setOutputStream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
}
