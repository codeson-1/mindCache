package com.zhishen.mindcache.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 语音模块 HTTP 客户端配置。
 * <p>DashScope ASR（qwen3-asr-flash）接口为 REST API，非 Spring AI 抽象，
 * 因此需要独立的 RestTemplate 实例做 HTTP 调用。
 * <p>超时设置：连接 10s，读取 120s（语音文件可能较大，上传+识别需要更长时间）。
 */
@Configuration
public class VoiceConfig {

    /**
     * 专用于调用 DashScope ASR API 的 RestTemplate。
     * 连接超时 10s，读取超时 120s。
     */
    @Bean
    public RestTemplate asrRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }
}
