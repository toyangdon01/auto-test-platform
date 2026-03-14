package com.autotest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 输出收集结果
 *
 * @author auto-test-platform
 */
@Data
public class OutputCollectResult {

    /**
     * 收集的文件列表
     */
    private List<CollectedFile> files = new ArrayList<>();

    /**
     * 收集错误列表
     */
    private List<String> collectErrors = new ArrayList<>();

    /**
     * 收集的文件信息
     */
    @Data
    public static class CollectedFile {
        /**
         * 文件名称（来自规则）
         */
        private String name;

        /**
         * 原始路径
         */
        private String originalPath;

        /**
         * 存储路径（相对路径）
         */
        private String storagePath;

        /**
         * 文件大小（字节）
         */
        private Long size;

        /**
         * 收集状态：success / not_found / error
         */
        private String collectStatus;

        /**
         * 错误信息
         */
        private String errorMessage;
    }

    public void addFile(CollectedFile file) {
        this.files.add(file);
    }

    public void addError(String error) {
        this.collectErrors.add(error);
    }
}
