package com.autotest.service.impl;

import com.autotest.entity.ResourceFile;
import com.autotest.entity.ScriptResource;
import com.autotest.mapper.ResourceFileMapper;
import com.autotest.mapper.ScriptResourceMapper;
import com.autotest.service.ScriptResourceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 脚本资源关联服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptResourceServiceImpl implements ScriptResourceService {

    private final ScriptResourceMapper scriptResourceMapper;
    private final ResourceFileMapper resourceFileMapper;

    @Override
    public List<ScriptResource> getByScriptId(Long scriptId) {
        List<ScriptResource> resources = scriptResourceMapper.findByScriptIdWithResource(scriptId);

        // 填充资源文件信息
        for (ScriptResource sr : resources) {
            ResourceFile rf = resourceFileMapper.selectById(sr.getResourceId());
            sr.setResource(rf);
        }

        return resources;
    }

    @Override
    public ScriptResource addResource(Long scriptId, Long resourceId, String targetPath, String permissions, Integer uploadOrder) {
        // 检查是否已存在
        LambdaQueryWrapper<ScriptResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptResource::getScriptId, scriptId)
               .eq(ScriptResource::getResourceId, resourceId);
        if (scriptResourceMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("该资源已关联到此脚本");
        }

        ScriptResource sr = new ScriptResource();
        sr.setScriptId(scriptId);
        sr.setResourceId(resourceId);
        sr.setTargetPath(targetPath != null ? targetPath : "/tmp");
        sr.setPermissions(permissions != null ? permissions : "644");
        sr.setUploadOrder(uploadOrder != null ? uploadOrder : 0);
        sr.setCreatedAt(LocalDateTime.now());

        scriptResourceMapper.insert(sr);
        log.info("添加脚本资源关联: scriptId={}, resourceId={}, targetPath={}", scriptId, resourceId, targetPath);

        return sr;
    }

    @Override
    public ScriptResource updateResource(Long scriptId, Long resourceId, String targetPath, String permissions, Integer uploadOrder) {
        LambdaQueryWrapper<ScriptResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptResource::getScriptId, scriptId)
               .eq(ScriptResource::getResourceId, resourceId);

        ScriptResource sr = scriptResourceMapper.selectOne(wrapper);
        if (sr == null) {
            throw new RuntimeException("资源关联不存在");
        }

        if (targetPath != null) sr.setTargetPath(targetPath);
        if (permissions != null) sr.setPermissions(permissions);
        if (uploadOrder != null) sr.setUploadOrder(uploadOrder);

        scriptResourceMapper.updateById(sr);
        return sr;
    }

    @Override
    public void removeResource(Long scriptId, Long resourceId) {
        LambdaQueryWrapper<ScriptResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptResource::getScriptId, scriptId)
               .eq(ScriptResource::getResourceId, resourceId);
        scriptResourceMapper.delete(wrapper);
        log.info("删除脚本资源关联: scriptId={}, resourceId={}", scriptId, resourceId);
    }

    @Override
    public void removeAllByScriptId(Long scriptId) {
        LambdaQueryWrapper<ScriptResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptResource::getScriptId, scriptId);
        scriptResourceMapper.delete(wrapper);
    }
}
