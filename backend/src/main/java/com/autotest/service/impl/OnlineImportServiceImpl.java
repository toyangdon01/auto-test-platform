package com.autotest.service.impl;

import com.autotest.config.ScriptConfig;
import com.autotest.dto.*;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.exception.BusinessException;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.service.OnlineImportService;
import com.autotest.service.ScriptPackageService;
import static com.autotest.service.ScriptPackageService.ConflictStrategy;
import com.autotest.util.ArchiveUrlBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 在线导入服务实现
 */
@Service
@Slf4j
public class OnlineImportServiceImpl implements OnlineImportService {

    @Autowired
    private ScriptPackageService packageService;
    
    @Autowired
    private ScriptMapper scriptMapper;
    
    @Autowired
    private ScriptVersionMapper scriptVersionMapper;
    
    @Value("${autotest.storage.scripts-path:C:/data/auto-test/scripts}")
    private String scriptsPath;
    
    private final YAMLMapper yamlMapper = new YAMLMapper();

    // 临时文件存储目录
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/autotest_online_import/";
    
    // 临时路径缓存（UUID -> 路径）
    private static final Map<String, TempPathInfo> TEMP_PATH_CACHE = new ConcurrentHashMap<>();
    
    // 临时文件过期时间（30 分钟）
    private static final long TEMP_PATH_EXPIRY_MS = 30 * 60 * 1000;

    /**
     * 从在线仓库预览脚本包
     */
    @Override
    public OnlinePreviewResponse previewFromOnline(OnlineImportRequest request) {
        log.info("预览在线仓库：url={}, branch={}, subDir={}", request.getUrl(), request.getBranch(), request.getSubDir());
        
        // 1. 解析 URL
        GitRepoInfo repoInfo = parseUrl(request.getUrl());
        if (repoInfo == null) {
            throw new BusinessException("无效的仓库地址，仅支持 Gitee/GitHub/GitLab");
        }
        
        // 2. 设置分支
        String branch = request.getBranch() != null && !request.getBranch().isEmpty() 
            ? request.getBranch() : repoInfo.getBranch();
        repoInfo.setBranch(branch);
        
        // 3. 设置子目录
        if (request.getSubDir() != null && !request.getSubDir().isEmpty()) {
            repoInfo.setSubDir(request.getSubDir());
        }
        
        // 4. 构建下载 URL
        String downloadUrl = ArchiveUrlBuilder.build(repoInfo, request.getAccessToken());
        log.info("下载 URL: {}", downloadUrl);
        
        // 5. 下载 ZIP 文件
        Path tempZip = downloadArchive(downloadUrl);
        
        // 6. 解压到临时目录
        Path tempDir = unzipArchive(tempZip);
        
        // 7. 如果有子目录，定位到子目录
        Path scriptDir = tempDir;
        if (repoInfo.getSubDir() != null && !repoInfo.getSubDir().isEmpty()) {
            scriptDir = tempDir.resolve(repoInfo.getSubDir());
            if (!Files.exists(scriptDir)) {
                throw new BusinessException("子目录不存在：" + repoInfo.getSubDir());
            }
        }
        
        // 8. 扫描脚本目录
        List<ScriptPreview> scripts = scanScripts(scriptDir);
        
        // 9. 构建响应
        OnlinePreviewResponse response = new OnlinePreviewResponse();
        response.setPlatform(repoInfo.getPlatform());
        response.setRepoName(repoInfo.getRepo());
        response.setBranch(branch);
        response.setScripts(scripts);
        response.setTotalScripts(scripts.size());
        
        // 10. 保存临时路径（用于后续导入）
        String tempPathId = UUID.randomUUID().toString();
        TempPathInfo tempInfo = new TempPathInfo(scriptDir.toString(), System.currentTimeMillis() + TEMP_PATH_EXPIRY_MS);
        TEMP_PATH_CACHE.put(tempPathId, tempInfo);
        response.setTempPath(tempPathId);
        
        log.info("预览完成：发现 {} 个脚本，临时路径 ID={}", scripts.size(), tempPathId);
        
        // 清理临时 ZIP 文件
        try {
            Files.deleteIfExists(tempZip);
        } catch (IOException e) {
            log.warn("删除临时 ZIP 失败：{}", e.getMessage());
        }
        
        return response;
    }

