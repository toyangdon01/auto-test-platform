package com.autotest.config;

import com.autotest.service.SshService;
import com.autotest.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SSH 配置加载器
 * 
 * 在应用启动时从系统配置加载 SSH 超时设置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshConfigLoader {

    private final SystemConfigService configService;

    @PostConstruct
    public void loadConfig() {
        try {
            // 加载 SSH 连接超时
            String sshTimeoutStr = configService.get("ssh_timeout", "86400000"); // 默认 24 小时
            int sshTimeout = Integer.parseInt(sshTimeoutStr);
            SshService.setDefaultTimeout(sshTimeout);
            log.info("SSH 连接超时设置为: {} ms ({} 小时)", sshTimeout, sshTimeout / 3600000.0);

            // 加载执行超时（从系统配置的 exec_timeout，单位是秒）
            String execTimeoutStr = configService.get("exec_timeout", "86400"); // 默认 24 小时
            int execTimeout = Integer.parseInt(execTimeoutStr) * 1000; // 转换为毫秒
            SshService.setDefaultExecTimeout(execTimeout);
            log.info("SSH 执行超时设置为: {} ms ({} 小时)", execTimeout, execTimeout / 3600000.0);

        } catch (Exception e) {
            log.warn("加载 SSH 配置失败，使用默认值: {}", e.getMessage());
        }
    }
}