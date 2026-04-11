package com.autotest.util;

import com.autotest.dto.GitPlatform;
import com.autotest.dto.GitRepoInfo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Git 平台归档下载 URL 构建器
 */
public class ArchiveUrlBuilder {

    /**
     * 构建下载 URL
     */
    public static String build(GitRepoInfo info, String accessToken) {
        if (info.getPlatform() == null) {
            throw new IllegalArgumentException("不支持的平台");
        }
        
        switch (info.getPlatform()) {
            case GITEE:
                return buildGiteeUrl(info, accessToken);
            case GITHUB:
                return buildGitHubUrl(info, accessToken);
            case GITLAB:
                return buildGitLabUrl(info, accessToken);
            default:
                throw new IllegalArgumentException("不支持的平台：" + info.getPlatform());
        }
    }

    /**
     * 构建 Gitee 下载 URL
     */
    private static String buildGiteeUrl(GitRepoInfo info, String token) {
        String owner = info.getOwner();
        String repo = info.getRepo();
        String branch = info.getBranch();
        
        if (token != null && !token.isEmpty()) {
            // 私有仓库使用 API
            return String.format(
                "https://gitee.com/api/v5/repos/%s/%s/archive?access_token=%s&ref=%s",
                owner, repo, 
                URLEncoder.encode(token, StandardCharsets.UTF_8),
                URLEncoder.encode(branch, StandardCharsets.UTF_8)
            );
        } else {
            // 公开仓库
            return String.format(
                "https://gitee.com/%s/%s/repository/archive/%s.zip",
                owner, repo, 
                URLEncoder.encode(branch, StandardCharsets.UTF_8)
            );
        }
    }

    /**
     * 构建 GitHub 下载 URL
     */
    private static String buildGitHubUrl(GitRepoInfo info, String token) {
        String owner = info.getOwner();
        String repo = info.getRepo();
        String branch = info.getBranch();
        
        if (token != null && !token.isEmpty()) {
            // 私有仓库使用 API
            return String.format(
                "https://api.github.com/repos/%s/%s/zipball/%s",
                owner, repo, 
                URLEncoder.encode(branch, StandardCharsets.UTF_8)
            );
        } else {
            // 公开仓库
            return String.format(
                "https://github.com/%s/%s/archive/refs/heads/%s.zip",
                owner, repo, 
                URLEncoder.encode(branch, StandardCharsets.UTF_8)
            );
        }
    }

    /**
     * 构建 GitLab 下载 URL
     */
    private static String buildGitLabUrl(GitRepoInfo info, String token) {
        String owner = info.getOwner();
        String repo = info.getRepo();
        String branch = info.getBranch();
        
        if (token != null && !token.isEmpty()) {
            // 私有仓库使用 API
            String projectId = URLEncoder.encode(owner + "/" + repo, StandardCharsets.UTF_8);
            return String.format(
                "https://gitlab.com/api/v4/projects/%s/repository/archive.zip?private_token=%s&ref=%s",
                projectId,
                URLEncoder.encode(token, StandardCharsets.UTF_8),
                URLEncoder.encode(branch, StandardCharsets.UTF_8)
            );
        } else {
            // 公开仓库
            return String.format(
                "https://gitlab.com/%s/%s/-/archive/%s/%s-%s.zip",
                owner, repo, 
                URLEncoder.encode(branch, StandardCharsets.UTF_8),
                repo,
                URLEncoder.encode(branch, StandardCharsets.UTF_8)
            );
        }
    }
}