package com.zhishen.mindcache.service;

import com.zhishen.mindcache.dto.CreateKnowledgeItemRequest;
import com.zhishen.mindcache.dto.UpdateKnowledgeItemRequest;
import com.zhishen.mindcache.exception.ResourceNotFoundException;
import com.zhishen.mindcache.model.entity.ClassificationFeedback;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.repository.ClassificationFeedbackRepository;
import com.zhishen.mindcache.repository.KnowledgeItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识条目 CRUD + 双写管道编排。
 *
 * <h3>双写管道</h3>
 * 每次增/改/删操作同步写入三处：
 * <ol>
 *   <li>JPA → {@code knowledge_items} 表（业务数据）</li>
 *   <li>{@link IngestionService} → {@code vector_store} 表（pgvector，1024维 Embedding）</li>
 *   <li>{@link KeywordIndexService} → Lucene 内存 BM25 索引</li>
 * </ol>
 *
 * <h3>AI 自动分类</h3>
 * 新增笔记后自动调用 {@link ClassificationService} 完成分类+标签+摘要。
 * 失败仅记 WARN 日志，不阻塞主流程。
 *
 * <h3>容错策略</h3>
 * 索引写入失败仅记 WARN 日志，不抛异常，保证主流程（JPA 写入）不中断。
 * 失败的索引在下次应用重启时由 {@link IndexInitializer} 全量重建补回。
 */
