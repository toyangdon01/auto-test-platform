package com.autotest.dto;

import lombok.Data;
import java.util.List;

/**
 * 在线导入请求
 */
@Data
public class OnlineImportRequest {
    /** 仓库 URL（支持 Gitee/GitHub/GitLab） */
    private String url;
    
    /** 分支/标签，默认 main */
    private String branch = "main";
    
    /** 子目录路径（可选，如 scripts/） */
    private String subDir;
    
    /** 访问令牌（可选，私有仓库需要） */
    private String accessToken;
    
    /** 选中的脚本名列表（可选，null=全部） */
    private List<String> selectedScripts;
    
    /** 冲突策略：SKIP/OVERWRITE/RENAME */
    private String conflictStrategy = "SKIP";
    
    /** 临时路径标识（预览后传递给导入） */
    private String tempPath;
}