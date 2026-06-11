package com.zhishen.mindcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MindCache 启动类。
 * <p>多模态碎片知识管家 — 基于 Spring AI + pgvector 的个人知识管理工具。
 * 支持文字/语音/图片三种模态的统一录入与语义检索。
 */
@SpringBootApplication
public class MindCacheApplication {

    /**
     * 应用入口。
     * 本地启动：{@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=local}
     */
    public static void main(String[] args) {
        SpringApplication.run(MindCacheApplication.class, args);
    }

}
