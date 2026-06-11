package com.zhishen.mindcache.exception;

/**
 * 业务资源不存在（如 knowledge item id 无效）。
 * 映射 HTTP 404。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
