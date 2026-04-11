package com.autotest.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 退出码
     */
    private int exitCode;

    /**
     * 标准输出
     */
    private String output;

    /**
     * 错误输出
     */
    private String error;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;
}