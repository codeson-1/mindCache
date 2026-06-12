package com.zhishen.mindcache.dto;

import com.zhishen.mindcache.model.entity.Tag;

import java.util.UUID;

/**
 * 标签响应 DTO。
 */
public record TagResponse(
        UUID id,
        String name,
        int usageCount,
        boolean isNew
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getUsageCount() != null ? tag.getUsageCount() : 0,
                tag.getIsNew() != null && tag.getIsNew()
        );
    }
}
