package com.autotest.service;

import com.autotest.entity.Server;
import com.jcraft.jsch.*;

import java.io.*;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * SSH 连接服务
 *
 * @author auto-test-platform
 */
public class SshService {

    private static final int DEFAULT_TIMEOUT = 30000; // 30秒
    private static final int EXEC_TIMEOUT = 60000; // 执行超时 60秒

    /**
     * 测试 SSH 连接
     */
    public static boolean testConnection(Server server) {
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = createSession(jsch, server);
            session.setTimeout(DEFAULT_TIMEOUT);
            
            System.out.println("[SSH] Connecting to " + server.getHost() + ":" + server.getPort() + " as " + server.getUsername());
            session.connect();
            System.out.println("[SSH] Connected successfully!");

            return session.isConnected();
        } catch (JSchException e) {
            System.out.println("[SSH] Connection failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * 执行远程命令（简单模式）
     */
    public static String executeCommand(Server server, String command) {
        ExecuteResult result = executeCommand(server, command, null, EXEC_TIMEOUT);
        return result.getOutput();
    }

    /**
     * 执行远程命令（返回详细结果）
     */
    public static ExecuteResult executeCommandWithResult(Server server, String command) {
        return executeCommand(server, command, null, EXEC_TIMEOUT);
    }

    /**
     * 执行远程命令（带实时输出）
     *
     * @param server      服务器信息
     * @param command     命令
     * @param outputCallback 实时输出回调（可为null）
     * @param timeoutMs   超时时间（毫秒）
     * @return 执行结果
     */
    public static ExecuteResult executeCommand(Server server, String command, Consumer<String> outputCallback, int timeoutMs) {
        Session session = null;
        ChannelExec channel = null;
        ExecuteResult result = new ExecuteResult();
        long startTime = System.currentTimeMillis();
        
        try {
            JSch jsch = new JSch();
            session = createSession(jsch, server);
            session.setTimeout(DEFAULT_TIMEOUT);
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            
            channel.connect();
            
            // 读取输出
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            byte[] buffer = new byte[4096];
            
            while (true) {
                // 检查超时
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    result.setExitCode(-1);
                    result.setError("Timeout after " + timeoutMs + "ms");
                    break;
                }
                
                // 读取标准输出
                while (in.available() > 0) {
                    int len = in.read(buffer, 0, 4096);
                    if (len < 0) break;
                    String output = new String(buffer, 0, len);
                    stdout.append(output);
                    if (outputCallback != null) {
                        outputCallback.accept(output);
                    }
                }
                
                // 读取错误输出
                while (err.available() > 0) {
                    int len = err.read(buffer, 0, 4096);
                    if (len < 0) break;
                    String error = new String(buffer, 0, len);
                    stderr.append(error);
                    if (outputCallback != null) {
                        outputCallback.accept("[ERROR] " + error);
                    }
                }
                
                if (channel.isClosed()) {
                    // 读取剩余输出
                    while (in.available() > 0) {
                        int len = in.read(buffer, 0, 4096);
                        if (len < 0) break;
                        String output = new String(buffer, 0, len);
                        stdout.append(output);
                        if (outputCallback != null) {
                            outputCallback.accept(output);
                        }
                    }
                    while (err.available() > 0) {
                        int len = err.read(buffer, 0, 4096);
                        if (len < 0) break;
                        String error = new String(buffer, 0, len);
                        stderr.append(error);
                        if (outputCallback != null) {
                            outputCallback.accept("[ERROR] " + error);
                        }
                    }
                    result.setExitCode(channel.getExitStatus());
                    break;
                }
                
                Thread.sleep(100);
            }
            
            result.setStdout(stdout.toString());
            result.setStderr(stderr.toString());
            result.setOutput(stdout.toString() + stderr.toString());
            result.setDurationMs(System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            result.setError(e.getMessage());
            result.setExitCode(-1);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
        
        return result;
    }

    /**
     * 上传文件到远程服务器
     */
    public static boolean uploadFile(Server server, String localPath, String remotePath) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            session = createSession(jsch, server);
            session.setTimeout(DEFAULT_TIMEOUT);
            session.connect();

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            // 创建远程目录
            int lastSlash = remotePath.lastIndexOf('/');
            if (lastSlash > 0) {
                String dir = remotePath.substring(0, lastSlash);
                createRemoteDirs(channel, dir);
            }
            
            // 上传文件
            channel.put(localPath, remotePath);
            return true;
        } catch (Exception e) {
            System.err.println("[SSH] Upload failed: " + e.getMessage());
            return false;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * 上传内容到远程服务器
     */
    public static boolean uploadContent(Server server, String content, String remotePath) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            session = createSession(jsch, server);
            session.setTimeout(DEFAULT_TIMEOUT);
            session.connect();

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            // 创建远程目录
            int lastSlash = remotePath.lastIndexOf('/');
            if (lastSlash > 0) {
                String dir = remotePath.substring(0, lastSlash);
                createRemoteDirs(channel, dir);
            }
            
            // 上传内容
            InputStream inputStream = new ByteArrayInputStream(content.getBytes());
            channel.put(inputStream, remotePath);
            return true;
        } catch (Exception e) {
            System.err.println("[SSH] Upload content failed: " + e.getMessage());
            return false;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * 创建远程目录（递归）
     */
    private static void createRemoteDirs(ChannelSftp channel, String path) throws SftpException {
        String[] dirs = path.split("/");
        StringBuilder currentPath = new StringBuilder();
        for (String dir : dirs) {
            if (dir.isEmpty()) continue;
            currentPath.append("/").append(dir);
            try {
                channel.mkdir(currentPath.toString());
            } catch (SftpException e) {
                // 目录可能已存在
            }
        }
    }

    /**
     * 从远程服务器下载文件
     */
    public static boolean downloadFile(Server server, String remotePath, String localPath) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            session = createSession(jsch, server);
            session.setTimeout(DEFAULT_TIMEOUT);
            session.connect();

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            // 创建本地目录
            File localFile = new File(localPath);
            if (!localFile.getParentFile().exists()) {
                localFile.getParentFile().mkdirs();
            }
            
            // 下载文件
            OutputStream outputStream = new FileOutputStream(localPath);
            channel.get(remotePath, outputStream);
            outputStream.close();
            
            return true;
        } catch (Exception e) {
            System.err.println("[SSH] Download failed: " + e.getMessage());
            return false;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * 获取服务器系统信息
     */
    public static ServerInfo getServerInfo(Server server) {
        ServerInfo info = new ServerInfo();
        
        try {
            // 获取操作系统信息
            String osInfo = executeCommand(server, "cat /etc/os-release 2>/dev/null | head -5");
            info.setOsInfo(parseOsInfo(osInfo));
            
            // 获取内核版本
            String kernel = executeCommand(server, "uname -r");
            info.setKernel(kernel.trim());
            
            // 获取 CPU 架构
            String cpuArch = executeCommand(server, "uname -m");
            info.setCpuArch(cpuArch.trim());
            
            // 获取 CPU 信息
            String cpuModel = executeCommand(server, "cat /proc/cpuinfo 2>/dev/null | grep 'model name' | head -1 | cut -d':' -f2");
            info.setCpuModel(cpuModel.trim());
            
            String cpuCores = executeCommand(server, "nproc");
            info.setCpuCores(Integer.parseInt(cpuCores.trim()));
            
            // 获取内存信息
            String memInfo = executeCommand(server, "free -m | grep Mem");
            String[] memParts = memInfo.trim().split("\\s+");
            if (memParts.length >= 2) {
                info.setMemoryTotalMb(Long.parseLong(memParts[1]));
            }
            
            // 获取磁盘信息
            String diskInfo = executeCommand(server, "df -h / 2>/dev/null | tail -1");
            info.setDiskInfo(diskInfo.trim());
            
        } catch (Exception e) {
            // 忽略错误
        }
        
        return info;
    }

    private static String parseOsInfo(String osInfo) {
        if (osInfo == null || osInfo.isEmpty()) return "Unknown";
        for (String line : osInfo.split("\n")) {
            if (line.startsWith("PRETTY_NAME=")) {
                return line.replace("PRETTY_NAME=", "").replace("\"", "");
            }
        }
        return osInfo.split("\n")[0];
    }

    /**
     * 创建 SSH Session
     */
    private static Session createSession(JSch jsch, Server server) throws JSchException {
        Session session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());

        if ("password".equals(server.getAuthType())) {
            session.setPassword(server.getAuthSecret());
        } else if ("key".equals(server.getAuthType())) {
            byte[] keyBytes = server.getAuthSecret().getBytes();
            jsch.addIdentity("key_" + server.getId(), keyBytes, null, null);
        }

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("UserKnownHostsFile", "/dev/null");
        session.setConfig(config);
        
        return session;
    }

    /**
     * 执行结果
     */
    public static class ExecuteResult {
        private int exitCode;
        private String stdout;
        private String stderr;
        private String output;
        private String error;
        private long durationMs;

        public int getExitCode() { return exitCode; }
        public void setExitCode(int exitCode) { this.exitCode = exitCode; }
        
        public String getStdout() { return stdout; }
        public void setStdout(String stdout) { this.stdout = stdout; }
        
        public String getStderr() { return stderr; }
        public void setStderr(String stderr) { this.stderr = stderr; }
        
        public String getOutput() { return output != null ? output : ""; }
        public void setOutput(String output) { this.output = output; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        
        public boolean isSuccess() { return exitCode == 0; }
    }

    /**
     * 服务器系统信息
     */
    public static class ServerInfo {
        private String osInfo;
        private String kernel;
        private String cpuModel;
        private String cpuArch;
        private Integer cpuCores;
        private Long memoryTotalMb;
        private String diskInfo;

        public String getOsInfo() { return osInfo; }
        public void setOsInfo(String osInfo) { this.osInfo = osInfo; }
        
        public String getKernel() { return kernel; }
        public void setKernel(String kernel) { this.kernel = kernel; }
        
        public String getCpuModel() { return cpuModel; }
        public void setCpuModel(String cpuModel) { this.cpuModel = cpuModel; }
        
        public String getCpuArch() { return cpuArch; }
        public void setCpuArch(String cpuArch) { this.cpuArch = cpuArch; }
        
        public Integer getCpuCores() { return cpuCores; }
        public void setCpuCores(Integer cpuCores) { this.cpuCores = cpuCores; }
        
        public Long getMemoryTotalMb() { return memoryTotalMb; }
        public void setMemoryTotalMb(Long memoryTotalMb) { this.memoryTotalMb = memoryTotalMb; }
        
        public String getDiskInfo() { return diskInfo; }
        public void setDiskInfo(String diskInfo) { this.diskInfo = diskInfo; }
    }
}
