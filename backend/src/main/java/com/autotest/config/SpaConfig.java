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

    private static final Resource INDEX_HTML = new ClassPathResource("static/index.html");

    /** api/ 是全部 REST 接口；ws/ 与 webshell/ 是 @ServerEndpoint WebSocket 端点 */
    private static final String[] BACKEND_PREFIXES = {"api/", "ws/", "webshell/"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 没有配置 server.servlet.context-path，是各 Controller 自带 /api/v1 前缀，
        // 所以 "/**" 同样覆盖了 API 路径 —— 回退到 index.html 时必须把后端路径排除掉。
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        // 如果请求的资源存在，返回它
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        // 打错的接口和缺失的前端资源保持 null，由 NoResourceFoundException 报 404
                        if (isBackendPath(resourcePath)) {
                            return null;
                        }
                        // 其余是 Vue Router history 模式的深链，交给 index.html 由前端路由接管
                        return INDEX_HTML.exists() && INDEX_HTML.isReadable() ? INDEX_HTML : null;
                    }
                });
    }

    private static boolean isBackendPath(String resourcePath) {
        for (String prefix : BACKEND_PREFIXES) {
            if (resourcePath.startsWith(prefix)) {
                return true;
            }
        }
        // 带扩展名的是静态资源本身，被 index.html 掩盖后表现为白屏且看不出原因。
        // 只看最后一段：早期段的点（/v1.5/tasks）不影响判断，代价是带点的路径参数
        // （/tasks/detail/v1.5）会按文件 404 —— 与 webpack historyApiFallback 一致，
        // 本项目路由参数均为数据库自增 id，不含点。
        int lastSeparator = resourcePath.lastIndexOf('/');
        return resourcePath.indexOf('.', lastSeparator + 1) >= 0;
    }
}
