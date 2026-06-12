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
}
