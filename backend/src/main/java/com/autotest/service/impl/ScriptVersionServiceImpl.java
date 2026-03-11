package com.autotest.service.impl;

import com.autotest.common.PageResult;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.service.ScriptVersionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 脚本版本服务实现
 *
 * @author auto-test-platform
 */
@Service
@RequiredArgsConstructor
public class ScriptVersionServiceImpl implements ScriptVersionService {

    private final ScriptVersionMapper versionMapper;
    private final ScriptMapper scriptMapper;

    @Override
    public PageResult<ScriptVersion> listVersions(Long scriptId, Integer page, Integer size) {
        LambdaQueryWrapper<ScriptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptVersion::getScriptId, scriptId)
                .orderByDesc(ScriptVersion::getCreatedAt);

        Page<ScriptVersion> pageObj = new Page<>(page, size);
        return PageResult.of(versionMapper.selectPage(pageObj, wrapper));
    }

    @Override
    public ScriptVersion getVersion(Long scriptId, String version) {
        LambdaQueryWrapper<ScriptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getVersion, version);
        return versionMapper.selectOne(wrapper);
    }

    @Override
    public ScriptVersion getVersionById(Long versionId) {
        return versionMapper.selectById(versionId);
    }

    @Override
    @Transactional
    public ScriptVersion createVersion(Long scriptId, ScriptVersion version) {
        // 检查脚本是否存在
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new RuntimeException("脚本不存在");
        }

        // 自动生成版本号（如果未指定）
        if (version.getVersion() == null || version.getVersion().isEmpty()) {
            version.setVersion(generateNextVersion(scriptId));
        } else {
            // 检查版本号是否已存在
            ScriptVersion existing = getVersion(scriptId, version.getVersion());
            if (existing != null) {
                throw new RuntimeException("版本号已存在");
            }
        }

        version.setScriptId(scriptId);
        version.setCreatedAt(LocalDateTime.now());
        
        // 设置默认值，避免数据库非空约束错误
        if (version.getFileList() == null) {
            // 继承脚本的文件列表，如果脚本也没有则使用空数组
            if (script.getFileList() != null) {
                version.setFileList(script.getFileList());
            } else {
                version.setFileList(new ArrayList<>()); // 设置空数组避免非空约束错误
            }
        }
        if (version.getEntryFile() == null) {
            version.setEntryFile(script.getEntryFile());
        }
        // 设置存储路径默认值
        if (version.getStoragePath() == null) {
            version.setStoragePath("scripts/" + scriptId + "/" + version.getVersion());
        }
        // 设置文件数量默认值
        if (version.getFileCount() == null) {
            version.setFileCount(version.getFileList() != null ? version.getFileList().size() : 0);
        }

        // 如果未指定生命周期模式，继承脚本设置
        if (version.getLifecycleMode() == null) {
            version.setLifecycleMode(script.getLifecycleMode());
            version.setHasDeploy(script.getHasDeploy());
            version.setHasCleanup(script.getHasCleanup());
            version.setDeployEntry(script.getDeployEntry());
            version.setCleanupEntry(script.getCleanupEntry());
        }

        versionMapper.insert(version);
        return version;
    }

    /**
     * 生成下一个版本号
     */
    private String generateNextVersion(Long scriptId) {
        // 获取最新版本
        LambdaQueryWrapper<ScriptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptVersion::getScriptId, scriptId)
                .orderByDesc(ScriptVersion::getCreatedAt)
                .last("LIMIT 1");
        ScriptVersion latest = versionMapper.selectOne(wrapper);
        
        if (latest == null || latest.getVersion() == null) {
            return "v1.0.0";
        }
        
        // 解析版本号并递增
        String currentVersion = latest.getVersion();
        if (currentVersion.startsWith("v")) {
            currentVersion = currentVersion.substring(1);
        }
        
        String[] parts = currentVersion.split("\\.");
        if (parts.length >= 3) {
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                int patch = Integer.parseInt(parts[2]);
                return String.format("v%d.%d.%d", major, minor, patch + 1);
            } catch (NumberFormatException e) {
                return "v1.0.0";
            }
        }
        
        return "v1.0.0";
    }

    @Override
    @Transactional
    public ScriptVersion updateVersion(Long scriptId, Long versionId, ScriptVersion version) {
        ScriptVersion existing = versionMapper.selectById(versionId);
        if (existing == null || !existing.getScriptId().equals(scriptId)) {
            throw new RuntimeException("版本不存在");
        }

        version.setId(versionId);
        version.setScriptId(scriptId);
        versionMapper.updateById(version);
        return versionMapper.selectById(versionId);
    }

    @Override
    @Transactional
    public void deleteVersion(Long scriptId, Long versionId) {
        ScriptVersion version = versionMapper.selectById(versionId);
        if (version == null || !version.getScriptId().equals(scriptId)) {
            throw new RuntimeException("版本不存在");
        }

        // 检查是否为当前版本
        Script script = scriptMapper.selectById(scriptId);
        if (script != null && version.getVersion().equals(script.getCurrentVersion())) {
            throw new RuntimeException("不能删除当前版本");
        }

        versionMapper.deleteById(versionId);
    }

    @Override
    @Transactional
    public void rollbackToVersion(Long scriptId, String version) {
        ScriptVersion scriptVersion = getVersion(scriptId, version);
        if (scriptVersion == null) {
            throw new RuntimeException("版本不存在");
        }

        // 更新脚本的当前版本和相关配置
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new RuntimeException("脚本不存在");
        }

        script.setCurrentVersion(version);
        script.setLifecycleMode(scriptVersion.getLifecycleMode());
        script.setHasDeploy(scriptVersion.getHasDeploy());
        script.setHasCleanup(scriptVersion.getHasCleanup());
        script.setDeployEntry(scriptVersion.getDeployEntry());
        script.setCleanupEntry(scriptVersion.getCleanupEntry());
        script.setEntryFile(scriptVersion.getEntryFile());
        script.setFileList(scriptVersion.getFileList());
        script.setUpdatedAt(LocalDateTime.now());

        scriptMapper.updateById(script);
    }

    @Override
    public Map<String, Object> compareVersions(Long scriptId, String version1, String version2) {
        ScriptVersion v1 = getVersion(scriptId, version1);
        ScriptVersion v2 = getVersion(scriptId, version2);

        if (v1 == null || v2 == null) {
            throw new RuntimeException("版本不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // 基本信息
        result.put("version1", buildVersionInfo(v1));
        result.put("version2", buildVersionInfo(v2));

        // 文件差异
        List<Map<String, Object>> fileDiff = compareFileLists(
                v1.getFileList() != null ? v1.getFileList() : new ArrayList<>(),
                v2.getFileList() != null ? v2.getFileList() : new ArrayList<>()
        );
        result.put("fileDiff", fileDiff);

        // 内容差异（如果有存储内容）
        if (v1.getContent() != null && v2.getContent() != null) {
            result.put("contentDiff", compareContent(v1.getContent(), v2.getContent()));
        }

        // 配置差异
        Map<String, Object> configDiff = new LinkedHashMap<>();
        if (!Objects.equals(v1.getLifecycleMode(), v2.getLifecycleMode())) {
            configDiff.put("lifecycleMode", Map.of("old", v1.getLifecycleMode(), "new", v2.getLifecycleMode()));
        }
        if (!Objects.equals(v1.getEntryFile(), v2.getEntryFile())) {
            configDiff.put("entryFile", Map.of("old", v1.getEntryFile(), "new", v2.getEntryFile()));
        }
        result.put("configDiff", configDiff);

        return result;
    }

    @Override
    @Transactional
    public void setCurrentVersion(Long scriptId, String version) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new RuntimeException("脚本不存在");
        }

        ScriptVersion scriptVersion = getVersion(scriptId, version);
        if (scriptVersion == null) {
            throw new RuntimeException("版本不存在");
        }

        script.setCurrentVersion(version);
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
    }

    private Map<String, Object> buildVersionInfo(ScriptVersion version) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", version.getVersion());
        info.put("fileCount", version.getFileCount());
        info.put("totalSize", version.getTotalSize());
        info.put("entryFile", version.getEntryFile());
        info.put("changeLog", version.getChangeLog());
        info.put("createdAt", version.getCreatedAt());
        return info;
    }

    private List<Map<String, Object>> compareFileLists(List<Map<String, Object>> list1, List<Map<String, Object>> list2) {
        List<Map<String, Object>> diff = new ArrayList<>();

        Map<String, Map<String, Object>> map1 = new HashMap<>();
        Map<String, Map<String, Object>> map2 = new HashMap<>();

        for (Map<String, Object> file : list1) {
            map1.put((String) file.get("path"), file);
        }
        for (Map<String, Object> file : list2) {
            map2.put((String) file.get("path"), file);
        }

        // 删除的文件
        for (String path : map1.keySet()) {
            if (!map2.containsKey(path)) {
                diff.add(Map.of("path", path, "status", "deleted", "old", map1.get(path)));
            }
        }

        // 新增的文件
        for (String path : map2.keySet()) {
            if (!map1.containsKey(path)) {
                diff.add(Map.of("path", path, "status", "added", "new", map2.get(path)));
            }
        }

        // 修改的文件
        for (String path : map1.keySet()) {
            if (map2.containsKey(path)) {
                Map<String, Object> f1 = map1.get(path);
                Map<String, Object> f2 = map2.get(path);

                if (!Objects.equals(f1.get("size"), f2.get("size")) ||
                        !Objects.equals(f1.get("checksum"), f2.get("checksum"))) {
                    diff.add(Map.of("path", path, "status", "modified", "old", f1, "new", f2));
                }
            }
        }

        return diff;
    }

    private List<Map<String, Object>> compareContent(String content1, String content2) {
        List<Map<String, Object>> diff = new ArrayList<>();

        String[] lines1 = content1.split("\n");
        String[] lines2 = content2.split("\n");

        int maxLines = Math.max(lines1.length, lines2.length);
        for (int i = 0; i < maxLines; i++) {
            String line1 = i < lines1.length ? lines1[i] : "";
            String line2 = i < lines2.length ? lines2[i] : "";

            if (!line1.equals(line2)) {
                Map<String, Object> lineDiff = new LinkedHashMap<>();
                lineDiff.put("line", i + 1);
                lineDiff.put("old", line1);
                lineDiff.put("new", line2);
                diff.add(lineDiff);
            }
        }

        return diff;
    }
}
