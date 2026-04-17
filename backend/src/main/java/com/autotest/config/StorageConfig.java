package com.autotest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 存储配置
 * 自动创建所需的存储目录
 *
 * @author auto-test-platform
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Value("${autotest.storage.scripts-path}")
    private String scriptsPath;

    @Value("${autotest.storage.temp-path}")
    private String tempPath;

    @Value("${autotest.storage.results-path}")
    private String resultsPath;

    public String getScriptsPath() {
        return scriptsPath;
    }

    public String getTempPath() {
        return tempPath;
    }

    public String getResultsPath() {
        return resultsPath;
    }

    /**
     * 应用启动时自动创建所有存储目录
     */
    @Bean
    public CommandLineRunner initStorageDirectories() {
        return args -> {
            List<String> paths = Arrays.asList(
                scriptsPath.replace("${user.home}", System.getProperty("user.home")),
                tempPath.replace("${user.home}", System.getProperty("user.home")),
                resultsPath.replace("${user.home}", System.getProperty("user.home"))
            );

            for (String path : paths) {
                File dir = new File(path);
                if (!dir.exists()) {
                    boolean created = dir.mkdirs();
                    if (created) {
                        log.info("已创建存储目录：{}", dir.getAbsolutePath());
                    } else {
                        log.warn("创建存储目录失败：{}", dir.getAbsolutePath());
                    }
                }
            }
        };
    }
}
