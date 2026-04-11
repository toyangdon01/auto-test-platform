package com.autotest.service.impl;

import com.autotest.config.ScriptConfig;
import com.autotest.dto.ExportOptions;
import com.autotest.dto.ImportResult;
import com.autotest.dto.PackageManifest;
import com.autotest.dto.ScriptImportStatus;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.service.ScriptConfigService;
import com.autotest.service.ScriptFileService;
import com.autotest.service.ScriptPackageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 脚本包服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
public class ScriptPackageServiceImpl implements ScriptPackageService {
    
    @Value("${autotest.storage.scripts-path:C:/data/auto-test/scripts}")
    private String scriptsPath;
    
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ScriptConfigService scriptConfigService;
    private final ScriptFileService scriptFileService;
    
    public ScriptPackageServiceImpl(ScriptMapper scriptMapper, ScriptVersionMapper scriptVersionMapper,
                                    ScriptConfigService scriptConfigService, ScriptFileService scriptFileService) {
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.scriptConfigService = scriptConfigService;
        this.scriptFileService = scriptFileService;
    }
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();
    
    @Override
    public String exportScripts(ExportOptions options) throws IOException {
        Path tempDir = Files.createTempDirectory("scripts-export-");
        
        try {
            List<String> scriptNames = new ArrayList<>();
            
            // 1. 复制每个脚本
            for (Long scriptId : options.getScriptIds()) {
                Script script = scriptMapper.selectById(scriptId);
                if (script == null) {
                    log.warn("脚本不存在：scriptId={}", scriptId);
                    continue;
                }
                
                Path sourceDir = Paths.get(scriptsPath, scriptId.toString());
                if (!Files.exists(sourceDir)) {
                    log.warn("脚本目录不存在：scriptId={}", scriptId);
                    continue;
                }
                
                Path targetDir = tempDir.resolve("scripts").resolve(script.getName());
                Files.createDirectories(targetDir);
                
                copyDirectory(sourceDir, targetDir);
                scriptNames.add(script.getName());
                log.info("导出脚本：{}", script.getName());
            }
            
            // 2. 写入 manifest.json
            writeManifest(tempDir, scriptNames);
            
            // 3. 打包为 ZIP
            String zipPath = tempDir + ".zip";
            zipDirectory(tempDir, zipPath);
            
            log.info("导出完成：scriptCount={}, zipPath={}", scriptNames.size(), zipPath);
            return zipPath;
            
        } finally {
            deleteDirectory(tempDir);
        }
    }
    
    @Override
    public ImportResult importPackage(MultipartFile file, ConflictStrategy strategy) throws IOException {
        log.info("开始导入脚本包：fileName={}, strategy={}", file.getOriginalFilename(), strategy);
        
        ImportResult result = new ImportResult();
        Path tempDir = Files.createTempDirectory("scripts-import-");
        
        try {
            // 1. 解压
            unzip(file, tempDir);
            
            // 2. 检查 scripts 目录
            Path scriptsDir = tempDir.resolve("scripts");
            if (!Files.exists(scriptsDir)) {
                result.addError("缺少 scripts/ 目录");
                log.error("导入失败：缺少 scripts/ 目录");
                return result;
            }
            
            // 3. 解析 manifest（可选）
            List<String> scriptNames = parseManifestOrDiscover(tempDir, result);
            if (scriptNames.isEmpty()) {
                return result;
            }
            
            result.setTotal(scriptNames.size());
            log.info("发现 {} 个脚本：{}", scriptNames.size(), scriptNames);
            
            // 4. 逐个导入
            for (String scriptName : scriptNames) {
                try {
                    importScript(tempDir, scriptName, strategy, result);
                } catch (Exception e) {
                    result.addFailed(scriptName, e.getMessage());
                    log.error("导入脚本失败：{}", scriptName, e);
                }
            }
            
            log.info("导入完成：total={}, imported={}, skipped={}, failed={}", 
                     result.getTotal(), result.getImported(), result.getSkipped(), result.getFailed());
            
            if (result.hasWarnings()) {
                log.warn("导入警告：{}", result.getWarnings());
            }
            
            return result;
            
        } catch (IOException e) {
            result.addError("解压失败：" + e.getMessage());
            log.error("解压脚本包失败", e);
            return result;
        } finally {
            deleteDirectory(tempDir);
        }
    }
    
