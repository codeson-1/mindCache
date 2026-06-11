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
        Document doc = new Document(
                item.getCleanContent(),
                Map.of(
                        "item_id", item.getId().toString(),
                        "content_type", item.getContentType().name(),
                        "category", item.getAutoCategory() != null ? item.getAutoCategory() : ""
                )
        );
        vectorStore.add(List.of(doc));
    }

    /**
     * 批量入库
     */
    public int ingestBatch(List<KnowledgeItem> items) {
        List<Document> docs = items.stream()
                .map(item -> new Document(
                        item.getCleanContent(),
                        Map.of(
                                "item_id", item.getId().toString(),
                                "content_type", item.getContentType().name(),
                                "category", item.getAutoCategory() != null ? item.getAutoCategory() : ""
                        )
                ))
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
}
