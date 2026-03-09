package com.autotest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 存储配置
 *
 * @author auto-test-platform
 */
@Configuration
public class StorageConfig {

    @Value("${autotest.storage.scripts-path}")
    private String scriptsPath;

    @Value("${autotest.storage.reports-path}")
    private String reportsPath;

    @Value("${autotest.storage.temp-path}")
    private String tempPath;

    @Value("${autotest.storage.results-path}")
    private String resultsPath;

    public String getScriptsPath() {
        return scriptsPath;
    }

    public String getReportsPath() {
        return reportsPath;
    }

    public String getTempPath() {
        return tempPath;
    }

    public String getResultsPath() {
        return resultsPath;
    }
}
