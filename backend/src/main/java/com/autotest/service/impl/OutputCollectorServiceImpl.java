package com.autotest.service.impl;

import com.autotest.dto.OutputCollectConfig;
import com.autotest.dto.OutputCollectResult;
import com.autotest.entity.Server;
import com.autotest.service.OutputCollectorService;
import com.autotest.service.SshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 输出收集服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputCollectorServiceImpl implements OutputCollectorService {

    private final ObjectMapper objectMapper;

    @Value("${autotest.storage.results-path}")
    private String resultsPath;

    @Override
    public OutputCollectResult collectOutputs(Server server, String workDir, OutputCollectConfig config, Long taskId, Long serverId) {
        OutputCollectResult result = new OutputCollectResult();

        if (config == null || !Boolean.TRUE.equals(config.getCollectEnabled())) {
            return result;
        }

        if (config.getCollectRules() == null || config.getCollectRules().isEmpty()) {
            return result;
        }

        String localDir = String.format("%s/task_%d/server_%d/collected", resultsPath, taskId, serverId);
        try {
            Files.createDirectories(Paths.get(localDir));
        } catch (IOException e) {
            log.error("创建本地存储目录失败: {}", localDir, e);
            result.addError("创建存储目录失败: " + e.getMessage());
            return result;
        }

        for (OutputCollectConfig.CollectRule rule : config.getCollectRules()) {
            try {
                collectByRule(server, workDir, rule, localDir, result, taskId);
            } catch (Exception e) {
                log.error("收集规则 [{}] 执行失败", rule.getName(), e);
                OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
                file.setName(rule.getName());
                file.setOriginalPath(rule.getPath());
                file.setCollectStatus("error");
                file.setErrorMessage(e.getMessage());
                result.addFile(file);

                if (Boolean.TRUE.equals(rule.getRequired())) {
                    result.addError(String.format("必需文件收集失败 [%s]: %s", rule.getName(), e.getMessage()));
                }
            }
        }

        return result;
    }

    private void collectByRule(Server server, String workDir, OutputCollectConfig.CollectRule rule, 
                               String localDir, OutputCollectResult result, Long taskId) {
        String remotePath = rule.getPath();

        if (!remotePath.startsWith("/")) {
            remotePath = workDir + "/" + remotePath;
        }

        String type = rule.getType() != null ? rule.getType() : "file";

        if ("directory".equals(type)) {
            collectDirectory(server, remotePath, rule, localDir, result, taskId);
        } else if ("pattern".equals(type)) {
            collectByPattern(server, remotePath, rule, localDir, result, taskId);
        } else {
            collectFile(server, remotePath, rule, localDir, result, taskId);
        }
    }

    private void collectFile(Server server, String remotePath, OutputCollectConfig.CollectRule rule, 
                             String localDir, OutputCollectResult result, Long taskId) {
        String checkCmd = String.format("test -f '%s' && echo exists || echo not_found", remotePath);
        SshService.ExecuteResult checkResult = SshService.executeCommandWithResult(server, checkCmd);
        
        if (!checkResult.getOutput().trim().equals("exists")) {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setCollectStatus("not_found");
            result.addFile(file);

            if (Boolean.TRUE.equals(rule.getRequired())) {
                result.addError(String.format("必需文件不存在: %s", remotePath));
            }
            return;
        }

        String sizeCmd = String.format("stat -c %%s '%s' 2>/dev/null || echo 0", remotePath);
        SshService.ExecuteResult sizeResult = SshService.executeCommandWithResult(server, sizeCmd);
        long remoteSize = Long.parseLong(sizeResult.getOutput().trim());

        long maxSizeBytes = parseSize(rule.getMaxSize());
        if (maxSizeBytes > 0 && remoteSize > maxSizeBytes) {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setCollectStatus("error");
            file.setErrorMessage(String.format("文件大小超限: %d > %d bytes", remoteSize, maxSizeBytes));
            result.addFile(file);
            return;
        }

        String fileName = Paths.get(remotePath).getFileName().toString();
        String localPath = localDir + "/" + fileName;

        boolean success = SshService.downloadFile(server, remotePath, localPath);
        
        if (success) {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setStoragePath(String.format("task_%d/server_%d/collected/%s", taskId, server.getId(), fileName));
            file.setSize(remoteSize);
            file.setCollectStatus("success");
            result.addFile(file);
            log.info("收集文件成功: {} -> {}", remotePath, localPath);
        } else {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setCollectStatus("error");
            file.setErrorMessage("下载失败");
            result.addFile(file);
        }
    }

    private void collectDirectory(Server server, String remotePath, OutputCollectConfig.CollectRule rule, 
                                  String localDir, OutputCollectResult result, Long taskId) {
        String checkCmd = String.format("test -d '%s' && echo exists || echo not_found", remotePath);
        SshService.ExecuteResult checkResult = SshService.executeCommandWithResult(server, checkCmd);

        if (!checkResult.getOutput().trim().equals("exists")) {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setCollectStatus("not_found");
            result.addFile(file);

            if (Boolean.TRUE.equals(rule.getRequired())) {
                result.addError(String.format("必需目录不存在: %s", remotePath));
            }
            return;
        }

        String dirName = Paths.get(remotePath).getFileName().toString();
        String tarFile = String.format("/tmp/output_collect_%d_%s.tar.gz", System.currentTimeMillis(), dirName);
        // 正确的打包命令：进入目录后打包当前目录内容
        String tarCmd = String.format("cd '%s' && tar -czf '%s' .", remotePath, tarFile);
        SshService.executeCommandWithResult(server, tarCmd);

        String sizeCmd = String.format("stat -c %%s '%s' 2>/dev/null || echo 0", tarFile);
        SshService.ExecuteResult sizeResult = SshService.executeCommandWithResult(server, sizeCmd);
        long tarSize = Long.parseLong(sizeResult.getOutput().trim());

        long maxSizeBytes = parseSize(rule.getMaxSize());
        if (maxSizeBytes > 0 && tarSize > maxSizeBytes) {
            SshService.executeCommand(server, "rm -f " + tarFile);
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setCollectStatus("error");
            file.setErrorMessage(String.format("目录打包后大小超限: %d > %d bytes", tarSize, maxSizeBytes));
            result.addFile(file);
            return;
        }

        String localPath = localDir + "/" + dirName + ".tar.gz";
        boolean success = SshService.downloadFile(server, tarFile, localPath);
        
        if (success) {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setStoragePath(String.format("task_%d/server_%d/collected/%s.tar.gz", taskId, server.getId(), dirName));
            file.setSize(tarSize);
            file.setCollectStatus("success");
            result.addFile(file);
            log.info("收集目录成功: {} -> {}", remotePath, localPath);
        } else {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(remotePath);
            file.setCollectStatus("error");
            file.setErrorMessage("下载失败");
            result.addFile(file);
        }
        
        SshService.executeCommand(server, "rm -f " + tarFile);
    }

    private void collectByPattern(Server server, String pattern, OutputCollectConfig.CollectRule rule, 
                                  String localDir, OutputCollectResult result, Long taskId) {
        String findCmd = String.format("find %s -type f 2>/dev/null | head -100", pattern);
        SshService.ExecuteResult findResult = SshService.executeCommandWithResult(server, findCmd);

        if (findResult.getOutput().trim().isEmpty()) {
            OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
            file.setName(rule.getName());
            file.setOriginalPath(pattern);
            file.setCollectStatus("not_found");
            result.addFile(file);

            if (Boolean.TRUE.equals(rule.getRequired())) {
                result.addError(String.format("未找到匹配文件: %s", pattern));
            }
            return;
        }

        String[] files = findResult.getOutput().trim().split("\n");
        for (String filePath : files) {
            if (filePath.trim().isEmpty()) continue;

            String fileName = Paths.get(filePath.trim()).getFileName().toString();
            String localPath = localDir + "/" + fileName;

            boolean success = SshService.downloadFile(server, filePath.trim(), localPath);

            if (success) {
                String sizeCmd = String.format("stat -c %%s '%s' 2>/dev/null || echo 0", filePath.trim());
                SshService.ExecuteResult sizeResult = SshService.executeCommandWithResult(server, sizeCmd);
                long fileSize = Long.parseLong(sizeResult.getOutput().trim());

                OutputCollectResult.CollectedFile file = new OutputCollectResult.CollectedFile();
                file.setName(rule.getName() + " - " + fileName);
                file.setOriginalPath(filePath.trim());
                file.setStoragePath(String.format("task_%d/server_%d/collected/%s", taskId, server.getId(), fileName));
                file.setSize(fileSize);
                file.setCollectStatus("success");
                result.addFile(file);
                log.info("收集文件成功(通配符): {} -> {}", filePath.trim(), localPath);
            }
        }
    }

    private long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return 0;
        }

        sizeStr = sizeStr.trim().toUpperCase();
        try {
            if (sizeStr.endsWith("GB")) {
                return Long.parseLong(sizeStr.replace("GB", "")) * 1024L * 1024L * 1024L;
            } else if (sizeStr.endsWith("MB")) {
                return Long.parseLong(sizeStr.replace("MB", "")) * 1024L * 1024L;
            } else if (sizeStr.endsWith("KB")) {
                return Long.parseLong(sizeStr.replace("KB", "")) * 1024L;
            } else {
                return Long.parseLong(sizeStr.replaceAll("[^0-9]", ""));
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public OutputCollectConfig parseConfig(Object outputConfigMap) {
        if (outputConfigMap == null) {
            return null;
        }

        try {
            if (outputConfigMap instanceof Map) {
                return objectMapper.convertValue(outputConfigMap, OutputCollectConfig.class);
            } else if (outputConfigMap instanceof OutputCollectConfig) {
                return (OutputCollectConfig) outputConfigMap;
            }
        } catch (Exception e) {
            log.error("解析输出配置失败", e);
        }

        return null;
    }
}
