package com.zhishen.mindcache.dto;

import com.zhishen.mindcache.model.entity.KnowledgeItem;

/**
 * 图片上传/分析响应 DTO。
 */
public record ImageUploadResponse(
        /** OCR 提取的文字 */
        String ocrText,
        /** AI 视觉描述 */
        String visualDescription,
        /** 融合后的最终文本（OCR + 视觉描述） */
        String mergedContent,
        /** 已创建的知识条目 */
        KnowledgeItemResponse knowledgeItem
) {
    public static ImageUploadResponse of(String ocrText, String visualDescription,
                                          String mergedContent, KnowledgeItem item) {
        return new ImageUploadResponse(ocrText, visualDescription, mergedContent, KnowledgeItemResponse.from(item));
    }
}
