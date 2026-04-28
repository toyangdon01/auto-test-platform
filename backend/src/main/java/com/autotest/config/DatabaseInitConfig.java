package com.autotest.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;

/**
 * 数据库初始化配置
 * 在数据源创建之前自动创建数据库目录
 *
 * @author auto-test-platform
 */
@Slf4j
@Configuration
public class DatabaseInitConfig {

    /**
     * 自定义数据源配置，在创建连接池之前确保数据库目录存在
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        // 在创建数据源之前，先创建数据库目录
        String url = properties.getUrl();
        if (url != null && url.startsWith("jdbc:sqlite:")) {
            createDatabaseDirectory(url);
        }
        
        // 创建数据源
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
    
    /**
     * 创建 SQLite 数据库目录
     */
    private void createDatabaseDirectory(String url) {
        String dbPath = url.substring("jdbc:sqlite:".length());
        
        // 移除查询参数
        int queryIndex = dbPath.indexOf('?');
        if (queryIndex > 0) {
            dbPath = dbPath.substring(0, queryIndex);
        }
        
        // 处理变量替换
        dbPath = dbPath.replace("${user.home}", System.getProperty("user.home"));
        
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (created) {
                log.info("已自动创建数据库目录：{}", parentDir.getAbsolutePath());
            } else {
                log.warn("创建数据库目录失败：{}", parentDir.getAbsolutePath());
            }
        }
    }
}