    /**
     * 从在线仓库导入脚本
     */
    @Override
    public ImportResult importFromOnline(OnlineImportRequest request) {
        log.info("导入在线仓库脚本：tempPath={}, selectedScripts={}, conflictStrategy={}", 
            request.getTempPath(), request.getSelectedScripts(), request.getConflictStrategy());
        
        // 1. 获取临时目录路径
        TempPathInfo tempInfo = TEMP_PATH_CACHE.get(request.getTempPath());
        if (tempInfo == null) {
            throw new BusinessException("临时文件已过期，请重新预览");
        }
        
        // 检查是否过期
        if (System.currentTimeMillis() > tempInfo.expiryTime) {
            TEMP_PATH_CACHE.remove(request.getTempPath());
            throw new BusinessException("临时文件已过期，请重新预览");
        }
        
        Path scriptDir = Paths.get(tempInfo.path);
        if (!Files.exists(scriptDir)) {
            TEMP_PATH_CACHE.remove(request.getTempPath());
            throw new BusinessException("临时文件已丢失，请重新预览");
        }
        
        try {
            // 2. 执行导入（复用 ScriptPackageServiceImpl 的导入逻辑）
            ImportResult result = importFromPath(scriptDir, request.getSelectedScripts(), 
                ConflictStrategy.valueOf(request.getConflictStrategy()));
            
            log.info("导入完成：成功={}, 跳过={}, 失败={}", 
                result.getImported(), result.getSkipped(), result.getFailed());
            
            return result;
        } finally {
            // 3. 清理临时文件和缓存
            cleanupTempPath(request.getTempPath());
        }
    }

    /**
     * 解析 Git 仓库 URL
     */
    private GitRepoInfo parseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        GitPlatform platform = GitPlatform.fromUrl(url);
        if (platform == null) {
            return null;
        }
        
        GitRepoInfo info = new GitRepoInfo();
        info.setPlatform(platform);
        info.setUrl(url);
        
        // 移除 .git 后缀
        String cleanUrl = url.replaceAll("\\.git$", "");
        
        // 正则匹配：https://domain.com/owner/repo 或 https://domain.com/owner/repo/tree/branch
        Pattern pattern = Pattern.compile(
            "https?://" + platform.getDomain() + "/([^/]+)/([^/]+)(?:/tree/([^/]+)(?:/(.*))?)?"
        );
        Matcher matcher = pattern.matcher(cleanUrl);
        
        if (!matcher.matches()) {
            return null;
        }
        
        info.setOwner(matcher.group(1));
        info.setRepo(matcher.group(2));
        
        // 分支
        String branch = matcher.group(3);
        if (branch != null && !branch.isEmpty()) {
            info.setBranch(branch);
        }
        
        // 子目录
        String subDir = matcher.group(4);
        if (subDir != null && !subDir.isEmpty()) {
            info.setSubDir(subDir);
        }
        
