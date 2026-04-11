package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.entity.ScriptVersion;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 脚本版本服务接口
 *
 * @author auto-test-platform
 */
public interface ScriptVersionService {

    /**
     * 获取脚本的版本列表
     */
    PageResult<ScriptVersion> listVersions(Long scriptId, Integer page, Integer size);

    /**
     * 获取版本详情
     */
    ScriptVersion getVersion(Long scriptId, String version);

    /**
     * 获取版本详情（按ID）
     */
    ScriptVersion getVersionById(Long versionId);

    /**
     * 创建新版本
     */
    ScriptVersion createVersion(Long scriptId, ScriptVersion version);

    /**
     * 更新版本
     */
    ScriptVersion updateVersion(Long scriptId, Long versionId, ScriptVersion version);

    /**
     * 删除版本
     */
    void deleteVersion(Long scriptId, Long versionId);

    /**
     * 回退到指定版本
     */
    void rollbackToVersion(Long scriptId, String version);

    /**
     * 版本对比
     */
    Map<String, Object> compareVersions(Long scriptId, String version1, String version2);

    /**
     * 设置当前版本
     */
    void setCurrentVersion(Long scriptId, String version);
}
