package com.autotest.websocket;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 配置器
 * 
 * 用于在 WebSocket 握手时获取 HTTP 请求信息
 *
 * @author auto-test-platform
 */
@Slf4j
public class WebShellConfigurator extends ServerEndpointConfig.Configurator {

    /**
     * 握手请求处理
     * 可以在此处进行身份验证
     */
    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        // 获取 HTTP Session（如果使用 Session 认证）
        HttpSession httpSession = (HttpSession) request.getHttpSession();
        if (httpSession != null) {
            // 将 HTTP Session 中的用户信息存入 WebSocket Session
            sec.getUserProperties().put("httpSession", httpSession);
        }

        // 获取请求头中的 Token（如果使用 Token 认证）
        // String token = request.getHeaders().get("Authorization") != null 
        //     ? request.getHeaders().get("Authorization").get(0) 
        //     : null;
        // if (token != null) {
        //     sec.getUserProperties().put("token", token);
        // }

        super.modifyHandshake(sec, request, response);
    }
}
