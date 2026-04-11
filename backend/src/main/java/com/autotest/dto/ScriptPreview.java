package com.autotest.dto;

import lombok.Data;
import java.util.List;

/**
 * 脚本预览信息（在线导入时使用）
 */
@Data
public class ScriptPreview {
    /** 脚本名称 */
    private String name;
    
    /** 是否有 autotest.yaml 配置 */
    private boolean hasYaml;
    
    /** 文件数量 */
    private int fileCount;
    
    /** 文件列表 */
    private List<String> files;
    
    /** 是否已存在于平台 */
    private boolean exists;
}