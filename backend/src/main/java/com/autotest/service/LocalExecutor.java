package com.autotest.service;

import com.autotest.entity.Server;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.TaskServer;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 本地执行器接口
 * 用于在平台本地执行脚本（如通过 SOL 连接到目标服务器）
 */
public interface LocalExecutor {

    /**
     * 执行脚本
     *
     * @param scriptVersion 脚本版本信息
     * @param params        参数
     * @param taskServer    任务服务器关联
     * @param server        服务器信息（本地执行时包含连接信息）
     * @param logConsumer   日志输出回调
     * @return 执行结果
     */
    ExecutionResult execute(
            ScriptVersion scriptVersion,
            Map<String, Object> params,
            TaskServer taskServer,
            Server server,
            Consumer<String> logConsumer
    ) throws Exception;

    /**
     * 执行指定脚本文件
     *
     * @param scriptVersion 脚本版本信息
     * @param scriptFile    要执行的脚本文件（相对于脚本目录）
     * @param params        参数
     * @param taskServer    任务服务器关联
     * @param server        服务器信息
     * @param logConsumer   日志输出回调
     * @return 执行结果
     */
    ExecutionResult execute(
            ScriptVersion scriptVersion,
            String scriptFile,
            Map<String, Object> params,
            TaskServer taskServer,
            Server server,
            Consumer<String> logConsumer
    ) throws Exception;

    /**
     * 获取执行器类型
     * @return 类型标识
     */
    String getType();

    /**
     * 检查执行器是否可用
     * @return 是否可用
     */
    boolean isAvailable();
}