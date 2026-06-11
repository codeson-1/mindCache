package com.zhishen.mindcache.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 标签实体。
 * <p>标签名全局唯一，通过 usage_count 实现热度排序。
 * isNew 标记指示该标签是由 AI 首次创建、尚未被用户确认。
 */
@Entity
@Table(name = "tags")
public class Tag {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 标签名（全局唯一） */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /** 使用次数，用于热度排序 */
    @Column(name = "usage_count")
    private Integer usageCount = 1;

    /** 是否 AI 首次创建的新标签 */
    @Column(name = "is_new")
    private Boolean isNew = true;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 无参构造器（JPA 必需）。
     */
    public Tag() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }

    public Boolean getIsNew() { return isNew; }
    public void setIsNew(Boolean isNew) { this.isNew = isNew; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
