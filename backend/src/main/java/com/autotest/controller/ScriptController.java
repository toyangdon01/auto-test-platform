package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.Task;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.service.ScriptFileService;
import com.autotest.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 脚本管理控制器
 *
 * @author auto-test-platform
 */
@Slf4j
@Tag(name = "scripts", description = "脚本管理")
@RestController
@RequestMapping("/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ScriptFileService scriptFileService;
    private final TaskMapper taskMapper;
    private final TaskService taskService;

    @Operation(summary = "获取脚本列表")
    @GetMapping
    public ApiResponse<PageResult<Script>> listScripts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String testCategory,
            @RequestParam(required = false) String status) {
        
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Script> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        
        if (name != null && !name.isEmpty()) {
            wrapper.like(Script::getName, name);
        }
        if (testCategory != null && !testCategory.isEmpty()) {
            wrapper.eq(Script::getTestCategory, testCategory);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Script::getStatus, status);
        }
        wrapper.orderByDesc(Script::getCreatedAt);
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Script> pageObj = 
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        
        return ApiResponse.success(PageResult.of(scriptMapper.selectPage(pageObj, wrapper)));
    }

    @Operation(summary = "获取脚本详情")
    @GetMapping("/{id}")
    public ApiResponse<Script> getScript(@PathVariable Long id) {
        Script script = scriptMapper.selectById(id);
        if (script == null) {
            return ApiResponse.success(null);
        }
        
        // 查询当前版本的角色定义和输出配置
        if (script.getCurrentVersion() != null) {
            ScriptVersion version = scriptVersionMapper.selectOne(
                new LambdaQueryWrapper<ScriptVersion>()
                    .eq(ScriptVersion::getScriptId, id)
                    .eq(ScriptVersion::getVersion, script.getCurrentVersion())
            );
            if (version != null) {
                if (version.getRoles() != null) {
                    script.setRoles(version.getRoles());
                }
                if (version.getOutputConfig() != null) {
                    script.setOutputConfig(version.getOutputConfig());
                }
                if (version.getSteps() != null) {
                    script.setSteps(version.getSteps());
                }
                if (version.getParameters() != null) {
                    script.setParameters(version.getParameters());
                }
            }
        }
        
        return ApiResponse.success(script);
    }

    @Operation(summary = "上传脚本文件")
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadScriptFile(
            @RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = scriptFileService.uploadScriptFile(file);
        return ApiResponse.success(result);
    }

    @Operation(summary = "创建脚本（带文件）")
    @PostMapping("/create")
    public ApiResponse<Script> createScriptWithFiles(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "testCategory", required = false) String testCategory,
            @RequestParam(value = "lifecycleMode", defaultValue = "simple") String lifecycleMode,
            @RequestParam(value = "entryFile", required = false) String entryFile,
            @RequestParam(value = "deployEntry", required = false) String deployEntry,
            @RequestParam(value = "cleanupEntry", required = false) String cleanupEntry,
            @RequestParam(value = "fileData", required = false) String fileDataJson) throws IOException {
        
        Script script = new Script();
        script.setName(name);
        script.setDescription(description);
        script.setTestCategory(testCategory);
        script.setLifecycleMode(lifecycleMode);
        script.setEntryFile(entryFile);
        script.setDeployEntry(deployEntry);
        script.setCleanupEntry(cleanupEntry);
        script.setCurrentVersion("v1.0.0");
        script.setHasDeploy("full".equals(lifecycleMode) && deployEntry != null && !deployEntry.isEmpty());
        script.setHasCleanup("full".equals(lifecycleMode) && cleanupEntry != null && !cleanupEntry.isEmpty());
        script.setStatus("enabled");
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        script.setFileList(new ArrayList<>());
        
        scriptMapper.insert(script);
        
        return ApiResponse.success(script);
    }

    @Operation(summary = "创建脚本")
    @PostMapping
    public ApiResponse<Script> createScript(@RequestBody Script script) {
        script.setCurrentVersion("v1.0.0");
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        
        // 确保 fileList 不为 null，避免数据库非空约束错误
        if (script.getFileList() == null) {
            script.setFileList(new ArrayList<>());
        }
        
        // 从角色配置中提取入口脚本（新版简化逻辑）
        if (script.getRoles() != null && !script.getRoles().isEmpty()) {
            // 角色配置格式: { "default": { "entryScript": "main.sh", ... }, ... }
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> rolesMap = mapper.convertValue(script.getRoles(), Map.class);
                
                // 优先从 default 角色获取入口脚本
                if (rolesMap.containsKey("default")) {
                    Object defaultRole = rolesMap.get("default");
                    if (defaultRole instanceof Map) {
                        Map<?, ?> defaultRoleMap = (Map<?, ?>) defaultRole;
                        if (defaultRoleMap.containsKey("entryScript")) {
                            String entryScript = (String) defaultRoleMap.get("entryScript");
                            if (entryScript != null && !entryScript.isEmpty()) {
                                script.setEntryFile(entryScript);
                            }
                        }
                        if (defaultRoleMap.containsKey("deployScript")) {
                            String deployScript = (String) defaultRoleMap.get("deployScript");
                            if (deployScript != null && !deployScript.isEmpty()) {
                                script.setDeployEntry(deployScript);
                                script.setHasDeploy(true);
                            }
                        }
                        if (defaultRoleMap.containsKey("cleanupScript")) {
                            String cleanupScript = (String) defaultRoleMap.get("cleanupScript");
                            if (cleanupScript != null && !cleanupScript.isEmpty()) {
                                script.setCleanupEntry(cleanupScript);
                                script.setHasCleanup(true);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析角色配置失败: {}", e.getMessage());
            }
        }
        
        // 如果仍没有入口文件，尝试从 fileList 中找 main.sh
        if (script.getEntryFile() == null || script.getEntryFile().isEmpty()) {
            if (script.getFileList() != null) {
                for (Object fileObj : script.getFileList()) {
                    if (fileObj instanceof Map) {
                        Map<?, ?> file = (Map<?, ?>) fileObj;
                        String path = (String) file.get("path");
                        if (path != null && path.equals("main.sh")) {
                            script.setEntryFile("main.sh");
                            break;
                        }
                    }
                }
            }
        }
        
        // 根据入口文件自动设置脚本类型
        if (script.getScriptType() == null || script.getScriptType().isEmpty()) {
            String entryFile = script.getEntryFile();
            if (entryFile != null && !entryFile.isEmpty()) {
                if (entryFile.endsWith(".sh")) {
                    script.setScriptType("shell");
                } else if (entryFile.endsWith(".py")) {
                    script.setScriptType("python");
                } else {
                    script.setScriptType("shell"); // 默认 shell
                }
            } else {
                script.setScriptType("shell"); // 默认 shell
            }
        }
        
        // 设置生命周期模式（新版统一用 simple）
        if (script.getLifecycleMode() == null || script.getLifecycleMode().isEmpty()) {
            script.setLifecycleMode("simple");
        }
        
        scriptMapper.insert(script);
        
        // 处理上传的临时文件（从 tempFilePath 复制到脚本目录）
        if (script.getTempFilePath() != null && !script.getTempFilePath().isEmpty()) {
            try {
                copyFromTempToScriptDir(script);
            } catch (Exception e) {
                log.error("复制临时文件失败: {}", e.getMessage());
            }
        }
        
        // 如果有脚本内容（在线编辑模式），保存到文件系统
        try {
            saveScriptContentFromEditor(script);
        } catch (IOException e) {
            log.warn("保存脚本内容失败: {}", e.getMessage());
        }
        
        // 从临时目录复制文件到脚本目录
        try {
            copyFromTempToScriptDir(script);
        } catch (IOException e) {
            log.warn("复制临时文件失败: {}", e.getMessage());
        }
        
        // 创建初始版本记录，包含角色定义
        createInitialVersion(script);
        
        return ApiResponse.success(script);
    }
    
    /**
     * 创建初始脚本版本
     */
    private void createInitialVersion(Script script) {
        ScriptVersion version = new ScriptVersion();
        version.setScriptId(script.getId());
        version.setVersion("v1.0.0");
        version.setLifecycleMode(script.getLifecycleMode());
        version.setHasDeploy(script.getHasDeploy());
        version.setHasCleanup(script.getHasCleanup());
        version.setDeployEntry(script.getDeployEntry());
        version.setCleanupEntry(script.getCleanupEntry());
        version.setEntryFile(script.getEntryFile());
        version.setFileList(script.getFileList());
        version.setFileCount(script.getFileList() != null ? script.getFileList().size() : 0);
        version.setStoragePath(scriptFileService.getScriptPath(script.getId()));
        version.setCreatedAt(LocalDateTime.now());
        
        // 保存角色定义
        if (script.getRoles() != null && !script.getRoles().isEmpty()) {
            version.setRoles(script.getRoles());
        }
        
        // 保存输出收集配置
        if (script.getOutputConfig() != null && !script.getOutputConfig().isEmpty()) {
            version.setOutputConfig(script.getOutputConfig());
        }
        
        // 保存执行步骤配置
        if (script.getSteps() != null && !script.getSteps().isEmpty()) {
            version.setSteps(script.getSteps());
        }
        
        // 保存共享参数定义
        if (script.getParameters() != null && !script.getParameters().isEmpty()) {
            version.setParameters(script.getParameters());
        }
        
        scriptVersionMapper.insert(version);
        log.info("创建脚本版本: scriptId={}, version={}", script.getId(), version.getVersion());
    }
    
    /**
     * 从临时目录复制文件到脚本目录
     */
    private void copyFromTempToScriptDir(Script script) throws IOException {
        String tempPath = script.getTempFilePath();
        if (tempPath == null || tempPath.isEmpty()) {
            return;
        }
        
        Path tempFile = Paths.get(tempPath);
        if (!Files.exists(tempFile)) {
            log.warn("临时文件不存在: {}", tempPath);
            return;
        }
        
        // 脚本目录包含版本号：scripts/{scriptId}/{version}/
        String version = script.getCurrentVersion() != null ? script.getCurrentVersion() : "v1.0.0";
        String targetPath = scriptFileService.getScriptPath(script.getId()) + "/" + version;
        Path targetDir = Paths.get(targetPath);
        Files.createDirectories(targetDir);
        
        // 如果是 zip 文件，解压
        if (tempPath.endsWith(".zip")) {
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(tempFile))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = targetDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zis, entryPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            log.info("解压脚本文件: {} -> {}", tempPath, targetPath);
        } else if (Files.isDirectory(tempFile)) {
            // 临时目录，递归复制所有文件
            Files.walk(tempFile)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        Path relativePath = tempFile.relativize(path);
                        Path targetFile = targetDir.resolve(relativePath.toString());
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(path, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.error("复制文件失败: {}", path, e);
                    }
                });
            log.info("复制临时目录: {} -> {}", tempPath, targetPath);
        } else {
            // 单文件，直接复制
            Path targetFile = targetDir.resolve(tempFile.getFileName().toString());
            Files.copy(tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("复制脚本文件: {} -> {}", tempPath, targetFile);
        }
        
        // 扫描目标目录，生成文件列表
        List<Map<String, Object>> actualFileList = scanScriptDirectory(targetDir);
        script.setFileList(actualFileList);
        log.info("更新文件列表: {} 个文件", actualFileList.size());
        
        // 清理临时文件
        try {
            Files.deleteIfExists(tempFile);
            // 如果临时目录为空，也删除
            Path tempParent = tempFile.getParent();
            if (tempParent != null && Files.list(tempParent).findAny().isEmpty()) {
                Files.deleteIfExists(tempParent);
            }
        } catch (Exception e) {
            log.debug("清理临时文件失败: {}", e.getMessage());
        }
    }
    
    /**
     * 扫描脚本目录生成文件列表
     */
    private List<Map<String, Object>> scanScriptDirectory(Path scriptDir) throws IOException {
        List<Map<String, Object>> fileList = new ArrayList<>();
        
        if (!Files.exists(scriptDir)) {
            return fileList;
        }
        
        Files.walk(scriptDir)
            .filter(path -> !Files.isDirectory(path))
            .forEach(path -> {
                try {
                    Path relativePath = scriptDir.relativize(path);
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("path", relativePath.toString().replace("\\", "/"));
                    fileInfo.put("name", path.getFileName().toString());
                    fileInfo.put("size", Files.size(path));
                    fileList.add(fileInfo);
                } catch (IOException e) {
                    log.warn("读取文件信息失败: {}", path);
                }
            });
        
        return fileList;
    }
    
    /**
     * 保存在线编辑的脚本内容到文件系统
     */
    private void saveScriptContentFromEditor(Script script) throws IOException {
        String scriptsPathStr = scriptFileService.getScriptPath(script.getId());
        Path scriptDir = Paths.get(scriptsPathStr);
        Files.createDirectories(scriptDir);
        
        boolean saved = false;
        
        // 保存执行脚本内容
        if (script.getRunContent() != null && !script.getRunContent().isEmpty()) {
            String entryFile = script.getEntryFile();
            if (entryFile == null || entryFile.isEmpty()) {
                entryFile = "script.sh";
            }
            Path targetPath = scriptDir.resolve(entryFile);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, script.getRunContent());
            log.info("保存执行脚本: {}", targetPath);
            saved = true;
        }
        
        // 保存部署脚本内容
        if (script.getDeployContent() != null && !script.getDeployContent().isEmpty()) {
            String deployEntry = script.getDeployEntry();
            if (deployEntry == null || deployEntry.isEmpty()) {
                deployEntry = "deploy.sh";
            }
            Path targetPath = scriptDir.resolve(deployEntry);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, script.getDeployContent());
            log.info("保存部署脚本: {}", targetPath);
            saved = true;
        }
        
        // 保存卸载脚本内容
        if (script.getCleanupContent() != null && !script.getCleanupContent().isEmpty()) {
            String cleanupEntry = script.getCleanupEntry();
            if (cleanupEntry == null || cleanupEntry.isEmpty()) {
                cleanupEntry = "cleanup.sh";
            }
            Path targetPath = scriptDir.resolve(cleanupEntry);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, script.getCleanupContent());
            log.info("保存卸载脚本: {}", targetPath);
            saved = true;
        }
        
        // 如果 fileList 中有 content 字段，也保存
        List<Map<String, Object>> fileList = script.getFileList();
        if (fileList != null && !fileList.isEmpty()) {
            for (Map<String, Object> file : fileList) {
                String path = (String) file.get("path");
                String content = (String) file.get("content");
                if (path != null && content != null) {
                    Path targetPath = scriptDir.resolve(path);
                    Files.createDirectories(targetPath.getParent());
                    Files.writeString(targetPath, content);
                    log.info("保存脚本文件: {}", targetPath);
                    saved = true;
                }
            }
        }
        
        if (!saved) {
            log.warn("没有脚本内容需要保存，脚本ID: {}", script.getId());
        }
    }

    @Operation(summary = "更新脚本")
    @PutMapping("/{id}")
    public ApiResponse<Void> updateScript(@PathVariable Long id, @RequestBody Script script) throws IOException {
        script.setId(id);
        script.setUpdatedAt(LocalDateTime.now());
        
        // 获取现有脚本信息
        Script existingScript = scriptMapper.selectById(id);
        if (existingScript == null) {
            return ApiResponse.error(404, "脚本不存在");
        }
        
        // 处理临时文件上传
        if (script.getTempFilePath() != null && !script.getTempFilePath().isEmpty()) {
            try {
                copyFromTempToScriptDir(script);
            } catch (Exception e) {
                log.error("复制临时文件失败: {}", e.getMessage());
            }
        }
        
        // 如果有脚本内容（在线编辑模式），保存到文件系统
        saveScriptContentFromEditor(script);
        
        // 更新脚本主表
        scriptMapper.updateById(script);
        
        // 更新当前版本的 ScriptVersion
        if (existingScript.getCurrentVersion() != null) {
            ScriptVersion version = scriptVersionMapper.selectOne(
                new LambdaQueryWrapper<ScriptVersion>()
                    .eq(ScriptVersion::getScriptId, id)
                    .eq(ScriptVersion::getVersion, existingScript.getCurrentVersion())
            );
            if (version != null) {
                // 更新文件列表
                if (script.getFileList() != null && !script.getFileList().isEmpty()) {
                    version.setFileList(script.getFileList());
                    version.setFileCount(script.getFileList().size());
                }
                // 更新入口文件
                if (script.getEntryFile() != null) {
                    version.setEntryFile(script.getEntryFile());
                }
                // 更新生命周期配置
                if (script.getLifecycleMode() != null) {
                    version.setLifecycleMode(script.getLifecycleMode());
                }
                if (script.getHasDeploy() != null) {
                    version.setHasDeploy(script.getHasDeploy());
                }
                if (script.getHasCleanup() != null) {
                    version.setHasCleanup(script.getHasCleanup());
                }
                if (script.getDeployEntry() != null) {
                    version.setDeployEntry(script.getDeployEntry());
                }
                if (script.getCleanupEntry() != null) {
                    version.setCleanupEntry(script.getCleanupEntry());
                }
                // 更新角色定义
                if (script.getRoles() != null && !script.getRoles().isEmpty()) {
                    version.setRoles(script.getRoles());
                    log.info("更新脚本角色定义: scriptId={}, version={}", id, version.getVersion());
                }
                // 更新输出收集配置
                if (script.getOutputConfig() != null && !script.getOutputConfig().isEmpty()) {
                    version.setOutputConfig(script.getOutputConfig());
                    log.info("更新输出收集配置: scriptId={}, version={}", id, version.getVersion());
                }
                // 更新执行步骤配置
                if (script.getSteps() != null && !script.getSteps().isEmpty()) {
                    version.setSteps(script.getSteps());
                    log.info("更新执行步骤配置: scriptId={}, version={}", id, version.getVersion());
                }
                // 更新共享参数定义
                if (script.getParameters() != null && !script.getParameters().isEmpty()) {
                    version.setParameters(script.getParameters());
                    log.info("更新共享参数定义: scriptId={}, version={}", id, version.getVersion());
                }
                scriptVersionMapper.updateById(version);
            }
        }
        
        return ApiResponse.success();
    }

    @Operation(summary = "删除脚本")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteScript(@PathVariable Long id) throws IOException {
        // 查询使用此脚本的所有任务
        List<Task> tasks = taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
            .eq(Task::getScriptId, id));
        
        if (!tasks.isEmpty()) {
            log.info("删除脚本前清理关联任务：scriptId={}, taskCount={}", id, tasks.size());
            // 删除关联的任务（级联删除 task_servers, task_steps, test_results 等）
            for (Task task : tasks) {
                taskService.deleteTask(task.getId());
            }
        }
        
        // 删除脚本文件
        scriptFileService.deleteScriptFiles(id);
        
        // 删除脚本记录
        scriptMapper.deleteById(id);
        
        log.info("脚本删除成功：scriptId={}", id);
        return ApiResponse.success();
    }

    @Operation(summary = "获取脚本文件内容")
    @GetMapping("/{id}/files/{*filePath}")
    public ApiResponse<Map<String, Object>> getScriptFile(
            @PathVariable Long id,
            @PathVariable String filePath) throws IOException {
        // 获取脚本信息，找到当前版本
        Script script = scriptMapper.selectById(id);
        if (script == null) {
            return ApiResponse.error(404, "脚本不存在");
        }
        
        String version = script.getCurrentVersion();
        if (version == null || version.isEmpty()) {
            version = "v1.0.0";
        }
        
        // 构建完整路径（包含版本号）
        String fullPath = version + "/" + filePath;
        String content = scriptFileService.readScriptFile(id, fullPath);
        Map<String, Object> result = new HashMap<>();
        result.put("path", filePath);
        result.put("content", content);
        return ApiResponse.success(result);
    }

    @Operation(summary = "更新脚本文件内容")
    @PutMapping("/{id}/files/{*filePath}")
    public ApiResponse<Void> updateScriptFile(
            @PathVariable Long id,
            @PathVariable String filePath,
            @RequestBody Map<String, String> body) throws IOException {
        // 获取脚本信息，找到当前版本
        Script script = scriptMapper.selectById(id);
        if (script == null) {
            return ApiResponse.error(404, "脚本不存在");
        }
        
        String version = script.getCurrentVersion();
        if (version == null || version.isEmpty()) {
            version = "v1.0.0";
        }
        
        String fullPath = version + "/" + filePath;
        String content = body.get("content");
        scriptFileService.updateScriptFile(id, fullPath, content);
        return ApiResponse.success();
    }

    @Operation(summary = "列出脚本文件")
    @GetMapping("/{id}/file-list")
    public ApiResponse<List<String>> listScriptFiles(@PathVariable Long id) throws IOException {
        return ApiResponse.success(scriptFileService.listScriptFiles(id));
    }

    // ==================== 脚本导出 ====================

    @Operation(summary = "导出脚本")
    @GetMapping("/{id}/export")
    public ResponseEntity<Resource> exportScript(
            @PathVariable Long id,
            @RequestParam(defaultValue = "zip") String format) throws IOException {
        Script script = scriptMapper.selectById(id);
        if (script == null) {
            return ResponseEntity.notFound().build();
        }
        
        String exportPath = scriptFileService.exportScript(script, format);
        Resource resource = new FileSystemResource(exportPath);
        
        String filename = script.getName() + "." + format;
        // 支持 UTF-8 编码的文件名（中文文件名）
        String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    // ==================== 版本快捷操作 ====================

    @Operation(summary = "回退到指定版本")
    @PostMapping("/{id}/rollback/{version}")
    public ApiResponse<Void> rollbackVersion(
            @PathVariable Long id,
            @PathVariable String version) {
        Script script = scriptMapper.selectById(id);
        if (script == null) {
            throw new RuntimeException("脚本不存在");
        }
        
        // 更新当前版本
        script.setCurrentVersion(version);
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        
        return ApiResponse.success();
    }

    // ==================== 角色定义 API ====================

    @Operation(summary = "获取脚本版本的角色定义")
    @GetMapping("/{id}/versions/{version}/roles")
    public ApiResponse<Object> getScriptRoles(
            @PathVariable Long id,
            @PathVariable String version) {
        ScriptVersion scriptVersion = scriptVersionMapper.selectOne(
            new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, id)
                .eq(ScriptVersion::getVersion, version)
        );
        
        if (scriptVersion == null) {
            return ApiResponse.success(Collections.emptyList());
        }
        
        return ApiResponse.success(scriptVersion.getRoles());
    }

    @Operation(summary = "更新脚本版本的角色定义")
    @PutMapping("/{id}/versions/{version}/roles")
    public ApiResponse<Void> updateScriptRoles(
            @PathVariable Long id,
            @PathVariable String version,
            @RequestBody Map<String, Object> roles) {
        ScriptVersion scriptVersion = scriptVersionMapper.selectOne(
            new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, id)
                .eq(ScriptVersion::getVersion, version)
        );
        
        if (scriptVersion == null) {
            throw new RuntimeException("脚本版本不存在");
        }
        
        scriptVersion.setRoles(roles);
        scriptVersionMapper.updateById(scriptVersion);
        
        return ApiResponse.success();
    }

    @Operation(summary = "获取脚本版本的简化角色信息（用于任务创建）")
    @GetMapping("/{id}/versions/{version}/roles/summary")
    public ApiResponse<List<Map<String, Object>>> getScriptRolesSummary(
            @PathVariable Long id,
            @PathVariable String version) {
        ScriptVersion scriptVersion = scriptVersionMapper.selectOne(
            new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, id)
                .eq(ScriptVersion::getVersion, version)
        );
        
        if (scriptVersion == null || scriptVersion.getRoles() == null) {
            return ApiResponse.success(Collections.emptyList());
        }
        
        // 提取简化的角色信息
        List<Map<String, Object>> summary = new ArrayList<>();
        Map<String, Object> roles = scriptVersion.getRoles();
        
        if (roles.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> roleList = (List<Map<String, Object>>) roles.get("roles");
            for (Map<String, Object> role : roleList) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", role.get("name"));
                info.put("displayName", role.get("displayName"));
                info.put("description", role.get("description"));
                info.put("params", role.get("params"));
                info.put("dependsOn", role.get("dependsOn"));
                info.put("resultCollector", role.get("resultCollector"));
                summary.add(info);
            }
        } else {
            // 新格式：直接以角色名为 key
            for (Map.Entry<String, Object> entry : roles.entrySet()) {
                String roleName = entry.getKey();
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> roleData = (Map<String, Object>) entry.getValue();
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", roleName);
                    info.put("displayName", roleData.get("displayName"));
                    info.put("description", roleData.get("description"));
                    info.put("params", roleData.get("params"));
                    info.put("dependsOn", roleData.get("dependsOn"));
                    info.put("resultCollector", roleData.get("resultCollector"));
                    // 新版脚本选择字段
                    info.put("entryScript", roleData.get("entryScript"));
                    info.put("deployScript", roleData.get("deployScript"));
                    info.put("cleanupScript", roleData.get("cleanupScript"));
                    // 兼容旧字段
                    info.put("entryFunction", roleData.get("entryFunction"));
                    info.put("startupProbe", roleData.get("startupProbe"));
                    summary.add(info);
                }
            }
        }
        
        return ApiResponse.success(summary);
    }
}
