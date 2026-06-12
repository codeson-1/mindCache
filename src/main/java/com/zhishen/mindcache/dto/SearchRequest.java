package com.zhishen.mindcache.dto;

/**
 * 搜索请求 DTO。
 *
 * @param query      自然语言搜索词（必填）
 * @param topK       返回条数（默认取 SearchConfig.defaultTopK）
 * @param category   分类筛选（可选，如 TECH/WORK/LIFE）
 * @param contentType 内容类型筛选（可选，如 TEXT/AUDIO/IMAGE）
 */
public record SearchRequest(
        String query,
        Integer topK,
        String category,
        String contentType
) {
    /**
     * 仅按搜索词查询（无筛选条件）。
     */
    public SearchRequest(String query) {
        this(query, null, null, null);
    }
}
