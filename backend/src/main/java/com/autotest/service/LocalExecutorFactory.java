package com.autotest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地执行器工厂
 * 根据平台操作系统自动选择合适的执行器
 */
@Slf4j
@Component
public class LocalExecutorFactory {

    @Autowired
    @Qualifier("shellExecutor")
    private LocalExecutor shellExecutor;

    @Autowired
    @Qualifier("powershellExecutor")
    private LocalExecutor powershellExecutor;

    private LocalExecutor defaultExecutor;

    @PostConstruct
    public void init() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        log.info("检测到操作系统：{}", osName);

        if (osName.contains("windows")) {
            if (powershellExecutor.isAvailable()) {
                defaultExecutor = powershellExecutor;
                log.info("使用 PowerShell 执行器（Windows）");
            } else if (shellExecutor.isAvailable()) {
                defaultExecutor = shellExecutor;
                log.warn("PowerShell 不可用，fallback 到 Shell 执行器");
            }
        } else {
            if (shellExecutor.isAvailable()) {
                defaultExecutor = shellExecutor;
                log.info("使用 Shell 执行器（Linux/Unix）");
            } else if (powershellExecutor.isAvailable()) {
                defaultExecutor = powershellExecutor;
                log.warn("Shell 不可用，fallback 到 PowerShell 执行器");
            }
        }

        if (defaultExecutor == null) {
            log.error("无可用的本地执行器！");
        }
    }

    /**
     * 获取默认执行器
     */
    public LocalExecutor getDefaultExecutor() {
        return defaultExecutor;
    }

    /**
     * 根据类型获取执行器
     */
    public LocalExecutor getExecutor(String type) {
        if ("shell".equals(type)) {
            return shellExecutor;
        } else if ("powershell".equals(type)) {
            return powershellExecutor;
        }
        return defaultExecutor;
    }

    /**
     * 获取当前执行器类型
     */
    public String getCurrentType() {
        return defaultExecutor != null ? defaultExecutor.getType() : "unknown";
    }
}