package com.zhishen.mindcache.dto;

import com.zhishen.mindcache.model.enums.ContentType;
import com.zhishen.mindcache.model.enums.SourceType;

/**
 * 知识条目更新请求 DTO。
 * <p>所有字段均为可选——传 null 表示该字段保持不变。
 * cleanContent 为 null 但 rawContent 不为 null 时，自动用 rawContent 兜底。
 */
public record UpdateKnowledgeItemRequest(
        /** 原始文本 */
        String rawContent,
        /** 清洗后文本 */
        String cleanContent,
        /** 输入模态 */
        ContentType contentType,
        /** 来源 */
        SourceType sourceType,
        /** AI 生成的一句话摘要（可手动修改） */
        String summary,
        /** JSON 扩展字段 */
        String metadataJson
) {
    /**
     * Record 紧凑构造器：cleanContent 兜底逻辑。
     */
    public UpdateKnowledgeItemRequest {
        if (cleanContent == null && rawContent != null) cleanContent = rawContent;
    }
}
