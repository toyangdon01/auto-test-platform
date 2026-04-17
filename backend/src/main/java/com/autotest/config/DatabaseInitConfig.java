package com.autotest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 数据库初始化配置
 * 在应用启动时自动创建数据库目录
 *
 * @author auto-test-platform
 */
@Slf4j
@Configuration
public class DatabaseInitConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Bean
    public CommandLineRunner initDatabaseDirectory() {
        return args -> {
            // 解析 SQLite 数据库路径
            // 格式：jdbc:sqlite:/path/to/db.db
            if (datasourceUrl != null && datasourceUrl.startsWith("jdbc:sqlite:")) {
                String dbPath = datasourceUrl.substring("jdbc:sqlite:".length());
                
                // 移除查询参数（如 ?foreign_keys=on&journal_mode=WAL）
                int queryIndex = dbPath.indexOf('?');
                if (queryIndex > 0) {
                    dbPath = dbPath.substring(0, queryIndex);
                }
                
                // 处理变量替换后的路径
                dbPath = dbPath.replace("${user.home}", System.getProperty("user.home"));
                
                File dbFile = new File(dbPath);
                File parentDir = dbFile.getParentFile();
                
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (created) {
                        log.info("已创建数据库目录：{}", parentDir.getAbsolutePath());
                    } else {
                        log.warn("创建数据库目录失败：{}", parentDir.getAbsolutePath());
                    }
                } else {
                    log.debug("数据库目录已存在：{}", parentDir != null ? parentDir.getAbsolutePath() : "unknown");
                }
            }
        };
    }
}
