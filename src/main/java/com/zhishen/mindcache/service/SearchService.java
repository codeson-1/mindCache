package com.zhishen.mindcache.service;

import com.zhishen.mindcache.config.SearchConfig;
import com.zhishen.mindcache.dto.KnowledgeItemResponse;
import com.zhishen.mindcache.dto.SearchRequest;
import com.zhishen.mindcache.dto.SearchResultItem;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.exception.ResourceNotFoundException;
import com.zhishen.mindcache.repository.KnowledgeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 多路融合检索服务。
 *
 * <h3>检索流程</h3>
 * <ol>
 *   <li>VectorStore 语义召回（pgvector HNSW，余弦相似度）</li>
 *   <li>Lucene BM25 关键词召回（SmartChineseAnalyzer 中文分词）</li>
 *   <li>按 item_id 合并 → BM25 min-max 归一化 → 时间衰减 → 加权融合</li>
 *   <li>JPA 批量加载实体 → 构建 SearchResultItem 列表</li>
 * </ol>
 *
 * <h3>融合公式</h3>
 * <pre>
 *   fusedScore = alpha × vectorSimilarity + beta × bm25Normalized + gamma × timeDecay
 * </pre>
 *
 * <h3>时间衰减</h3>
 * <pre>
 *   timeDecay = exp(-lambda × daysSinceCreation)
 * </pre>
 * 越新的内容权重越高。lambda 默认 0.01（约 100 天后权重降至 ~0.37）。
 */
