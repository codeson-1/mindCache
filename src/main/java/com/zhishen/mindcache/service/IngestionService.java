package com.zhishen.mindcache.service;

import com.zhishen.mindcache.model.entity.KnowledgeItem;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 向量化入库管道 —— 所有知识条目在持久化后自动向量化。
 * 调用 VectorStore（Spring AI 自动管理 pgvector 表）完成写入与删除。
 */
@Service
public class IngestionService {

    private final VectorStore vectorStore;

    /**
     * VectorStore 由 Spring AI 自动注入（PgVectorStore 实现）。
     */
    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 单条入库：KnowledgeItem → Document → VectorStore.add
     */
    public void ingest(KnowledgeItem item) {
        vectorStore.add(List.of(toDocument(item)));
    }

    /**
     * 批量入库
     */
    public int ingestBatch(List<KnowledgeItem> items) {
        List<Document> docs = items.stream()
                .map(this::toDocument)
                .toList();
        vectorStore.add(docs);
        return docs.size();
    }

    /**
     * 删除向量（更新/删除笔记时调用）。
     * itemId 来自 UUID.toString()，不含单引号，字符串拼接安全。
     */
    public void removeByItemId(String itemId) {
        vectorStore.delete("item_id == '" + itemId + "'");
    }

    /**
     * KnowledgeItem → Spring AI Document，metadata 携带 item_id / content_type / category。
     * <p>与 {@code KeywordIndexService.toDoc()} 对称设计。
     */
    private Document toDocument(KnowledgeItem item) {
        return new Document(
                item.getCleanContent(),
                Map.of(
                        "item_id", item.getId().toString(),
                        "content_type", item.getContentType().name(),
                        "category", resolveCategory(item)
                )
        );
    }

    /**
     * 有效分类：用户修正优先，AI 自动分类兜底。
     * <p>与 {@code KeywordIndexService.toDoc()} 中的 category 取值逻辑一致。
     */
    private String resolveCategory(KnowledgeItem item) {
        if (item.getUserCategory() != null && !item.getUserCategory().isBlank()) {
            return item.getUserCategory();
        }
        return item.getAutoCategory() != null ? item.getAutoCategory() : "";
    }
}
