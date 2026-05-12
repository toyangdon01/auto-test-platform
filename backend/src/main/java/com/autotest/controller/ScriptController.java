package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.config.ScriptConfig;
import com.autotest.dto.ConfigParseResult;
import com.autotest.dto.ScriptUploadResult;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.Task;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.service.ScriptConfigService;
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
import java.io.PrintWriter;
import java.io.StringWriter;
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
@RequestMapping("/api/v1/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ScriptFileService scriptFileService;
    private final ScriptConfigService scriptConfigService;
    private final TaskMapper taskMapper;
    private final TaskService taskService;

    @Operation(
        summary = "获取脚本列表",
        description = "分页获取脚本列表，支持按名称、分类、状态筛选"
    )
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

    @Operation(
        summary = "获取脚本详情",
        description = "根据ID获取脚本详细信息，包括版本、步骤配置、参数定义等"
    )
    @GetMapping("/{id}")
    public ApiResponse<Script> getScript(@PathVariable Long id) {
        Script script = scriptMapper.selectById(id);
        if (script == null) {
            return ApiResponse.success(null);
        }
        
        // 查询当前版本的输出配置和步骤配置
        if (script.getCurrentVersion() != null) {
            ScriptVersion version = scriptVersionMapper.selectOne(
                new LambdaQueryWrapper<ScriptVersion>()
                    .eq(ScriptVersion::getScriptId, id)
                    .eq(ScriptVersion::getVersion, script.getCurrentVersion())
            );
            if (version != null) {
                // 加载文件列表
                if (version.getFileList() != null && !version.getFileList().isEmpty()) {
                    script.setFileList(version.getFileList());
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

    @Operation(
        summary = "上传脚本文件",
        description = "上传脚本文件（ZIP包或单文件），返回临时路径用于后续创建脚本。\n" +
                     "支持格式：.zip、.sh、.py、.yaml等。\n" +
                     "ZIP包内可包含 autotest.yaml 配置文件，系统会自动解析。"
    )
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadScriptFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tempPath", required = false) String existingTempPath) throws IOException {
        Map<String, Object> result = scriptFileService.uploadScriptFile(file, existingTempPath);
        return ApiResponse.success(result);
    }

    @Operation(summary = "创建脚本（带文件）")
    @PostMapping("/create")
    public ApiResponse<Script> createScriptWithFiles(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "testCategory", required = false) String testCategory,
            @RequestParam(value = "fileData", required = false) String fileDataJson) throws IOException {
        
        Script script = new Script();
        script.setName(name);
        script.setDescription(description);
        script.setTestCategory(testCategory);
        script.setCurrentVersion("v1.0.0");
        script.setStatus("enabled");
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        
        scriptMapper.insert(script);
        
        return ApiResponse.success(script);
    }

    @Operation(
        summary = "创建脚本",
        description = "创建新脚本。\n" +
                     "\n**创建流程：**\n" +
                     "1. 先调用 POST /scripts/upload 上传脚本文件，获取 tempFilePath\n" +
                     "2. 使用 tempFilePath 创建脚本记录\n" +
                     "\n**请求体说明：**\n" +
                     "- name: 脚本名称，必填，唯一\n" +
                     "- scriptType: 脚本类型（shell/python）\n" +
                     "- testCategory: 测试分类（system/database/network/performance/functional）\n" +
                     "- tempFilePath: 上传返回的临时路径\n" +
                     "- steps: 执行步骤配置（可选，若 autotest.yaml 中有则自动解析）\n" +
                     "- parameters: 参数定义（可选）\n" +
                     "\n**自动解析：**\n" +
                     "如果 tempFilePath 指向的目录包含 autotest.yaml，系统会自动解析配置"
    )
    @PostMapping
    public ApiResponse<ScriptUploadResult> createScript(@RequestBody Script script) {
        script.setCurrentVersion("v1.0.0");
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        
        // 确保 fileList 不为 null，避免数据库非空约束错误
        if (script.getFileList() == null) {
            script.setFileList(new ArrayList<>());
        }
        
        // 根据脚本文件自动设置脚本类型
        if (script.getScriptType() == null || script.getScriptType().isEmpty()) {
            script.setScriptType("shell"); // 默认 shell
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
        
        // 兜底解析：如果前端没传配置，尝试从 autotest.yaml 解析
        ConfigParseResult parseResult = null;
        boolean needsAutoParse = (script.getSteps() == null || script.getSteps().isEmpty()) &&
                                  script.getTempFilePath() != null && !script.getTempFilePath().isEmpty();
        
        if (needsAutoParse) {
            parseResult = scriptConfigService.parseConfigFromTempWithDetails(script.getTempFilePath());
            
            if (parseResult.isSuccess() && parseResult.hasConfig()) {
                log.info("前端未传配置，自动解析 autotest.yaml: scriptId={}", script.getId());
                // 应用配置到 script
                ScriptConfig config = parseResult.getConfig();
                if (config.getSteps() != null && !config.getSteps().isEmpty()) {
                    script.setSteps(config.getSteps());
                }
                if (config.getParameters() != null && !config.getParameters().isEmpty()) {
                    // 转换 ParameterConfig 为 Map
                    List<Map<String, Object>> params = new ArrayList<>();
                    for (ScriptConfig.ParameterConfig pc : config.getParameters()) {
                        Map<String, Object> param = new LinkedHashMap<>();
                        param.put("name", pc.getName());
                        param.put("type", pc.getType() != null ? pc.getType() : "string");
                        param.put("default", pc.getDefaultValue());
                        param.put("description", pc.getDescription());
                        params.add(param);
                    }
                    script.setParameters(params);
                }
            } else if (parseResult.getStatus() == ConfigParseResult.Status.PARSE_ERROR) {
                log.warn("autotest.yaml 解析失败: {} at {}", parseResult.getError(), parseResult.getErrorLocation());
            }
        }
        
        // 创建版本记录
        createInitialVersion(script);
        
        return ApiResponse.success(ScriptUploadResult.of(script, parseResult));
    }
    
    /**
     * 创建初始脚本版本
     */
    private void createInitialVersion(Script script) {
        ScriptVersion version = new ScriptVersion();
        version.setScriptId(script.getId());
        version.setVersion("v1.0.0");
        version.setStoragePath(scriptFileService.getScriptPath(script.getId()));
        version.setCreatedAt(LocalDateTime.now());
        
        // 确保 fileList 不为 null，避免数据库非空约束错误
        version.setFileList(new ArrayList<>());
        version.setFileCount(0);
        
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
     * 创建初始脚本版本（带配置）
     */
    private ScriptVersion createInitialVersionWithConfig(Script script, ScriptConfig config) {
        ScriptVersion version = new ScriptVersion();
        version.setScriptId(script.getId());
        version.setVersion("v1.0.0");
        version.setStoragePath(scriptFileService.getScriptPath(script.getId()));
        version.setCreatedAt(LocalDateTime.now());
        
        // 设置文件列表（从临时目录读取）
        if (script.getFileList() != null && !script.getFileList().isEmpty()) {
            version.setFileList(script.getFileList());
            version.setFileCount(script.getFileList().size());
        } else {
            // 确保 fileList 不为 null
            version.setFileList(new ArrayList<>());
            version.setFileCount(0);
        }
        
        // 应用配置到版本
        scriptConfigService.applyConfigToScript(config, script, version);
        
        log.info("创建脚本版本（带配置）: scriptId={}, version={}", script.getId(), version.getVersion());
        return version;
    }
    
    /**
     * 复制 autotest.yaml 配置文件到脚本目录
     */
    private void copyConfigFile(String tempPath, Long scriptId) throws IOException {
        Path sourcePath = Paths.get(tempPath, "autotest.yaml");
        if (!Files.exists(sourcePath)) {
            return;
        }
        
        String scriptDir = scriptFileService.getScriptPath(scriptId);
        Path targetPath = Paths.get(scriptDir, "autotest.yaml");
        
        Files.createDirectories(targetPath.getParent());
        Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("已复制配置文件: {} -> {}", sourcePath, targetPath);
    }
    
    /**
     * 从临时目录复制文件到脚本目录
     * @return 返回解压/复制后的目录路径，用于后续配置解析
     */
    private String copyFromTempToScriptDir(Script script) throws IOException {
        String tempPath = script.getTempFilePath();
        if (tempPath == null || tempPath.isEmpty()) {
            return null;
        }
        
        Path tempFile = Paths.get(tempPath);
        if (!Files.exists(tempFile)) {
            log.warn("临时文件不存在: {}", tempPath);
            return null;
        }
        
        // 脚本目录不再包含版本号：scripts/{scriptId}/
        String targetPath = scriptFileService.getScriptPath(script.getId());
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
        
        // 返回解压后的目录路径，用于后续配置解析
        return targetPath;
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
                    String fileName = relativePath.toString().replace("\\", "/");
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("path", fileName);
                    fileInfo.put("name", path.getFileName().toString());
                    fileInfo.put("size", Files.size(path));
                    fileInfo.put("type", getFileExtension(fileName));
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
        
        // 如果 fileList 中有 content 字段，保存脚本文件
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

    @Operation(
        summary = "更新脚本",
        description = "更新脚本基本信息和配置。\n" +
                     "可以更新：名称、描述、分类、超时时间、步骤配置、参数定义等。\n" +
                     "更新后会创建新版本记录。"
    )
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
        
        // 同步更新 autotest.yaml 配置文件
        scriptConfigService.syncConfig(id);
        
        return ApiResponse.success();
    }

    @Operation(
        summary = "删除脚本",
        description = "删除脚本及其关联的版本记录、文件。\n" +
                     "**注意：** 会级联删除关联此脚本的任务记录。"
    )
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
            @PathVariable String filePath) {
        try {
            // 直接使用前端传入的路径，不再拼接 version
            // 前端应该传入完整路径，如 "v1.0.0/main.sh"
            String cleanPath = filePath.replaceAll("^/+", "").replaceAll("/+", "/");
            
            log.info("读取脚本文件: scriptId={}, filePath={}", id, cleanPath);
            String fullStoragePath = "C:/data/auto-test/scripts/" + id + "/" + cleanPath;
            log.info("完整路径: {}", fullStoragePath);
            
            String content = scriptFileService.readScriptFile(id, cleanPath);
            Map<String, Object> result = new HashMap<>();
            result.put("path", cleanPath);
            result.put("content", content);
            return ApiResponse.success(result);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            log.error("读取脚本文件失败: scriptId={}, filePath={}, error={}\n堆栈:\n{}", id, filePath, e.getMessage(), sw.toString());
            return ApiResponse.error(500, "读取文件失败: " + e.getMessage() + " | " + e.getClass().getName());
        }
    }

    @Operation(summary = "更新脚本文件内容")
    @PutMapping("/{id}/files/{*filePath}")
    public ApiResponse<Void> updateScriptFile(
            @PathVariable Long id,
            @PathVariable String filePath,
            @RequestBody Map<String, String> body) {
        try {
            // 直接使用前端传入的路径
            String cleanPath = filePath.replaceAll("^/+", "").replaceAll("/+", "/");
            
            log.info("更新脚本文件: scriptId={}, filePath={}", id, cleanPath);
            String content = body.get("content");
            scriptFileService.updateScriptFile(id, cleanPath, content);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新脚本文件失败: scriptId={}, filePath={}, error={}", id, filePath, e.getMessage(), e);
            return ApiResponse.error(500, "更新文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "列出脚本文件")
    @GetMapping("/{id}/file-list")
    public ApiResponse<List<String>> listScriptFiles(@PathVariable Long id) throws IOException {
        return ApiResponse.success(scriptFileService.listScriptFiles(id));
    }

    @Operation(summary = "删除脚本文件")
    @DeleteMapping("/{id}/files/{*filePath}")
    public ApiResponse<Void> deleteScriptFile(
            @PathVariable Long id,
            @PathVariable String filePath) {
        try {
            String cleanPath = filePath.replaceAll("^/+", "").replaceAll("/+", "/");
            log.info("删除脚本文件: scriptId={}, file={}", id, cleanPath);
            scriptFileService.deleteScriptFile(id, cleanPath);
            
            // 更新脚本版本的文件列表
            updateScriptFileList(id);
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除脚本文件失败: scriptId={}, file={}, error={}", id, filePath, e.getMessage());
            return ApiResponse.error(500, "删除文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "清空脚本文件")
    @DeleteMapping("/{id}/files")
    public ApiResponse<Void> clearScriptFiles(@PathVariable Long id) {
        try {
            log.info("清空脚本文件: scriptId={}", id);
            scriptFileService.clearScriptFiles(id);
            
            // 更新脚本版本的文件列表为空
            Script script = scriptMapper.selectById(id);
            if (script != null) {
                script.setFileList(new ArrayList<>());
                scriptMapper.updateById(script);
                
                // 更新版本文件列表
                if (script.getCurrentVersion() != null) {
                    ScriptVersion version = scriptVersionMapper.selectOne(
                        new LambdaQueryWrapper<ScriptVersion>()
                            .eq(ScriptVersion::getScriptId, id)
                            .eq(ScriptVersion::getVersion, script.getCurrentVersion())
                    );
                    if (version != null) {
                        version.setFileList(new ArrayList<>());
                        version.setFileCount(0);
                        scriptVersionMapper.updateById(version);
                    }
                }
            }
            
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("清空脚本文件失败: scriptId={}, error={}", id, e.getMessage());
            return ApiResponse.error(500, "清空文件失败: " + e.getMessage());
        }
    }

    /**
     * 更新脚本版本的文件列表
     */
    private void updateScriptFileList(Long scriptId) throws IOException {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;
        
        // 重新扫描文件列表
        String scriptPath = scriptFileService.getScriptPath(scriptId);
        Path scriptDir = Paths.get(scriptPath);
        List<Map<String, Object>> fileList = new ArrayList<>();
        
        if (Files.exists(scriptDir)) {
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
        }
        
        // 更新脚本
        script.setFileList(fileList);
        scriptMapper.updateById(script);
        
        // 更新当前版本
        if (script.getCurrentVersion() != null) {
            ScriptVersion version = scriptVersionMapper.selectOne(
                new LambdaQueryWrapper<ScriptVersion>()
                    .eq(ScriptVersion::getScriptId, scriptId)
                    .eq(ScriptVersion::getVersion, script.getCurrentVersion())
            );
            if (version != null) {
                version.setFileList(fileList);
                version.setFileCount(fileList.size());
                scriptVersionMapper.updateById(version);
            }
        }
    }

    // ==================== 临时文件操作（新建脚本时使用） ====================

    @Operation(summary = "读取临时文件内容")
    @GetMapping("/temp-files")
    public ApiResponse<Map<String, String>> getTempFileContent(
            @RequestParam String tempPath,
            @RequestParam String filePath) {
        try {
            // 安全检查：防止路径遍历攻击
            if (filePath.contains("..") || filePath.startsWith("/") || filePath.contains(":")) {
                return ApiResponse.error(400, "非法的文件路径");
            }
            
            Path fullPath = Paths.get(tempPath, filePath).normalize();
            
            // 检查文件是否存在且在临时目录内
            if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
                return ApiResponse.error(404, "文件不存在");
            }
            
            // 检查是否在临时目录范围内
            Path normalizedTempPath = Paths.get(tempPath).normalize();
            if (!fullPath.startsWith(normalizedTempPath)) {
                return ApiResponse.error(403, "无权访问此文件");
            }
            
            // 限制文件大小（最大 1MB）
            long fileSize = Files.size(fullPath);
            if (fileSize > 1024 * 1024) {
                return ApiResponse.error(400, "文件过大，最大支持 1MB");
            }
            
            String content = Files.readString(fullPath);
            return ApiResponse.success(Map.of("content", content));
        } catch (Exception e) {
            log.error("读取临时文件失败: {}", e.getMessage());
            return ApiResponse.error(500, "读取文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新临时文件内容")
    @PutMapping("/temp-files")
    public ApiResponse<Void> updateTempFileContent(
            @RequestParam String tempPath,
            @RequestParam String filePath,
            @RequestBody Map<String, String> body) {
        try {
            // 安全检查
            if (filePath.contains("..") || filePath.startsWith("/") || filePath.contains(":")) {
                return ApiResponse.error(400, "非法的文件路径");
            }
            
            Path fullPath = Paths.get(tempPath, filePath).normalize();
            Path normalizedTempPath = Paths.get(tempPath).normalize();
            
            if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
                return ApiResponse.error(404, "文件不存在");
            }
            
            if (!fullPath.startsWith(normalizedTempPath)) {
                return ApiResponse.error(403, "无权访问此文件");
            }
            
            String content = body.get("content");
            if (content == null) {
                return ApiResponse.error(400, "内容不能为空");
            }
            
            Files.writeString(fullPath, content);
            log.info("更新临时文件: {}", fullPath);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("更新临时文件失败: {}", e.getMessage());
            return ApiResponse.error(500, "更新文件失败: " + e.getMessage());
        }
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
    
    // ==================== 辅助方法 ====================
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) return "";
        // 处理 .tar.gz
        if (filename.toLowerCase().endsWith(".tar.gz")) {
            return "tar.gz";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }
}