@Service
@Transactional
public class KnowledgeItemService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeItemService.class);

    private final KnowledgeItemRepository repo;
    private final IngestionService ingestionService;
    private final KeywordIndexService keywordIndexService;
    private final ClassificationService classificationService;
    private final TagService tagService;
    private final ClassificationFeedbackRepository feedbackRepo;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入。
     */
    public KnowledgeItemService(KnowledgeItemRepository repo,
                                 IngestionService ingestionService,
                                 KeywordIndexService keywordIndexService,
                                 ClassificationService classificationService,
                                 TagService tagService,
                                 ClassificationFeedbackRepository feedbackRepo,
                                 ObjectMapper objectMapper) {
        this.repo = repo;
        this.ingestionService = ingestionService;
        this.keywordIndexService = keywordIndexService;
        this.classificationService = classificationService;
        this.tagService = tagService;
        this.feedbackRepo = feedbackRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * 统一录入：JPA 持久化 → AI 自动分类 → VectorStore 写入 → Lucene 索引。
     *
     * @param request 创建请求（contentType 区分 TEXT/AUDIO/IMAGE）
     * @return 已持久化的 KnowledgeItem（含自动生成的 UUID）
     */
    public KnowledgeItem create(CreateKnowledgeItemRequest request) {
        KnowledgeItem item = new KnowledgeItem();
        item.setRawContent(request.rawContent());
        item.setCleanContent(request.cleanContent());
        item.setContentType(request.contentType());
        item.setSourceType(request.sourceType());
        item.setMetadataJson(request.metadataJson());
        item = repo.save(item);

        // AI 自动分类 + 标签 + 摘要（失败仅记日志）
        classifyAndTag(item);

        // 双写索引（失败仅记日志）
        ingestAndIndex(item);

        return item;
    }

    /**
     * 按 ID 查询单条笔记。
     *
     * @param id 知识条目 UUID
     * @return 可能为空的 Optional
     */
    @Transactional(readOnly = true)
    public Optional<KnowledgeItem> findById(UUID id) {
        return repo.findById(id);
    }

    /**
     * 用户修正分类/标签（人在回路）。
     * <p>写入 classification_feedback 记录，更新 userCategory，替换 USER 来源的标签。
     *
     * @param id                 知识条目 UUID
     * @param correctedCategory  用户修正后的分类（null 表示不修改）
     * @param correctedTags      用户修正后的标签（null 表示不修改）
     * @return 更新后的实体
     */
    public KnowledgeItem correctClassification(UUID id, String correctedCategory, List<String> correctedTags) {
        KnowledgeItem item = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge item not found: " + id));

        // 记录修正前的原始值
        String originalCategory = item.getAutoCategory();
        List<String> originalTags = tagService.getItemTagNames(id);

        boolean categoryChanged = false;
        if (correctedCategory != null && !correctedCategory.isBlank()) {
            item.setUserCategory(correctedCategory.trim());
            categoryChanged = true;
        }

        if (correctedTags != null) {
            tagService.syncItemTags(item, correctedTags, "USER");
        }

        item.setUpdatedAt(Instant.now());
        item = repo.save(item);

        if (categoryChanged) {
            try {
                ingestionService.removeByItemId(id.toString());
                ingestionService.ingest(item);
            } catch (Exception e) {
                log.warn("VectorStore re-ingest failed after classification correction for item {}: {}",
                        id, e.getMessage());
            }
            try {
                keywordIndexService.reindex(item);
            } catch (Exception e) {
                log.warn("Lucene reindex failed after classification correction for item {}: {}",
                        id, e.getMessage());
            }
        }

        // 写入 feedback（同一事务，修正+feedback 原子性）
        ClassificationFeedback feedback = new ClassificationFeedback();
        feedback.setItem(item);
        feedback.setOriginalCategory(originalCategory);
        feedback.setCorrectedCategory(correctedCategory);
        feedback.setOriginalTags(toJsonArray(originalTags));
        feedback.setCorrectedTags(toJsonArray(correctedTags));

        // 将笔记前100字写入 feedback_text，供 few-shot 示例使用
        String content = item.getCleanContent();
        if (content != null && !content.isBlank()) {
            feedback.setFeedbackText(content.length() > 100
                    ? content.substring(0, 100) + "..."
                    : content);
        }

        feedbackRepo.save(feedback);

        return item;
    }

    /**
     * 分页列表，支持按自动分类筛选。
     * <p>使用只读事务，避免 Hibernate 脏检查开销。
     *
     * @param page     页码（0-based）
     * @param size     每页条数
     * @param category 分类筛选条件（可选）
     */
    @Transactional(readOnly = true)
    public Page<KnowledgeItem> list(int page, int size, String category) {
        PageRequest pageable = PageRequest.of(page, size);
        if (category != null && !category.isBlank()) {
            return repo.findByAutoCategory(category, pageable);
        }
        return repo.findAll(pageable);
    }

    /**
     * 更新笔记内容：部分字段更新 → 旧向量删除 + 新向量写入 → Lucene 重建。
     * <p>每个字段为 null 时保持原值不变。
     *
     * @param id      目标条目 ID
     * @param request 更新请求（所有字段可选）
     * @return 更新后的实体，若 ID 不存在返回 empty
     */
    public Optional<KnowledgeItem> update(UUID id, UpdateKnowledgeItemRequest request) {
        return repo.findById(id).map(existing -> {
            // 仅覆盖非 null 字段
            if (request.rawContent() != null) existing.setRawContent(request.rawContent());
            if (request.cleanContent() != null) existing.setCleanContent(request.cleanContent());
            if (request.contentType() != null) existing.setContentType(request.contentType());
            if (request.sourceType() != null) existing.setSourceType(request.sourceType());
            if (request.summary() != null) existing.setSummary(request.summary());
            if (request.metadataJson() != null) existing.setMetadataJson(request.metadataJson());
            existing.setUpdatedAt(Instant.now());
            existing = repo.save(existing);

            // 重新入库：先删旧向量，再写新 → Lucene 重索引
            try {
                ingestionService.removeByItemId(id.toString());
                ingestionService.ingest(existing);
            } catch (Exception e) {
                log.warn("VectorStore re-ingest failed for item {}: {}", id, e.getMessage());
            }
            try {
                keywordIndexService.reindex(existing);
            } catch (Exception e) {
                log.warn("Lucene reindex failed for item {}: {}", id, e.getMessage());
            }

            return existing;
        });
    }

    /**
     * 删除笔记：向量删除 → Lucene 删除 → JPA 删除。
     *
     * @param id 目标条目 ID
     * @return false 表示 ID 不存在
     */
    public boolean delete(UUID id) {
        Optional<KnowledgeItem> maybeItem = repo.findById(id);
        if (maybeItem.isEmpty()) {
            return false;
        }

        // 先删索引，再删标签计数，最后删实体
        try {
            ingestionService.removeByItemId(id.toString());
        } catch (Exception e) {
            log.warn("VectorStore delete failed for item {}: {}", id, e.getMessage());
        }
        try {
            keywordIndexService.remove(id.toString());
        } catch (Exception e) {
            log.warn("Lucene delete failed for item {}: {}", id, e.getMessage());
        }

        tagService.decrementTagsForItem(id);
        repo.delete(maybeItem.get());
        return true;
    }

    /**
     * 双写辅助：ingest 到 pgvector + index 到 Lucene。
     * 失败仅记 WARN 日志，不抛异常，保证主流程不中断。
     */
    private void ingestAndIndex(KnowledgeItem item) {
        try {
            ingestionService.ingest(item);
        } catch (Exception e) {
            log.warn("VectorStore ingestion failed for item {}: {}", item.getId(), e.getMessage());
        }
        try {
            keywordIndexService.index(item);
        } catch (Exception e) {
            log.warn("Lucene indexing failed for item {}: {}", item.getId(), e.getMessage());
        }
    }

    /**
     * AI 自动分类辅助方法：调用 LLM 分类 → 更新 item 字段 → 同步标签。
     * 失败仅记 WARN 日志，不抛异常。
     */
    private void classifyAndTag(KnowledgeItem item) {
        try {
            ClassificationService.ClassificationResult result =
                    classificationService.classify(item.getCleanContent());

            // 写入分类和摘要
            if (result.category() != null) {
                item.setAutoCategory(result.category());
            }
            if (result.suggestedSummary() != null) {
                item.setSummary(result.suggestedSummary());
            }
            repo.save(item);

            // 同步标签
            if (!result.tags().isEmpty()) {
                tagService.syncItemTags(item, result.tags(), "AI");
            }

            log.info("Auto-classification done for item {}: category={}, tags={}",
                    item.getId(), result.category(), result.tags());
        } catch (Exception e) {
            log.warn("Auto-classification failed for item {}: {}", item.getId(), e.getMessage());
        }
    }

    /**
     * 标签列表 → JSON 数组字符串（如 {@code ["Spring","微服务"]}）。
     * <p>与表 classification_feedback 的 JSON 数组格式一致，标签名含特殊字符安全。
     */
    private String toJsonArray(List<String> tags) {
        if (tags == null) return null;
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("Failed to serialize tags to JSON: {}", e.getMessage());
            return "[]";
        }
    }
}
