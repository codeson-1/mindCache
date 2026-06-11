package com.zhishen.mindcache.model.entity;

import com.zhishen.mindcache.model.enums.ContentType;
import com.zhishen.mindcache.model.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识条目 — 文字/语音/图片三种模态的统一业务实体。
 * <p>不含 Embedding 字段，向量由 {@code vector_store} 表独立管理，
 * 通过 {@code vector_store.metadata->>'item_id'} 关联。
 * <p>注意：使用普通 Class 而非 Record，因为 Hibernate 的 @GeneratedValue
 * 需要通过 setter 写入 ID（Record 的 final 字段不可写）。
 */
@Entity
@Table(name = "knowledge_items")
public class KnowledgeItem {

    /** 主键，数据库自动生成 UUID */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 原始输入文本（含口语词、OCR 噪声等） */
    @Column(name = "raw_content", nullable = false, columnDefinition = "TEXT")
    private String rawContent;

    /** LLM 标准化清洗后的内容，用于 Embedding 和 Lucene 索引 */
    @Column(name = "clean_content", nullable = false, columnDefinition = "TEXT")
    private String cleanContent;

    /** 输入模态：TEXT/AUDIO/IMAGE */
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 10)
    private ContentType contentType;

    /** 来源：MANUAL/VOICE/UPLOAD/WEB_CLIP */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    private SourceType sourceType;

    /** AI 自动分类（如 TECH/WORK/LIFE/IDEA/READING/REFERENCE） */
    @Column(name = "auto_category", length = 30)
    private String autoCategory;

    /** 用户手动修正的分类（人在回路） */
    @Column(name = "user_category", length = 30)
    private String userCategory;

    /** AI 生成的一句话摘要 */
    @Column(name = "summary", length = 200)
    private String summary;

    /** JSON 扩展字段（duration、imageUrl、audioUrl 等） */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    /** 创建时间，插入后不可修改 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 无参构造器（JPA 必需），自动填充时间戳。
     */
    public KnowledgeItem() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ---- getters / setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public String getCleanContent() { return cleanContent; }
    public void setCleanContent(String cleanContent) { this.cleanContent = cleanContent; }

    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getAutoCategory() { return autoCategory; }
    public void setAutoCategory(String autoCategory) { this.autoCategory = autoCategory; }

    public String getUserCategory() { return userCategory; }
    public void setUserCategory(String userCategory) { this.userCategory = userCategory; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
