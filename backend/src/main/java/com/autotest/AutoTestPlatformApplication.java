package com.autotest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 自动化测试管理平台启动类
 *
 * @author auto-test-platform
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.autotest.mapper")
public class AutoTestPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoTestPlatformApplication.class, args);
    }
}
