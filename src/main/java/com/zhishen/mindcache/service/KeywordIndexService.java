package com.zhishen.mindcache.service;

import com.zhishen.mindcache.model.entity.KnowledgeItem;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * BM25 关键词索引服务。
 * 纯内存（ByteBuffersDirectory），SmartChineseAnalyzer 中文分词，
 * 与 pgvector 向量检索并行，在 SearchService 中按 item_id 融合。
 */
@Service
public class KeywordIndexService {

    private final IndexWriter indexWriter;
    private final SmartChineseAnalyzer analyzer;

    /**
     * 构造器注入：IndexWriter + Analyzer 均由 KeywordIndexConfig 提供。
     */
    public KeywordIndexService(IndexWriter indexWriter, SmartChineseAnalyzer analyzer) {
        this.indexWriter = indexWriter;
        this.analyzer = analyzer;
    }

    /**
     * 新增一条至 Lucene 索引
     */
    public void index(KnowledgeItem item) throws IOException {
        indexWriter.addDocument(toDoc(item));
        indexWriter.commit();
    }

    /**
     * 更新：先删旧、再加新
     */
    public void reindex(KnowledgeItem item) throws IOException {
        indexWriter.deleteDocuments(new Term("item_id", item.getId().toString()));
        indexWriter.addDocument(toDoc(item));
        indexWriter.commit();
    }

    /**
     * 删除
     */
    public void remove(String itemId) throws IOException {
        indexWriter.deleteDocuments(new Term("item_id", itemId));
        indexWriter.commit();
    }

    /**
     * BM25 关键词搜索，返回 topK 结果。
     */
    public List<ScoredItemId> search(String queryText, int topK) throws Exception {
        DirectoryReader reader = DirectoryReader.open(indexWriter);
        IndexSearcher searcher = new IndexSearcher(reader);

        QueryParser parser = new QueryParser("content", analyzer);
        Query query = parser.parse(queryText);
        TopDocs topDocs = searcher.search(query, topK);

        List<ScoredItemId> results = new ArrayList<>();
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            results.add(new ScoredItemId(doc.get("item_id"), sd.score));
        }

        reader.close();
        return results;
    }

    /**
     * 启动兜底：从 knowledge_items 全量重建 Lucene 索引。
     */
    public void rebuildAll(List<KnowledgeItem> items) throws IOException {
        indexWriter.deleteAll();
        for (KnowledgeItem item : items) {
            indexWriter.addDocument(toDoc(item));
        }
        indexWriter.commit();
    }

    // ---- internal ----

    /**
     * 将 KnowledgeItem 转换为 Lucene Document。
     * <p>索引字段：
     * <ul>
     *   <li>{@code item_id} — StringField，精确匹配，存储</li>
     *   <li>{@code content} — TextField，全文索引，不存储（省内存）</li>
     *   <li>{@code category} — StringField，精确匹配，存储（分类筛选）</li>
     *   <li>{@code created_at} — LongPoint + StoredField，毫秒时间戳（时间衰减）</li>
     * </ul>
     */
    private Document toDoc(KnowledgeItem item) {
        Document doc = new Document();
        // item_id: 精确匹配 + 存储
        doc.add(new StringField("item_id", item.getId().toString(), Field.Store.YES));
        // content: 全文索引，不存储原文（省内存）
        doc.add(new TextField("content", item.getCleanContent(), Field.Store.NO));
        // category: 精确匹配 + 存储（用于分类筛选）
        String category = item.getUserCategory() != null ? item.getUserCategory() : item.getAutoCategory();
        if (category != null) {
            doc.add(new StringField("category", category, Field.Store.YES));
        }
        // created_at: 数值索引 + 存储（用于时间衰减/范围查询）
        long epochMillis = item.getCreatedAt().toEpochMilli();
        doc.add(new LongPoint("created_at", epochMillis));
        doc.add(new StoredField("created_at", epochMillis));
        return doc;
    }

    /**
     * BM25 搜索结果。
     *
     * @param itemId 知识条目 ID
     * @param score  BM25 评分（∈ [0, +∞)，需在 SearchService 做 min-max 归一化）
     */
    public record ScoredItemId(String itemId, float score) {}
}
