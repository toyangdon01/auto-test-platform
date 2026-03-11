package com.autotest.service;

import com.autotest.entity.SystemConfig;

import java.util.Map;

/**
 * 系统配置服务接口
 *
 * @author auto-test-platform
 */
public interface SystemConfigService {

    /**
     * 获取所有配置
     *
     * @return 配置Map
     */
    Map<String, String> getAll();

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @return 配置值
     */
    String get(String key);

    /**
     * 获取配置值，带默认值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    String get(String key, String defaultValue);

    /**
     * 设置配置
     *
     * @param key   配置键
     * @param value 配置值
     */
    void set(String key, String value);

    /**
     * 批量设置配置
     *
     * @param configs 配置Map
     */
    void setAll(Map<String, String> configs);

    /**
     * 删除配置
     *
     * @param key 配置键
     */
    void delete(String key);
}