    @Override
    public PackageManifest previewPackage(MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("scripts-preview-");
        
        try {
            unzip(file, tempDir);
            
            // 尝试解析 manifest.json
            Path manifestPath = tempDir.resolve("manifest.json");
            if (Files.exists(manifestPath)) {
                return objectMapper.readValue(manifestPath.toFile(), PackageManifest.class);
            }
            
            // 没有 manifest，从目录结构发现
            PackageManifest manifest = new PackageManifest();
            manifest.setFormat("autotest-scripts-package/v1");
            manifest.setExportedAt(LocalDate.now().toString());
            
            Path scriptsDir = tempDir.resolve("scripts");
            if (Files.exists(scriptsDir)) {
                List<String> scriptNames = Files.list(scriptsDir)
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
                manifest.setScripts(scriptNames);
            }
            
            return manifest;
            
        } finally {
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * 解析 manifest 或从目录发现脚本
     */
    private List<String> parseManifestOrDiscover(Path tempDir, ImportResult result) throws IOException {
        Path manifestPath = tempDir.resolve("manifest.json");
        
        if (Files.exists(manifestPath)) {
            try {
                PackageManifest manifest = objectMapper.readValue(manifestPath.toFile(), PackageManifest.class);
                if (manifest.isValid() && manifest.getScripts() != null) {
                    log.info("解析 manifest.json: {} 个脚本", manifest.getScripts().size());
                    return manifest.getScripts();
                }
            } catch (Exception e) {
                log.warn("manifest.json 解析失败，使用目录结构发现：{}", e.getMessage());
            }
        }
        
        // 从目录结构发现
        Path scriptsDir = tempDir.resolve("scripts");
        return Files.list(scriptsDir)
            .filter(Files::isDirectory)
            .map(Path::getFileName)
            .map(Path::toString)
            .collect(Collectors.toList());
    }
    
    /**
     * 导入单个脚本
     */
    private void importScript(Path tempDir, String scriptName, ConflictStrategy strategy, ImportResult result) throws IOException {
        Path scriptDir = tempDir.resolve("scripts").resolve(scriptName);
        Path configPath = scriptDir.resolve("autotest.yaml");
        
        // 检查 autotest.yaml
        if (!Files.exists(configPath)) {
            throw new IOException("缺少 autotest.yaml");
        }
        
        // 解析配置
        ScriptConfig config;
        try {
            config = yamlMapper.readValue(configPath.toFile(), ScriptConfig.class);
        } catch (Exception e) {
            throw new IOException("autotest.yaml 解析失败：" + e.getMessage());
        }
        
        // 验证必要字段
        if (config.getName() == null || config.getName().isEmpty()) {
            result.addWarning(scriptName, "配置缺少 name 字段，使用目录名");
        }
        
        // 检查冲突
        Script existing = scriptMapper.selectOne(
            new LambdaQueryWrapper<Script>().eq(Script::getName, scriptName)
        );
        
        if (existing != null) {
            if (strategy == ConflictStrategy.SKIP) {
                result.addSkipped(scriptName, "脚本已存在");
                return;
            } else if (strategy == ConflictStrategy.OVERWRITE) {
                // 更新现有脚本
                updateScript(existing.getId(), scriptDir, config, scriptName, result);
                return;
            }
            // RENAME: 继续创建新脚本，使用新名称
        }
        
        // 创建新脚本
        createScript(scriptName, scriptDir, config, result);
    }
    
    /**
     * 创建新脚本
     */
    private void createScript(String scriptName, Path scriptDir, ScriptConfig config, ImportResult result) throws IOException {
        // 创建脚本记录
        Script script = new Script();
        script.setName(scriptName);
        script.setScriptType(config.getType() != null ? config.getType() : "shell");
        script.setTestCategory(config.getCategory() != null ? config.getCategory() : "general");
        script.setDescription(config.getDescription());
        script.setDefaultTimeout(config.getTimeout() != null ? config.getTimeout() : 3600);
        script.setCurrentVersion("v1.0.0");
        script.setStatus("enabled");
        
        scriptMapper.insert(script);
        log.info("创建脚本：{}", script.getName());
        
        // 复制文件到存储目录
        Path targetDir = Paths.get(scriptsPath, script.getId().toString());
        Files.createDirectories(targetDir);
        copyDirectory(scriptDir, targetDir);
        
        // 创建版本记录
        ScriptVersion version = new ScriptVersion();
        version.setScriptId(script.getId());
        version.setVersion("v1.0.0");
        version.setStoragePath(scriptFileService.getScriptPath(script.getId()));
        
        // 扫描文件列表
        List<Map<String, Object>> fileList = scanDirectory(targetDir);
        version.setFileList(fileList);
        version.setFileCount(fileList.size());
        
        // 应用配置
        if (config.getSteps() != null) {
            version.setSteps(config.getSteps());
        }
        if (config.getParameters() != null) {
            version.setParameters(convertParameters(config.getParameters()));
        }
        
        scriptVersionMapper.insert(version);
        
        // 同步配置文件
        scriptConfigService.saveConfig(script.getId(), config);
        
        result.addImported(scriptName, script.getId());
        log.info("脚本导入成功：scriptId={}, name={}", script.getId(), script.getName());
    }
    
    /**
     * 更新现有脚本
     */
    private void updateScript(Long scriptId, Path scriptDir, ScriptConfig config, String scriptName, ImportResult result) throws IOException {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new IOException("脚本不存在");
        }
        
        // 更新基本信息
        if (config.getType() != null) {
            script.setScriptType(config.getType());
        }
        if (config.getCategory() != null) {
            script.setTestCategory(config.getCategory());
        }
        if (config.getDescription() != null) {
            script.setDescription(config.getDescription());
        }
        if (config.getTimeout() != null) {
            script.setDefaultTimeout(config.getTimeout());
        }
        
        scriptMapper.updateById(script);
        
        // 覆盖文件
        Path targetDir = Paths.get(scriptsPath, scriptId.toString());
        deleteDirectory(targetDir);
        Files.createDirectories(targetDir);
        copyDirectory(scriptDir, targetDir);
        
        // 更新版本
        ScriptVersion version = scriptVersionMapper.selectOne(
            new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getVersion, script.getCurrentVersion())
        );
        
        if (version != null) {
            List<Map<String, Object>> fileList = scanDirectory(targetDir);
            version.setFileList(fileList);
            version.setFileCount(fileList.size());
            
            if (config.getSteps() != null) {
                version.setSteps(config.getSteps());
            }
            if (config.getParameters() != null) {
                version.setParameters(convertParameters(config.getParameters()));
            }
            
            scriptVersionMapper.updateById(version);
        }
        
        // 同步配置文件
        scriptConfigService.saveConfig(scriptId, config);
        
        result.addImported(scriptName, scriptId, "已更新");
        log.info("脚本更新成功：scriptId={}, name={}", scriptId, script.getName());
    }
    