        return info;
    }

    /**
     * 下载 ZIP 文件
     */
    private Path downloadArchive(String downloadUrl) {
        try {
            // 创建临时目录
            Path tempDir = Paths.get(TEMP_DIR);
            Files.createDirectories(tempDir);
            
            // 生成临时文件名
            Path tempZip = tempDir.resolve("repo_" + System.currentTimeMillis() + ".zip");
            
            // 下载文件
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (AutoTest Platform)");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new BusinessException("下载失败：HTTP " + responseCode);
            }
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(tempZip)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            log.info("下载完成：{} bytes", Files.size(tempZip));
            return tempZip;
            
        } catch (IOException e) {
            log.error("下载失败：{}", e.getMessage());
            throw new BusinessException("下载失败：" + e.getMessage());
        }
    }

    /**
     * 解压 ZIP 文件
     */
    private Path unzipArchive(Path zipPath) {
        try {
            // 创建解压目录
            Path extractDir = Paths.get(TEMP_DIR, "extract_" + System.currentTimeMillis());
            Files.createDirectories(extractDir);
            
            // 解压 ZIP
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    Files.newInputStream(zipPath))) {
                
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = extractDir.resolve(entry.getName());
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        // 确保父目录存在
                        Files.createDirectories(entryPath.getParent());
                        // 复制内容
                        Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            
            log.info("解压完成：{}", extractDir);
            return extractDir;
            
        } catch (IOException e) {
            log.error("解压失败：{}", e.getMessage());
            throw new BusinessException("解压失败：" + e.getMessage());
        }
    }

    /**
     * 扫描脚本目录
     */
    private List<ScriptPreview> scanScripts(Path dir) {
        List<ScriptPreview> scripts = new ArrayList<>();
        
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isDirectory)
                .forEach(scriptDir -> {
                    // 检查是否有 autotest.yaml 或 main.sh
                    Path yamlPath = scriptDir.resolve("autotest.yaml");
                    Path mainPath = scriptDir.resolve("main.sh");
                    Path mainPyPath = scriptDir.resolve("main.py");
                    
                    if (Files.exists(yamlPath) || Files.exists(mainPath) || Files.exists(mainPyPath)) {
                        ScriptPreview preview = new ScriptPreview();
                        preview.setName(scriptDir.getFileName().toString());
                        preview.setHasYaml(Files.exists(yamlPath));
                        
                        // 统计文件数
                        try (Stream<Path> fileStream = Files.walk(scriptDir)) {
                            List<String> files = fileStream
                                .filter(Files::isRegularFile)
                                .map(p -> scriptDir.relativize(p).toString())
                                .toList();
                            preview.setFileCount(files.size());
                            preview.setFiles(files);
                        } catch (IOException e) {
                            preview.setFileCount(0);
                            preview.setFiles(new ArrayList<>());
                        }
                        
                        scripts.add(preview);
                    }
                });
        } catch (IOException e) {
            log.error("扫描脚本失败：{}", e.getMessage());
        }
        
        return scripts;
    }

    /**
     * 清理临时路径
     */
    private void cleanupTempPath(String tempPathId) {
        TempPathInfo tempInfo = TEMP_PATH_CACHE.remove(tempPathId);
        if (tempInfo != null) {
            try {
                Path scriptDir = Paths.get(tempInfo.path);
                // 删除整个目录树
                try (Stream<Path> paths = Files.walk(scriptDir)) {
                    paths.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.warn("删除临时文件失败：{}", p);
                            }
                        });
                }
                log.info("清理临时文件完成：{}", tempPathId);
            } catch (IOException e) {
                log.warn("清理临时文件失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 从 Path 导入脚本（复用 ScriptPackageServiceImpl 的逻辑）
     */
    private ImportResult importFromPath(Path scriptDir, List<String> selectedScripts, 
                                        ConflictStrategy strategy) {
        ImportResult result = new ImportResult();
        
        // 扫描脚本目录
        List<String> scriptNames;
        try {
            scriptNames = selectedScripts != null ? selectedScripts : 
                Files.list(scriptDir)
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("autotest.yaml")) || 
                                Files.exists(p.resolve("main.sh")) ||
                                Files.exists(p.resolve("main.py")))
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            log.error("扫描脚本目录失败", e);
            result.addError("扫描失败：" + e.getMessage());
            return result;
        }
        
        result.setTotal(scriptNames.size());
        log.info("发现 {} 个脚本：{}", scriptNames.size(), scriptNames);
        
        // 逐个导入
        for (String scriptName : scriptNames) {
            try {
                importScriptFromPath(scriptDir, scriptName, strategy, result);
            } catch (Exception e) {
                result.addFailed(scriptName, e.getMessage());
                log.error("导入脚本失败：{}", scriptName, e);
            }
        }
        
        return result;
    }
    
    /**
     * 导入单个脚本从 Path
     */
    private void importScriptFromPath(Path baseDir, String scriptName, 
                                      ConflictStrategy strategy, ImportResult result) throws IOException {
        Path scriptDir = baseDir.resolve(scriptName);
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
        
        // 检查冲突
        Script existing = scriptMapper.selectOne(
            new LambdaQueryWrapper<Script>().eq(Script::getName, scriptName)
        );
        
        if (existing != null) {
            if (strategy == ConflictStrategy.SKIP) {
                result.addSkipped(scriptName, "脚本已存在");
                return;
            } else if (strategy == ConflictStrategy.OVERWRITE) {
                // TODO: 更新现有脚本
                result.addSkipped(scriptName, "覆盖功能待实现");
                return;
            }
            // RENAME: 创建新脚本，使用新名称
            scriptName = scriptName + "_v" + System.currentTimeMillis();
        }
        
        // 创建新脚本
        createScriptFromPath(scriptName, scriptDir, config, result);
    }
    
    /**
     * 创建新脚本从 Path
     */
    private void createScriptFromPath(String scriptName, Path scriptDir, ScriptConfig config, 
                                      ImportResult result) throws IOException {
        // 创建脚本记录
        Script script = new Script();
        script.setName(scriptName);
        script.setScriptType(config.getType() != null ? config.getType() : "shell");
        script.setTestCategory(config.getCategory() != null ? config.getCategory() : "general");
        script.setDescription(config.getDescription());
        script.setDefaultTimeout(config.getTimeout() != null ? config.getTimeout() : 3600);
        script.setStatus("active");
        
        scriptMapper.insert(script);
        log.info("创建脚本记录：{}", scriptName);
        
        // 创建脚本目录
        Path targetDir = Paths.get(scriptsPath, script.getId().toString());
        Files.createDirectories(targetDir);
        
        // 复制文件
        copyDirectory(scriptDir, targetDir);
        log.info("复制脚本文件：{} -> {}", scriptDir, targetDir);
        
        // 创建版本记录
        ScriptVersion version = new ScriptVersion();
        version.setScriptId(script.getId());
        version.setVersion("v1.0.0");
        version.setChangeLog("从在线仓库导入");
        
        // 计算文件列表和大小
        List<Map<String, Object>> fileList = new ArrayList<>();
        long[] totalSize = {0};
        
        Files.walkFileTree(scriptDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("path", scriptDir.relativize(file).toString());
                fileInfo.put("size", attrs.size());
                fileList.add(fileInfo);
                totalSize[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        
        version.setFileList(fileList);
        version.setFileCount(fileList.size());
        version.setTotalSize(totalSize[0]);
        
        scriptVersionMapper.insert(version);
        
        result.addImported(scriptName, script.getId());
        log.info("脚本导入成功：{} (id={})", scriptName, script.getId());
    }
    
    /**
     * 复制目录
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * 临时路径信息
     */
    private static class TempPathInfo {
        String path;
        long expiryTime;
        
        TempPathInfo(String path, long expiryTime) {
            this.path = path;
            this.expiryTime = expiryTime;
        }
    }
}