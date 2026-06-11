package com.zhishen.mindcache.service;

import com.zhishen.mindcache.dto.CreateKnowledgeItemRequest;
import com.zhishen.mindcache.dto.UpdateKnowledgeItemRequest;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.repository.KnowledgeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
 * <h3>容错策略</h3>
 * 索引写入失败仅记 WARN 日志，不抛异常，保证主流程（JPA 写入）不中断。
 * 失败的索引会在下次查询时通过 @PostConstruct 全量重建补回。
 */
@Service
@Transactional
public class KnowledgeItemService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeItemService.class);

    private final KnowledgeItemRepository repo;
    private final IngestionService ingestionService;
    private final KeywordIndexService keywordIndexService;

    /**
     * 构造器注入。
     */
    public KnowledgeItemService(KnowledgeItemRepository repo,
                                 IngestionService ingestionService,
                                 KeywordIndexService keywordIndexService) {
        this.repo = repo;
        this.ingestionService = ingestionService;
        this.keywordIndexService = keywordIndexService;
    }

    /**
     * 统一录入：JPA 持久化 → VectorStore 写入 → Lucene 索引。
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
    public Optional<KnowledgeItem> findById(UUID id) {
        return repo.findById(id);
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

        // 先删索引，再删实体
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
}
