package com.autotest.dto;

import lombok.Data;

/**
 * Git 仓库信息
 */
@Data
public class GitRepoInfo {
    /** 平台类型 */
    private GitPlatform platform;
    
    /** 用户/组织名 */
    private String owner;
    
    /** 仓库名 */
    private String repo;
    
    /** 分支/标签 */
    private String branch = "main";
    
    /** 子目录路径 */
    private String subDir;
    
    /** 完整 URL */
    private String url;
}