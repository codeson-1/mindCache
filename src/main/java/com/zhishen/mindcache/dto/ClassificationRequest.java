package com.zhishen.mindcache.dto;

import java.util.List;

/**
 * 分类修正请求 DTO（人在回路）。
 * <p>用户可在前端修正 AI 分类和标签，修正数据记录到 classification_feedback 表。
 */
public record ClassificationRequest(
        /** 用户修正后的分类（TECH/WORK/LIFE/IDEA/READING/REFERENCE），可为 null 表示不修改 */
        String correctedCategory,
        /** 用户修正后的标签列表，可为 null 表示不修改 */
        List<String> correctedTags
) {}
