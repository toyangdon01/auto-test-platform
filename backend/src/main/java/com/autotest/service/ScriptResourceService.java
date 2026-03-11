package com.autotest.service;

import com.autotest.entity.ScriptResource;

import java.util.List;

/**
 * 脚本资源关联服务接口
 */
public interface ScriptResourceService {

    /**
     * 获取脚本的资源列表
     */
    List<ScriptResource> getByScriptId(Long scriptId);

    /**
     * 添加资源关联
     */
    ScriptResource addResource(Long scriptId, Long resourceId, String targetPath, String permissions, Integer uploadOrder);

    /**
     * 更新资源关联
     */
    ScriptResource updateResource(Long scriptId, Long resourceId, String targetPath, String permissions, Integer uploadOrder);

    /**
     * 删除资源关联
     */
    void removeResource(Long scriptId, Long resourceId);

    /**
     * 删除脚本的所有资源关联
     */
    void removeAllByScriptId(Long scriptId);
}
