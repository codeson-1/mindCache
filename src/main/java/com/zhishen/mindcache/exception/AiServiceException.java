package com.zhishen.mindcache.exception;

/**
 * AI 服务不可用或业务上视为 AI 失败（ASR、LLM 清洗、图片分析、自动分类等）。
 * 映射 HTTP 502，与文档约定一致。
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
