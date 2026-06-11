package com.zhishen.mindcache.config;

import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Lucene 关键词索引配置。
 * 纯内存索引（ByteBuffersDirectory），零磁盘 IO；
 * SmartChineseAnalyzer 中文分词 + BM25Similarity 排序。
 */
@Configuration
public class KeywordIndexConfig {

    /**
     * Lucene 内存索引目录。
     * <p>ByteBuffersDirectory：纯堆内存，零磁盘 IO，重启丢失（通过 @PostConstruct 从 DB 重建）。
     * destroyMethod="close" 确保应用关闭时释放内存。
     */
    @Bean(destroyMethod = "close")
    public ByteBuffersDirectory luceneDirectory() {
        return new ByteBuffersDirectory();
    }

    /**
     * SmartChineseAnalyzer：中文分词器，支持简体中文隐马尔可夫分词。
     * 线程安全，所有 Lucene 搜索复用同一实例。
     */
    @Bean(destroyMethod = "close")
    public SmartChineseAnalyzer luceneAnalyzer() {
        return new SmartChineseAnalyzer();
    }

    /**
     * IndexWriter：Lucene 索引写入入口。
     * <p>BM25Similarity：显式声明经典 BM25 排序（Lucene 默认即 BM25，此处显式保证可读性）。
     * 单机单实例，线程安全。
     */
    @Bean(destroyMethod = "close")
    public IndexWriter luceneIndexWriter(ByteBuffersDirectory directory, SmartChineseAnalyzer analyzer)
            throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity());
        return new IndexWriter(directory, config);
    }
}
