package com.autotest.service.impl;

import com.autotest.entity.Script;
import com.autotest.service.ScriptFileService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.zip.*;

/**
 * 脚本文件服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
public class ScriptFileServiceImpl implements ScriptFileService {

    @Value("${autotest.storage.scripts-path:/data/auto-test/scripts}")
    private String scriptsPath;

    @Value("${autotest.storage.temp-path:/data/auto-test/temp}")
    private String tempPath;

    @Override
    public Map<String, Object> uploadScriptFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 获取文件扩展名
        String extension = getFileExtension(originalFilename).toLowerCase();
        
        // 验证文件类型
        if (!isValidScriptFile(extension)) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 .sh, .py, .zip, .tar.gz 文件");
        }

        // 创建临时目录
        Path tempDir = Paths.get(tempPath, "upload_" + System.currentTimeMillis());
        Files.createDirectories(tempDir);

        // 保存临时文件
        String safeFileName = sanitizeFileName(originalFilename);
        Path tempFilePath = tempDir.resolve(safeFileName);
        file.transferTo(tempFilePath.toFile());

        Map<String, Object> result = new HashMap<>();
        result.put("originalName", originalFilename);
        result.put("fileName", safeFileName);
        result.put("tempPath", tempFilePath.toString());
        result.put("extension", extension);
        result.put("size", file.getSize());

        // 如果是压缩包，解压并获取文件列表
        if (isArchiveFile(extension)) {
            String scriptName = originalFilename.replaceAll("\\.(zip|tar\\.gz|tar\\.tgz)$", "");
            List<Map<String, Object>> fileList = extractArchive(tempFilePath.toString(), scriptName);
            result.put("fileList", fileList);
            result.put("isArchive", true);

            // 自动检测入口文件
            String entryFile = detectEntryFile(fileList);
            result.put("suggestedEntry", entryFile);
        } else {
            // 单文件脚本
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", safeFileName);
            fileInfo.put("path", safeFileName);
            fileInfo.put("size", file.getSize());
            fileInfo.put("type", extension);
            fileInfo.put("isEntry", true);
            result.put("fileList", Collections.singletonList(fileInfo));
            result.put("isArchive", false);
            result.put("suggestedEntry", safeFileName);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> extractArchive(String tempFilePath, String scriptName) throws IOException {
        List<Map<String, Object>> fileList = new ArrayList<>();
        Path archivePath = Paths.get(tempFilePath);
        String extension = getFileExtension(tempFilePath).toLowerCase();

        Path extractDir = archivePath.getParent().resolve("extracted");
        Files.createDirectories(extractDir);

        try {
            if ("zip".equals(extension)) {
                extractZip(archivePath, extractDir, fileList);
            } else if (extension.endsWith("gz") || "tgz".equals(extension)) {
                extractTarGz(archivePath, extractDir, fileList);
            }
        } catch (Exception e) {
            log.error("解压文件失败: {}", e.getMessage());
            throw new IOException("解压文件失败: " + e.getMessage());
        }

        return fileList;
    }

    private void extractZip(Path zipPath, Path targetDir, List<Map<String, Object>> fileList) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                Path targetPath = targetDir.resolve(entry.getName());
                Files.createDirectories(targetPath.getParent());
                Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);

                addFileInfo(fileList, entry.getName(), targetPath);
            }
        }
    }

    private void extractTarGz(Path tarGzPath, Path targetDir, List<Map<String, Object>> fileList) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(tarGzPath));
             TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                Path targetPath = targetDir.resolve(entry.getName());
                Files.createDirectories(targetPath.getParent());
                Files.copy(tis, targetPath, StandardCopyOption.REPLACE_EXISTING);

                addFileInfo(fileList, entry.getName(), targetPath);
            }
        }
    }

    private void addFileInfo(List<Map<String, Object>> fileList, String name, Path path) throws IOException {
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("name", Paths.get(name).getFileName().toString());
        fileInfo.put("path", name);
        fileInfo.put("size", Files.size(path));
        fileInfo.put("type", getFileExtension(name));
        fileInfo.put("isEntry", isLikelyEntryFile(name));
        fileList.add(fileInfo);
    }

    @Override
    public void saveScriptFiles(Long scriptId, List<Map<String, Object>> files, String entryFile) throws IOException {
        Path scriptDir = Paths.get(scriptsPath, scriptId.toString());
        Files.createDirectories(scriptDir);

        for (Map<String, Object> file : files) {
            String sourcePath = (String) file.get("tempPath");
            String relativePath = (String) file.get("path");
            
            if (sourcePath == null || relativePath == null) {
                continue;
            }

            Path targetPath = scriptDir.resolve(relativePath);
            Files.createDirectories(targetPath.getParent());
            Files.copy(Paths.get(sourcePath), targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 设置入口文件执行权限
        if (entryFile != null) {
            Path entryPath = scriptDir.resolve(entryFile);
            if (Files.exists(entryPath)) {
                try {
                    Files.setPosixFilePermissions(entryPath, 
                            Set.of(PosixFilePermission.OWNER_READ,
                                   PosixFilePermission.OWNER_WRITE,
                                   PosixFilePermission.OWNER_EXECUTE));
                } catch (UnsupportedOperationException e) {
                    // Windows 系统不支持 POSIX 权限
                    log.debug("Windows 系统不支持设置 POSIX 权限");
                }
            }
        }
    }

    @Override
    public String readScriptFile(Long scriptId, String filePath) throws IOException {
        Path scriptPath = Paths.get(scriptsPath, scriptId.toString(), filePath);
        if (!Files.exists(scriptPath)) {
            throw new FileNotFoundException("文件不存在: " + filePath);
        }
        return Files.readString(scriptPath);
    }

    @Override
    public void updateScriptFile(Long scriptId, String filePath, String content) throws IOException {
        Path scriptPath = Paths.get(scriptsPath, scriptId.toString(), filePath);
        if (!Files.exists(scriptPath)) {
            throw new FileNotFoundException("文件不存在: " + filePath);
        }
        Files.writeString(scriptPath, content);
    }

    @Override
    public void deleteScriptFiles(Long scriptId) throws IOException {
        Path scriptDir = Paths.get(scriptsPath, scriptId.toString());
        if (Files.exists(scriptDir)) {
            deleteDirectory(scriptDir);
        }
    }

    @Override
    public String exportScript(Script script, String format) throws IOException {
        Long scriptId = script.getId();
        Path scriptDir = Paths.get(scriptsPath, scriptId.toString());
        
        if (!Files.exists(scriptDir)) {
            throw new FileNotFoundException("脚本目录不存在");
        }

        String exportFileName = script.getName() + "." + format;
        Path exportPath = Paths.get(tempPath, "export_" + System.currentTimeMillis(), exportFileName);
        Files.createDirectories(exportPath.getParent());

        if ("zip".equals(format)) {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(exportPath))) {
                Files.walk(scriptDir)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            try {
                                String relativePath = scriptDir.relativize(path).toString().replace("\\", "/");
                                zos.putNextEntry(new ZipEntry(relativePath));
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                log.error("打包文件失败: {}", e.getMessage());
                            }
                        });
            }
        } else if ("tar.gz".equals(format)) {
            try (OutputStream fos = Files.newOutputStream(exportPath);
                 GZIPOutputStream gos = new GZIPOutputStream(fos);
                 TarArchiveOutputStream tos = new TarArchiveOutputStream(gos)) {
                
                Files.walk(scriptDir)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            try {
                                String relativePath = scriptDir.relativize(path).toString().replace("\\", "/");
                                TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), relativePath);
                                tos.putArchiveEntry(entry);
                                Files.copy(path, tos);
                                tos.closeArchiveEntry();
                            } catch (IOException e) {
                                log.error("打包文件失败: {}", e.getMessage());
                            }
                        });
            }
        }

        return exportPath.toString();
    }

    @Override
    public String getScriptPath(Long scriptId) {
        return Paths.get(scriptsPath, scriptId.toString()).toString();
    }

    @Override
    public List<String> listScriptFiles(Long scriptId) throws IOException {
        Path scriptDir = Paths.get(scriptsPath, scriptId.toString());
        if (!Files.exists(scriptDir)) {
            return List.of();
        }

        List<String> files = new ArrayList<>();
        Files.walk(scriptDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    String relativePath = scriptDir.relativize(path).toString().replace("\\", "/");
                    files.add(relativePath);
                });

        return files;
    }

    // ==================== 辅助方法 ====================

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        
        // 处理 .tar.gz
        if (filename.toLowerCase().endsWith(".tar.gz")) {
            return "tar.gz";
        }
        
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    private boolean isValidScriptFile(String extension) {
        return extension.equals("sh") || extension.equals("py") ||
               extension.equals("zip") || extension.equals("tar.gz") ||
               extension.equals("tgz");
    }

    private boolean isArchiveFile(String extension) {
        return extension.equals("zip") || extension.equals("tar.gz") || extension.equals("tgz");
    }

    private String sanitizeFileName(String filename) {
        // 移除路径分隔符，防止路径遍历攻击
        return filename.replaceAll("[/\\\\]", "_");
    }

    private boolean isLikelyEntryFile(String path) {
        String name = Paths.get(path).getFileName().toString().toLowerCase();
        return name.equals("main.sh") || name.equals("main.py") ||
               name.equals("run.sh") || name.equals("run.py") ||
               name.equals("start.sh") || name.equals("start.py") ||
               name.equals("index.sh") || name.equals("index.py") ||
               name.startsWith("test_") || path.contains("/main.") || path.contains("/run.");
    }

    private String detectEntryFile(List<Map<String, Object>> fileList) {
        // 优先查找明确命名的入口文件
        String[] priorityNames = {"main.sh", "main.py", "run.sh", "run.py", "start.sh", "start.py"};
        
        for (String name : priorityNames) {
            for (Map<String, Object> file : fileList) {
                if (name.equalsIgnoreCase((String) file.get("name"))) {
                    return (String) file.get("path");
                }
            }
        }

        // 查找根目录的脚本文件
        for (Map<String, Object> file : fileList) {
            String path = (String) file.get("path");
            if (!path.contains("/") && !path.contains("\\")) {
                String type = (String) file.get("type");
                if ("sh".equals(type) || "py".equals(type)) {
                    return path;
                }
            }
        }

        // 返回第一个脚本文件
        for (Map<String, Object> file : fileList) {
            String type = (String) file.get("type");
            if ("sh".equals(type) || "py".equals(type)) {
                return (String) file.get("path");
            }
        }

        return null;
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", p);
                        }
                    });
        }
    }
}