    /**
     * 转换参数格式
     */
    private List<Map<String, Object>> convertParameters(List<ScriptConfig.ParameterConfig> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScriptConfig.ParameterConfig pc : params) {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", pc.getName());
            param.put("type", pc.getType() != null ? pc.getType() : "string");
            param.put("default", pc.getDefaultValue());
            param.put("description", pc.getDescription());
            result.add(param);
        }
        return result;
    }
    
    /**
     * 扫描目录生成文件列表
     */
    private List<Map<String, Object>> scanDirectory(Path dir) throws IOException {
        List<Map<String, Object>> fileList = new ArrayList<>();
        
        if (!Files.exists(dir)) {
            return fileList;
        }
        
        Files.walk(dir)
            .filter(path -> !Files.isDirectory(path))
            .forEach(path -> {
                try {
                    Path relativePath = dir.relativize(path);
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("path", relativePath.toString().replace("\\", "/"));
                    fileInfo.put("name", path.getFileName().toString());
                    fileInfo.put("size", Files.size(path));
                    fileList.add(fileInfo);
                } catch (IOException e) {
                    log.warn("读取文件信息失败：{}", path);
                }
            });
        
        return fileList;
    }
    
    /**
     * 写入 manifest.json
     */
    private void writeManifest(Path tempDir, List<String> scriptNames) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "autotest-scripts-package/v1");
        manifest.put("exportedAt", LocalDate.now().toString());
        manifest.put("scripts", scriptNames);
        
        Path manifestPath = tempDir.resolve("manifest.json");
        objectMapper.writeValue(manifestPath.toFile(), manifest);
        log.info("写入 manifest.json: {} 个脚本", scriptNames.size());
    }
    
    /**
     * 解压 ZIP 文件
     */
    private void unzip(MultipartFile file, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        log.info("解压完成：{}", targetDir);
    }
    
    /**
     * 打包为 ZIP
     */
    private void zipDirectory(Path sourceDir, String zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Paths.get(zipPath)))) {
            Files.walk(sourceDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        ZipEntry entry = new ZipEntry(sourceDir.relativize(path).toString().replace("\\", "/"));
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        log.error("打包文件失败：{}", path, e);
                    }
                });
        }
        log.info("打包完成：{}", zipPath);
    }
    
    /**
     * 复制目录
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
            .forEach(path -> {
                try {
                    Path targetPath = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error("复制文件失败：{} -> {}", path, target, e);
                }
            });
        log.info("复制目录：{} -> {}", source, target);
    }
    
    /**
     * 删除目录
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("删除文件失败：{}", path, e);
                    }
                });
        }
    }
}
