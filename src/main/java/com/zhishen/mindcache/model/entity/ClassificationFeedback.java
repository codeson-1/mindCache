package com.zhishen.mindcache.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 分类反馈表 — 人在回路的分类修正记录。
 * <p>用户每次手动修正 AI 分类/标签，均记录到此表。
 * 同类修正积累到一定数量后，进入分类 Prompt 的 few-shot 示例库，实现渐进优化。
 */
@Entity
@Table(name = "classification_feedback")
public class ClassificationFeedback {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 关联的知识条目 */
    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private KnowledgeItem item;

    /** AI 原始分类 */
    @Column(name = "original_category", length = 30)
    private String originalCategory;

    /** 用户修正后的分类 */
    @Column(name = "corrected_category", length = 30)
    private String correctedCategory;

    /** AI 原始标签（JSON 数组字符串） */
    @Column(name = "original_tags", columnDefinition = "TEXT")
    private String originalTags;

    /** 用户修正后的标签（JSON 数组字符串） */
    @Column(name = "corrected_tags", columnDefinition = "TEXT")
    private String correctedTags;

    /** 用户自由文本反馈 */
    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    /** 反馈时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 无参构造器（JPA 必需） */
    public ClassificationFeedback() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public KnowledgeItem getItem() { return item; }
    public void setItem(KnowledgeItem item) { this.item = item; }

    public String getOriginalCategory() { return originalCategory; }
    public void setOriginalCategory(String originalCategory) { this.originalCategory = originalCategory; }

    public String getCorrectedCategory() { return correctedCategory; }
    public void setCorrectedCategory(String correctedCategory) { this.correctedCategory = correctedCategory; }

    public String getOriginalTags() { return originalTags; }
    public void setOriginalTags(String originalTags) { this.originalTags = originalTags; }

    public String getCorrectedTags() { return correctedTags; }
    public void setCorrectedTags(String correctedTags) { this.correctedTags = correctedTags; }

    public String getFeedbackText() { return feedbackText; }
    public void setFeedbackText(String feedbackText) { this.feedbackText = feedbackText; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
