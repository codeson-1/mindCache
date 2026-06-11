package com.zhishen.mindcache.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 知识条目-标签多对多关联表。
 * <p>联合主键 (item_id, tag_id)，使用 @IdClass 映射。
 * source 字段标记来源：AI（自动打标）或 USER（用户手动修改）。
 */
@Entity
@Table(name = "item_tags")
@IdClass(ItemTag.ItemTagId.class)
public class ItemTag {

    /** 关联的知识条目（复合主键之一） */
    @Id
    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private KnowledgeItem item;

    /** 关联的标签（复合主键之一） */
    @Id
    @ManyToOne
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    /** 标签来源：AI（自动）或 USER（手动修正） */
    @Column(name = "source", length = 10)
    private String source = "AI";

    /** 无参构造器（JPA 必需） */
    public ItemTag() {}

    /**
     * 便捷构造器。
     *
     * @param item   知识条目
     * @param tag    标签
     * @param source 来源（AI/USER）
     */
    public ItemTag(KnowledgeItem item, Tag tag, String source) {
        this.item = item;
        this.tag = tag;
        this.source = source != null ? source : "AI";
    }

    public KnowledgeItem getItem() { return item; }
    public void setItem(KnowledgeItem item) { this.item = item; }

    public Tag getTag() { return tag; }
    public void setTag(Tag tag) { this.tag = tag; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    /**
     * 联合主键类（@IdClass 映射）。
     * 字段名必须与实体中的 @Id 字段名完全一致。
     */
    @SuppressWarnings("JpaDataSourceORMInspection")
    public static class ItemTagId implements Serializable {
        private UUID item;
        private UUID tag;

        public ItemTagId() {}

        public ItemTagId(UUID item, UUID tag) {
            this.item = item;
            this.tag = tag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemTagId that)) return false;
            return Objects.equals(item, that.item) && Objects.equals(tag, that.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, tag);
        }
    }
}
