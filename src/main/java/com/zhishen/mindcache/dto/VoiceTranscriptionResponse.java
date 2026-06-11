package com.zhishen.mindcache.dto;

import com.zhishen.mindcache.model.entity.KnowledgeItem;

/**
 * 语音转写响应 DTO。
 * 包含 ASR 转写文本、去口语化清洗文本、已入库的 KnowledgeItem。
 */
public record VoiceTranscriptionResponse(
        /** ASR 原始转写文本 */
        String asrText,
        /** LLM 去口语化后的清洗文本 */
        String cleanedText,
        /** 已创建的知识条目（含 id、向量已入库） */
        KnowledgeItemResponse knowledgeItem
) {
    public static VoiceTranscriptionResponse of(String asrText, String cleanedText, KnowledgeItem item) {
        return new VoiceTranscriptionResponse(asrText, cleanedText, KnowledgeItemResponse.from(item));
    }
}
