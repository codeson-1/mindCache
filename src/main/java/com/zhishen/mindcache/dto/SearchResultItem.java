package com.zhishen.mindcache.dto;

/**
 * 单条搜索结果，包含融合评分明细。
 *
 * @param item         知识条目响应（含标签）
 * @param fusedScore   融合后最终评分（0~1，越高越相关）
 * @param vectorScore  向量语义得分（批次 min-max 归一化到 0~1）
 * @param bm25Score    BM25 关键词得分（批次 min-max 归一化到 0~1）
 * @param timeDecay    时间衰减因子（0~1，越新越接近 1）
 */
public record SearchResultItem(
        KnowledgeItemResponse item,
        double fusedScore,
        double vectorScore,
        double bm25Score,
        double timeDecay
) {}
