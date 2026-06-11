package com.zhishen.mindcache.dto;

import com.zhishen.mindcache.model.enums.ContentType;
import com.zhishen.mindcache.model.enums.SourceType;

/**
 * 知识条目创建请求 DTO。
 * <p>{@code contentType} 用于区分输入模态：TEXT / AUDIO / IMAGE。
 * 所有字段均有合理默认值，前端可按需传递。
 */
public record CreateKnowledgeItemRequest(
        /** 原始输入文本（必填） */
        String rawContent,
        /** LLM 清洗后文本；为空时默认取 rawContent */
        String cleanContent,
        /** 输入模态 */
        ContentType contentType,
        /** 来源 */
        SourceType sourceType,
        /** JSON 扩展字段（duration、imageUrl 等） */
        String metadataJson
) {
    /**
     * Record 紧凑构造器：补齐默认值。
     * contentType 默认 TEXT，sourceType 默认 MANUAL，cleanContent 默认 rawContent。
     */
    public CreateKnowledgeItemRequest {
        if (contentType == null) contentType = ContentType.TEXT;
        if (sourceType == null) sourceType = SourceType.MANUAL;
        if (cleanContent == null || cleanContent.isBlank()) cleanContent = rawContent;
    }
}