@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** pgvector 距离值在 Document metadata 中的 key */
    private static final String DISTANCE_KEY = "distance";

    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 100;

    private final VectorStore vectorStore;
    private final KeywordIndexService keywordIndexService;
    private final KnowledgeItemRepository itemRepo;
    private final TagService tagService;
    private final SearchConfig config;

    /**
     * 构造器注入。
     */
    public SearchService(VectorStore vectorStore,
                         KeywordIndexService keywordIndexService,
                         KnowledgeItemRepository itemRepo,
                         TagService tagService,
                         SearchConfig config) {
        this.vectorStore = vectorStore;
        this.keywordIndexService = keywordIndexService;
        this.itemRepo = itemRepo;
        this.tagService = tagService;
        this.config = config;
    }

    /**
     * 多路融合搜索。
     *
     * @param request 搜索请求（query 必填，其余可选）
     * @return 按融合评分降序排列的结果列表，最多 topK 条
     */
    public List<SearchResultItem> search(SearchRequest request) {
        String query = request.query();
        int topK = request.topK() != null ? request.topK() : config.getDefaultTopK();
        if (topK < MIN_TOP_K || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK 必须在 " + MIN_TOP_K + "~" + MAX_TOP_K + " 之间");
        }

        // 扩大召回：从两路各取 topK×3，给融合留足够候选
        int wideTopK = Math.max(topK * 3, 30);

        // ---- 第1路：向量语义召回 ----
        Map<String, Double> vectorScores = similaritySearch(query, wideTopK);
        vectorScores = minMaxNormalize(vectorScores);   // batch 内归一化到 [0,1]

        // ---- 第2路：BM25 关键词召回 ----
        Map<String, Double> bm25Scores = bm25Search(query, wideTopK);

        // ---- 合并所有候选 item_id ----
        Map<String, double[]> candidateScores = new HashMap<>();
        for (String itemId : vectorScores.keySet()) {
            candidateScores.putIfAbsent(itemId, new double[2]);
            candidateScores.get(itemId)[0] = vectorScores.get(itemId);
        }
        for (String itemId : bm25Scores.keySet()) {
            candidateScores.putIfAbsent(itemId, new double[2]);
            candidateScores.get(itemId)[1] = bm25Scores.get(itemId);
        }

        if (candidateScores.isEmpty()) {
            log.info("No results for query: {}", query);
            return List.of();
        }

        // ---- 批量加载实体 ----
        List<UUID> ids = candidateScores.keySet().stream()
                .map(UUID::fromString)
                .toList();
        List<KnowledgeItem> items = itemRepo.findAllById(ids);

        // ---- 按 category/contentType 筛选 ----
        if (request.category() != null && !request.category().isBlank()) {
            items = items.stream()
                    .filter(i -> {
                        String cat = i.getUserCategory() != null ? i.getUserCategory() : i.getAutoCategory();
                        return request.category().equals(cat);
                    })
                    .toList();
        }
        if (request.contentType() != null && !request.contentType().isBlank()) {
            items = items.stream()
                    .filter(i -> request.contentType().equalsIgnoreCase(i.getContentType().name()))
                    .toList();
        }

        // ---- 时间衰减 + 融合评分 ----
        Instant now = Instant.now();
        List<SearchResultItem> results = new ArrayList<>();

        // 批量加载标签（避免 N+1）
        Map<UUID, List<String>> tagsMap = tagService.getItemTagNames(
                items.stream().map(KnowledgeItem::getId).toList());

        for (KnowledgeItem item : items) {
            String itemId = item.getId().toString();
            double[] scores = candidateScores.get(itemId);
            double vectorScore = scores != null ? scores[0] : 0.0;
            double bm25Score = scores != null ? scores[1] : 0.0;
            double timeDecay = computeTimeDecay(item.getCreatedAt(), now);

            double fusedScore = config.getAlpha() * vectorScore
                    + config.getBeta() * bm25Score
                    + config.getGamma() * timeDecay;

            List<String> tags = tagsMap.getOrDefault(item.getId(), List.of());

            results.add(new SearchResultItem(
                    KnowledgeItemResponse.from(item, tags),
                    fusedScore,
                    vectorScore,
                    bm25Score,
                    timeDecay
            ));
        }

        // ---- 按融合评分降序排序，相对分差截断，取 topK ----
        results.sort(Comparator.comparingDouble(SearchResultItem::fusedScore).reversed());

        // 相对分差截断：fusedScore < top1 × factor 的视为弱相关，予以过滤
        if (!results.isEmpty()) {
            double cutoff = results.get(0).fusedScore() * config.getRelativeCutoffFactor();
            results = results.stream()
                    .filter(r -> r.fusedScore() >= cutoff)
                    .collect(Collectors.toList());
        }

        List<SearchResultItem> topResults = results.stream()
                .limit(topK)
                .toList();

        log.info("Search query=\"{}\" → {} candidates → {} results (α={}, β={}, γ={})",
                query, results.size(), topResults.size(),
                config.getAlpha(), config.getBeta(), config.getGamma());

        return topResults;
    }

    /**
     * 相关笔记推荐：用目标笔记的 cleanContent 做向量语义搜索，
     * 多取一条用于排除自身，返回 topK 条语义最相似的其他笔记。
     *
     * @param itemId 目标笔记 ID
     * @param topK   返回条数
     * @return 按向量相似度 + 时间衰减排序的相关笔记列表
     */
    public List<SearchResultItem> findRelated(UUID itemId, int topK) {
        KnowledgeItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge item not found: " + itemId));

        // 多取一条，用于排除自身
        Map<String, Double> vectorScores = similaritySearch(item.getCleanContent(), topK + 1);
        vectorScores = minMaxNormalize(vectorScores);
        vectorScores.remove(itemId.toString());

        if (vectorScores.isEmpty()) {
            log.info("No related items found for item {}", itemId);
            return List.of();
        }

        // 批量加载实体
        List<UUID> ids = vectorScores.keySet().stream()
                .map(UUID::fromString)
                .toList();
        List<KnowledgeItem> relatedItems = itemRepo.findAllById(ids);

        // 时间衰减 + 排序
        Instant now = Instant.now();
        Map<UUID, List<String>> tagsMap = tagService.getItemTagNames(
                relatedItems.stream().map(KnowledgeItem::getId).toList());

        List<SearchResultItem> results = new ArrayList<>();
        for (KnowledgeItem ri : relatedItems) {
            String riId = ri.getId().toString();
            double vectorScore = vectorScores.getOrDefault(riId, 0.0);
            double timeDecay = computeTimeDecay(ri.getCreatedAt(), now);
            double fusedScore = config.getAlpha() * vectorScore + config.getGamma() * timeDecay;

            List<String> tags = tagsMap.getOrDefault(ri.getId(), List.of());
            results.add(new SearchResultItem(
                    KnowledgeItemResponse.from(ri, tags),
                    fusedScore,
                    vectorScore,
                    0.0,      // 无 BM25
                    timeDecay
            ));
        }

        results.sort(Comparator.comparingDouble(SearchResultItem::fusedScore).reversed());

        // 相对分差截断
        if (!results.isEmpty()) {
            double cutoff = results.get(0).fusedScore() * config.getRelativeCutoffFactor();
            results = results.stream()
                    .filter(r -> r.fusedScore() >= cutoff)
                    .collect(Collectors.toList());
        }
        return results.stream().limit(topK).toList();
    }

    /**
     * VectorStore 语义检索，返回 itemId → similarity 映射。
     * <p>使用 pgvector COSINE_DISTANCE，distance ∈ [0, 2]，
     * 转换为 similarity = 1 - distance/2 ∈ [0, 1]。
     */
    private Map<String, Double> similaritySearch(String query, int topK) {
        Map<String, Double> result = new HashMap<>();
        try {
            var searchRequest = org.springframework.ai.vectorstore.SearchRequest
                    .builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(config.getSimilarityThreshold())
                    .build();
            List<Document> docs = vectorStore.similaritySearch(searchRequest);

            for (Document doc : docs) {
                String itemId = (String) doc.getMetadata().get("item_id");
                if (itemId == null) continue;

                // pgvector COSINE_DISTANCE: distance ∈ [0, 2], 0 = 完全相同
                double distance = getDistance(doc);
                double similarity = 1.0 - (distance / 2.0);
                similarity = Math.max(0.0, Math.min(1.0, similarity));

                result.put(itemId, similarity);
            }
        } catch (Exception e) {
            log.warn("VectorStore similarity search failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Lucene BM25 关键词检索，返回 itemId → 归一化score 映射。
     * <p>原始 BM25 ∈ [0, +∞)，做 min-max 归一化到 [0, 1]。
     */
    private Map<String, Double> bm25Search(String query, int topK) {
        Map<String, Double> raw = new HashMap<>();
        try {
            List<KeywordIndexService.ScoredItemId> hits = keywordIndexService.search(query, topK);
            for (var hit : hits) {
                raw.put(hit.itemId(), (double) hit.score());
            }
        } catch (Exception e) {
            log.warn("KeywordIndexService BM25 search failed: {}", e.getMessage());
        }
        return minMaxNormalize(raw);
    }

    /**
     * BM25 原始分数 min-max 归一化到 [0, 1]。
     * <p>如果只有 1 条结果，直接给 1.0。
     * 如果所有分数相同，全部给 1.0。
     */
    private Map<String, Double> minMaxNormalize(Map<String, Double> raw) {
        if (raw.isEmpty()) return raw;

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double v : raw.values()) {
            if (v < min) min = v;
            if (v > max) max = v;
        }

        Map<String, Double> normalized = new HashMap<>();
        if (max == min) {
            // 所有分数相同：全部置为 1.0
            for (String key : raw.keySet()) {
                normalized.put(key, 1.0);
            }
        } else {
            for (var entry : raw.entrySet()) {
                double norm = (entry.getValue() - min) / (max - min);
                normalized.put(entry.getKey(), norm);
            }
        }
        return normalized;
    }

    /**
     * 指数时间衰减：越新的内容权重越高。
     * <pre>
     *   decay = exp(-λ × days)
     * </pre>
     *
     * @param createdAt 内容创建时间
     * @param now       当前时间
     * @return 衰减因子 ∈ (0, 1]，当天创建 = 1.0
     */
    double computeTimeDecay(Instant createdAt, Instant now) {
        long days = ChronoUnit.DAYS.between(createdAt, now);
        if (days < 0) days = 0;
        return Math.exp(-config.getTimeDecayLambda() * days);
    }

    /**
     * 从 Spring AI Document metadata 中提取 pgvector 距离值。
     * <p>兼容 distance 可能以 String、Double、Float 等形式存储。
     */
    private double getDistance(Document doc) {
        Object raw = doc.getMetadata().get(DISTANCE_KEY);
        if (raw instanceof Number num) {
            return num.doubleValue();
        }
        if (raw instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
