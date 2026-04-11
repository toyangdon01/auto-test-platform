package com.autotest.dto;

import lombok.Getter;

/**
 * Git 平台枚举
 */
@Getter
public enum GitPlatform {
    GITEE("Gitee", "gitee.com"),
    GITHUB("GitHub", "github.com"),
    GITLAB("GitLab", "gitlab.com");

    private final String displayName;
    private final String domain;

    GitPlatform(String displayName, String domain) {
        this.displayName = displayName;
        this.domain = domain;
    }

    /**
     * 从 URL 判断平台类型
     */
    public static GitPlatform fromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String lowerUrl = url.toLowerCase();
        for (GitPlatform platform : values()) {
            if (lowerUrl.contains(platform.domain)) {
                return platform;
            }
        }
        return null;
    }
}