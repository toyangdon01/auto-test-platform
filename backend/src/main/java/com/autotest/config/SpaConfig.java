package com.autotest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA (Single Page Application) 配置
 * 
 * 将所有非 API、非静态资源的请求转发到 index.html
 * 支持前端路由（Vue Router history 模式）
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源处理（排除 API 路径）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
