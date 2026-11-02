package com.autotest.service;

import com.autotest.entity.Script;
import com.autotest.entity.Server;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.TaskServer;
import com.autotest.mapper.ScriptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * PowerShell 执行器（用于 Windows 平台本地执行）
 */
@Slf4j
@Component("powershellExecutor")
public class PowerShellExecutor implements LocalExecutor {

    @Value("${autotest.storage.scripts-path:C:/data/auto-test/scripts}")
    private String scriptsPath;

    @Value("${autotest.local-executor.timeout:300}")
    private int timeout;

    @Autowired
    private ScriptMapper scriptMapper;

    @Value("${autotest.local-executor.powershell-path:powershell.exe}")
    private String powershellPath;

    @Override
    public ExecutionResult execute(
            ScriptVersion scriptVersion,
            Map<String, Object> params,
            TaskServer taskServer,
            Server server,
            long taskTimeoutMs,
            Consumer<String> logConsumer
    ) throws Exception {
        // 调用新方法，scriptFile 为 null
        return execute(scriptVersion, null, params, taskServer, server, taskTimeoutMs, logConsumer);
    }
    
    @Override
    public ExecutionResult execute(
            ScriptVersion scriptVersion,
            String scriptFile,
            Map<String, Object> params,
            TaskServer taskServer,
            Server server,
            long taskTimeoutMs,
            Consumer<String> logConsumer
    ) throws Exception {

        long startTime = System.currentTimeMillis();

        // 使用脚本版本中的存储路径
        String scriptDir = scriptVersion.getStoragePath();
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

        log.info("PowerShellExecutor 执行脚本：{}", scriptPath);

        // 根据文件类型选择执行方式
        ProcessBuilder pb;
        
        if (entryFile.endsWith(".py")) {
            // Python 脚本
            pb = new ProcessBuilder(
                    "python",
                    scriptPath
            );
        } else if (entryFile.endsWith(".ps1")) {
            // PowerShell 脚本
            pb = new ProcessBuilder(
                    powershellPath,
                    "-ExecutionPolicy", "Bypass",
                    "-File", scriptPath
            );
        } else if (entryFile.endsWith(".sh")) {
            // Shell 脚本 - 在 Windows 上使用 Git Bash 或 WSL
            pb = new ProcessBuilder(
                    "bash",
                    scriptPath
            );
        } else {
            // 默认尝试用 Python 执行
            pb = new ProcessBuilder(
                    "python",
                    scriptPath
            );
        }
        
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
        int stepTimeout = resolveTimeout(scriptVersion, taskTimeoutMs);
        log.info("本地执行超时上限：{} 秒（脚本 {}）", stepTimeout, scriptPath);
        boolean completed = process.waitFor(stepTimeout, TimeUnit.SECONDS);

        long durationMs = System.currentTimeMillis() - startTime;

        if (!completed) {
            process.destroyForcibly();
            log.error("脚本执行超时：{}", scriptPath);
            return ExecutionResult.builder()
                    .success(false)
                    .exitCode(-1)
                    .output(output.toString())
                    .error("执行超时（" + stepTimeout + "秒）")
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

        log.info("PowerShellExecutor 执行完成，exitCode={}，duration={}ms", exitCode, durationMs);

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
        return "powershell";
    }

    @Override
    public boolean isAvailable() {
        try {
            // 使用更兼容的检测方式
            ProcessBuilder pb = new ProcessBuilder(powershellPath, "-Command", "Write-Host OK");
            Process p = pb.start();
            boolean completed = p.waitFor(5, TimeUnit.SECONDS);
            if (completed && p.exitValue() == 0) {
                log.info("PowerShell 执行器可用");
                return true;
            }
            log.warn("PowerShell 检测返回非零退出码: {}", p.exitValue());
            return false;
        } catch (Exception e) {
            log.warn("PowerShell 执行器不可用：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 本地执行超时（秒）：任务配置的超时优先，其次脚本声明的默认超时，最后才是配置兜底值。
     * 串口类脚本（IPMI SOL 逐段敲字符）单次就要十几分钟，固定 300 秒会中途打断。
     */
    private int resolveTimeout(ScriptVersion scriptVersion, long taskTimeoutMs) {
        if (taskTimeoutMs > 0) {
            return (int) Math.min(taskTimeoutMs / 1000, Integer.MAX_VALUE);
        }
        if (scriptVersion != null && scriptVersion.getScriptId() != null) {
            try {
                Script script = scriptMapper.selectById(scriptVersion.getScriptId());
                if (script != null && script.getDefaultTimeout() != null && script.getDefaultTimeout() > 0) {
                    return script.getDefaultTimeout();
                }
            } catch (Exception e) {
                log.warn("读取脚本默认超时失败，使用兜底值：{}", e.getMessage());
            }
        }
        return timeout;
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