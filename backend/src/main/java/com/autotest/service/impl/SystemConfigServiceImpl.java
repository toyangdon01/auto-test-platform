package com.autotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.autotest.entity.SystemConfig;
import com.autotest.mapper.SystemConfigMapper;
import com.autotest.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper configMapper;

    @Override
    public Map<String, String> getAll() {
        List<SystemConfig> configs = configMapper.selectList(null);
        Map<String, String> map = new HashMap<>();
        for (SystemConfig config : configs) {
            map.put(config.getKey(), config.getValue());
        }
        return map;
    }

    @Override
    public String get(String key) {
        return get(key, null);
    }

    @Override
    public String get(String key, String defaultValue) {
        SystemConfig config = configMapper.selectById(key);
        if (config != null && config.getValue() != null) {
            return config.getValue();
        }
        return defaultValue;
    }

    @Override
    @Transactional
    public void set(String key, String value) {
        SystemConfig existing = configMapper.selectById(key);

        if (existing != null) {
            existing.setValue(value);
            existing.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(existing);
        } else {
            SystemConfig config = new SystemConfig();
            config.setKey(key);
            config.setValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(config);
        }

        log.info("配置更新: {} = {}", key, value);
    }

    @Override
    @Transactional
    public void setAll(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                set(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void delete(String key) {
        configMapper.deleteById(key);
        log.info("配置删除: {}", key);
    }
}
