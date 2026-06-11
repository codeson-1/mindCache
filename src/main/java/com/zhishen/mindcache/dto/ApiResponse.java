package com.zhishen.mindcache.dto;

/**
 * 统一 API 响应格式。
 * <p>所有 REST 接口均返回此结构，前端据此判断请求状态。
 * <ul>
 *   <li>成功：{@code code=200, message="ok", data=...}</li>
 *   <li>失败：{@code code=4xx/5xx, message=错误描述, data=null}</li>
 * </ul>
 */
public record ApiResponse<T>(int code, String message, T data) {

    /**
     * 成功响应快捷工厂方法。
     *
     * @param data 响应数据，可为 null
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "ok", data);
    }

    /**
     * 错误响应快捷工厂方法。
     *
     * @param code    HTTP 状态码（400/404/413/502）
     * @param message 人类可读的错误信息
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
