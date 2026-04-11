package com.autotest.service;

import com.autotest.entity.Server;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.TaskServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Shell 执行器（用于 Linux/Unix 平台本地执行）
 */
@Slf4j
@Component("shellExecutor")
public class ShellExecutor implements LocalExecutor {

    @Value("${autotest.storage.scripts-path:C:/data/auto-test/scripts}")
    private String scriptsPath;

    @Value("${autotest.local-executor.timeout:300}")
    private int timeout;

    @Value("${autotest.local-executor.shell-path:bash}")
    private String shellPath;

    @Override
    public ExecutionResult execute(
            ScriptVersion scriptVersion,
            Map<String, Object> params,
            TaskServer taskServer,
            Server server,
            Consumer<String> logConsumer
    ) throws Exception {
        // 调用新方法，scriptFile 为 null
        return execute(scriptVersion, null, params, taskServer, server, logConsumer);
    }
    
    @Override
    public ExecutionResult execute(
            ScriptVersion scriptVersion,
            String scriptFile,
            Map<String, Object> params,
            TaskServer taskServer,
            Server server,
            Consumer<String> logConsumer
    ) throws Exception {
        // 使用 scriptFile 参数
        String entryFile = scriptFile;
        
        // 如果没有指定，尝试从 fileList 中自动检测
        if (entryFile == null || entryFile.isEmpty()) {
            if (scriptVersion.getFileList() != null) {
                for (Object item : scriptVersion.getFileList()) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fileInfo = (Map<String, Object>) item;
                        String path = (String) fileInfo.get("path");
                        String name = (String) fileInfo.get("name");
                        String filePath = path != null ? path : name;
                        if (filePath != null && (filePath.equals("main.sh") || filePath.equals("main.py"))) {
                            entryFile = filePath;
                            break;
                        }
                    }
                }
            }
        }
        
        if (entryFile == null || entryFile.isEmpty()) {
            return ExecutionResult.builder()
                    .success(false)
                    .exitCode(-1)
                    .error("未指定要执行的脚本文件")
                    .build();
        }
        
        return executeInternal(scriptVersion, entryFile, params, logConsumer);
    }
    
    private ExecutionResult executeInternal(
            ScriptVersion scriptVersion,
            String entryFile,
            Map<String, Object> params,
            Consumer<String> logConsumer
    ) throws Exception {

        long startTime = System.currentTimeMillis();

        // 使用脚本版本中的存储路径
        String scriptDir = scriptVersion.getStoragePath();
        
        if (scriptDir == null || scriptDir.isEmpty()) {
            return ExecutionResult.builder()
                    .success(false)
                    .exitCode(-1)
                    .error("脚本存储路径未配置")
                    .build();
        }
        
        if (entryFile == null || entryFile.isEmpty()) {
            return ExecutionResult.builder()
                    .success(false)
                    .exitCode(-1)
                    .error("入口文件未配置")
                    .build();
        }
        
        String scriptPath = scriptDir + "/" + entryFile;

        log.info("ShellExecutor 执行脚本：{}", scriptPath);

        // 构建命令
        ProcessBuilder pb = new ProcessBuilder(
                shellPath,
                scriptPath
        );
        pb.directory(new File(scriptDir));

        // 设置环境变量（跳过空值参数）
        Map<String, String> env = pb.environment();
        if (params != null) {
            params.forEach((k, v) -> {
                if (v != null && !String.valueOf(v).trim().isEmpty()) {
                    env.put(k, String.valueOf(v));
                }
            });
        }

        // 目标服务器信息通过参数传递（用户配置的步骤参数）
        // 脚本包自行解析这些参数来连接目标服务器
        
        // 启动进程
        Process process = pb.start();

        // 异步读取输出
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        CompletableFuture<Void> outFuture = readStreamAsync(process.getInputStream(), logConsumer, output);
        CompletableFuture<Void> errFuture = readStreamAsync(process.getErrorStream(), logConsumer, error);

        // 等待完成
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

        long durationMs = System.currentTimeMillis() - startTime;

        if (!completed) {
            process.destroyForcibly();
            log.error("脚本执行超时：{}", scriptPath);
            return ExecutionResult.builder()
                    .success(false)
                    .exitCode(-1)
                    .output(output.toString())
                    .error("执行超时（" + timeout + "秒）")
                    .durationMs(durationMs)
                    .build();
        }

        // 等待输出读取完成
        try {
            outFuture.get(5, TimeUnit.SECONDS);
            errFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("等待输出完成失败：{}", e.getMessage());
        }

        int exitCode = process.exitValue();
        boolean success = exitCode == 0;

        log.info("ShellExecutor 执行完成，exitCode={}，duration={}ms", exitCode, durationMs);

        return ExecutionResult.builder()
                .success(success)
                .exitCode(exitCode)
                .output(output.toString())
                .error(error.toString())
                .durationMs(durationMs)
                .build();
    }

    @Override
    public String getType() {
        return "shell";
    }

    @Override
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(shellPath, "--version");
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            log.warn("Shell 执行器不可用：{}", e.getMessage());
            return false;
        }
    }

    private CompletableFuture<Void> readStreamAsync(
            InputStream stream,
            Consumer<String> logConsumer,
            StringBuilder output
    ) {
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (logConsumer != null) {
                        logConsumer.accept(line);
                    }
                }
            } catch (IOException e) {
                log.error("读取流失败：{}", e.getMessage());
            }
        });
    }
}