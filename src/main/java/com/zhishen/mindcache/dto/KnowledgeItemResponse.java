package com.zhishen.mindcache.dto;

import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.model.enums.ContentType;
import com.zhishen.mindcache.model.enums.SourceType;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识条目响应 DTO。
 * <p>包含 KnowledgeItem 的所有字段，通过 {@link #from(KnowledgeItem)} 从实体转换。
 */
public record KnowledgeItemResponse(
        UUID id,
        String rawContent,
        String cleanContent,
        ContentType contentType,
        SourceType sourceType,
        String autoCategory,
        String userCategory,
        String summary,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * 从实体对象构造响应 DTO。
     *
     * @param item 持久化的 KnowledgeItem 实体
     * @return 前端友好的响应对象
     */
    public static KnowledgeItemResponse from(KnowledgeItem item) {
        return new KnowledgeItemResponse(
                item.getId(),
                item.getRawContent(),
                item.getCleanContent(),
                item.getContentType(),
                item.getSourceType(),
                item.getAutoCategory(),
                item.getUserCategory(),
                item.getSummary(),
                item.getMetadataJson(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
