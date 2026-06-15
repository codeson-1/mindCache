package com.zhishen.mindcache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 多路融合检索参数配置。
 *
 * <h3>融合公式</h3>
 * <pre>
 *   fusedScore = alpha × vectorSimilarity + beta × bm25Normalized + gamma × timeDecay
 * </pre>
 *
 * <h3>调参指南（第5周第7天评测）</h3>
 * <ul>
 *   <li>提高 alpha → 语义泛化优先</li>
 *   <li>提高 beta  → 关键词精确匹配优先</li>
 *   <li>提高 gamma → 新鲜度优先</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "mindcache.search")
public class SearchConfig {

    /** 向量语义权重，默认 0.5 */
    private double alpha = 0.5;

    /** BM25 关键词权重，默认 0.3 */
    private double beta = 0.3;

    /** 时间衰减权重，默认 0.2 */
    private double gamma = 0.2;

    /** 时间衰减系数（每天），默认 0.01（约 100 天后权重降至 0.37） */
    private double timeDecayLambda = 0.01;

    /** 默认返回条数 */
    private int defaultTopK = 20;

    /**
     * pgvector 向量检索的最低余弦相似度阈值（0~1）。
     * 低于此值的向量不会进入候选集，在数据库/HNSW 层即可拦截语义无关的结果。
     * <p>默认 0.3：仅拦截余弦相似度极低（< 0.3）的明显无关结果，主要过滤交第二道相对分差截断。
     */
    private double similarityThreshold = 0.3;

    /**
     * 融合评分相对截断因子（0~1）。
     * 融合后只保留 fusedScore ≥ top1FusedScore × factor 的结果。
     * 用于拦截「向量未命中但 BM25 关键词碰巧命中」的漏网之鱼。
     * <p>默认 0.75：得分不到第一名 75% 的视为弱相关，予以过滤。
     */
    private double relativeCutoffFactor = 0.75;

    // ---- getters / setters ----

    public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) { this.alpha = alpha; }

    public double getBeta() { return beta; }
    public void setBeta(double beta) { this.beta = beta; }

    public double getGamma() { return gamma; }
    public void setGamma(double gamma) { this.gamma = gamma; }

    public double getTimeDecayLambda() { return timeDecayLambda; }
    public void setTimeDecayLambda(double timeDecayLambda) { this.timeDecayLambda = timeDecayLambda; }

    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int defaultTopK) { this.defaultTopK = defaultTopK; }

    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }

    public double getRelativeCutoffFactor() { return relativeCutoffFactor; }
    public void setRelativeCutoffFactor(double relativeCutoffFactor) { this.relativeCutoffFactor = relativeCutoffFactor; }
}
