package com.zhishen.mindcache.config;

import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.repository.KnowledgeItemRepository;
import com.zhishen.mindcache.service.KeywordIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

/**
 * Lucene 启动兜底：内存索引重启后丢失，启动时从 DB 全量重建。
 *
 * <p>{@link KeywordIndexConfig} 中的 ByteBuffersDirectory 是纯堆内存索引，
 * 进程重启即清空，而 pgvector 在 PostgreSQL 磁盘上持久存在。
 * 此组件负责弥合这个差距——每次应用启动时，从 knowledge_items 表
 * 全量读取并调用 {@link KeywordIndexService#rebuildAll(List)} 重建 Lucene 索引。
 *
 * <p>失败策略：重建失败仅记 ERROR 日志，不阻断启动。
 * 索引可后续通过手动调用或下次重启自动恢复，pgvector 语义检索始终可用。
 */
@Component
public class IndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

    private final KeywordIndexService keywordIndexService;
    private final KnowledgeItemRepository repository;

    public IndexInitializer(KeywordIndexService keywordIndexService,
                            KnowledgeItemRepository repository) {
        this.keywordIndexService = keywordIndexService;
        this.repository = repository;
    }

    /**
     * 启动时从 PostgreSQL 全量重建 Lucene 内存索引。
     * <p>数据量：个人库 < 一万条，全量加载耗时 < 1s，内存占用 < 10MB。
     */
    @PostConstruct
    public void rebuildLuceneIndex() {
        try {
            List<KnowledgeItem> all = repository.findAll();
            keywordIndexService.rebuildAll(all);
            log.info("Lucene index rebuilt from {} knowledge items on startup", all.size());
        } catch (IOException e) {
            log.error("Failed to rebuild Lucene index on startup", e);
        }
    }
}
