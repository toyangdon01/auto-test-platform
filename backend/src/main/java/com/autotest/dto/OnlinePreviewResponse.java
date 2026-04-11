package com.autotest.dto;

import lombok.Data;
import java.util.List;

/**
 * 在线导入预览响应
 */
@Data
public class OnlinePreviewResponse {
    /** 平台类型 */
    private GitPlatform platform;
    
    /** 仓库名 */
    private String repoName;
    
    /** 分支 */
    private String branch;
    
    /** 发现的脚本列表 */
    private List<ScriptPreview> scripts;
    
    /** 总脚本数 */
    private int totalScripts;
    
    /** 临时路径标识（用于后续导入） */
    private String tempPath;
}