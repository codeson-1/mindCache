package com.zhishen.mindcache.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA 路由回退配置。
 * <p>Vue Router 使用 HTML5 History 模式，所有非 API 请求需要回退到 {@code index.html}，
 * 让前端路由接管 URL 解析。
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 为所有非 /api/ 路径注册回退处理器
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        // 如果请求的静态资源存在，直接返回
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 否则回退到 index.html（SPA 路由交前端处理）
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
