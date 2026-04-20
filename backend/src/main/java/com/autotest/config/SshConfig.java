package com.autotest.config;

import com.autotest.service.SshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * SSH 配置
 */
@Slf4j
@Configuration
public class SshConfig {

    @Value("${autotest.ssh.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${autotest.ssh.exec-timeout:86400000}")
    private int execTimeout;

    @PostConstruct
    public void init() {
        SshService.setDefaultTimeout(connectTimeout);
        SshService.setDefaultExecTimeout(execTimeout);
        log.info("SSH 配置初始化: connectTimeout={}ms, execTimeout={}ms", connectTimeout, execTimeout);
    }
}
