package com.zhishen.mindcache.repository;

import com.zhishen.mindcache.model.entity.KnowledgeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * KnowledgeItem 数据访问层。
 * <p>继承 JpaRepository 自动获得 CRUD + 分页 + 排序能力。
 */
@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {

    /**
     * 按自动分类分页查询。
     *
     * @param autoCategory AI 自动分类（如 TECH/WORK/LIFE）
     * @param pageable     分页参数
     */
    Page<KnowledgeItem> findByAutoCategory(String autoCategory, Pageable pageable);

    /**
     * 查询指定时间范围内的知识条目（按创建时间排序）。
     *
     * @param start 起始时间（含）
     * @param end   结束时间（含）
     * @return 时间范围内的条目列表
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.createdAt >= :start AND k.createdAt <= :end ORDER BY k.createdAt DESC")
    List<KnowledgeItem> findByCreatedAtBetween(@Param("start") Instant start, @Param("end") Instant end);
}
